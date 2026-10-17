package com.verityball.entity;

import com.verityball.VerityState;
import com.verityball.VerityVariant;
import com.verityball.client.ClientHooks;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verity: un mob que se queda quieto y se controla desde un menú.
 * Se puede coger en la mano y, si se activa "Seguirme", camina (o bota) detrás del jugador.
 */
public class VerityEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAINBOW =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    /** Id de la entidad (jugador) que la lleva en la mano, o -1 si está suelta. No se guarda en el mundo. */
    private static final EntityDataAccessor<Integer> DATA_HOLDER =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    /** ¿Gira la cara hacia el jugador? (true por defecto; al apagarlo se queda mirando hacia donde estaba). */
    private static final EntityDataAccessor<Boolean> DATA_FLEE =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LOOK =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);
    /** Jugador al que sigue (vacío = no sigue a nadie). */
    private static final EntityDataAccessor<Optional<UUID>> DATA_FOLLOW =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Distancia máxima (en bloques) para cogerla. */
    public static final double PICKUP_REACH = 5.0D;

    // ---- Seguir al jugador ----
    /** Empieza a andar cuando está a más de esta distancia... */
    /** Modo alejarse: deja de andar a esta distancia y vuelve a alejarse si te acercas a menos de la segunda. */
    private static final double FLEE_STOP = 18.0D, FLEE_RESUME = 14.0D;
    private static final double FOLLOW_START = 2.6D;
    /** ...y se para cuando llega a esta. */
    private static final double FOLLOW_STOP = 1.6D;
    /** A partir de aquí corre (humanoide). */
    private static final double FOLLOW_RUN_DIST = 7.0D;
    /** Si se queda más lejos que esto, se teletransporta junto al jugador. */
    private static final double FOLLOW_TELEPORT_DIST = 24.0D;
    /** Bloques por tick. */
    private static final double SPEED_WALK = 0.20D, SPEED_RUN = 0.30D, SPEED_HOP = 0.17D;

    /** Solo servidor: ahora mismo está yendo hacia el jugador. */
    private boolean followMoving;

    /** Tick (del cliente) en el que empezó el estado actual; sirve para animar. */
    public int stateStartTick = 0;

    // ---- Solo cliente: hacia dónde mira (sigue al jugador con la mirada) ----
    public float lookYaw, lookYawO, lookPitch, lookPitchO;
    public int lookTick = -1;
    /** Solo cliente: velocidad horizontal suavizada en bloques/tick (para elegir caminar/correr). */
    public float moveSpeed;
    /** Solo cliente: reloj de la animación de caminar (avanza más rápido cuanto más rápido va). */
    public float walkClock;

    public VerityEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true); // solo creativo / comandos pueden quitarla
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STATE, 0);
        this.entityData.define(DATA_RAINBOW, false);
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_HOLDER, -1);
        this.entityData.define(DATA_FOLLOW, Optional.empty());
        this.entityData.define(DATA_LOOK, true);
        this.entityData.define(DATA_FLEE, false);
    }

    public VerityState getState() {
        return VerityState.byId(this.entityData.get(DATA_STATE));
    }

    public int getStateId() {
        return this.entityData.get(DATA_STATE);
    }

    public void setState(VerityState state) {
        this.entityData.set(DATA_STATE, state.id);
    }

    public boolean isRainbow() {
        return this.entityData.get(DATA_RAINBOW);
    }

    public void setRainbow(boolean value) {
        this.entityData.set(DATA_RAINBOW, value);
    }

    public VerityVariant getVariant() {
        return VerityVariant.byId(this.entityData.get(DATA_VARIANT));
    }

    /** ¿Te sigue con la mirada? */
    public boolean isLookingAtPlayers() {
        return this.entityData.get(DATA_LOOK);
    }

    /**
     * Servidor: enciende o apaga el seguimiento con la mirada. Al apagarlo se queda mirando hacia
     * {@code facing} (el jugador que lo apagó), así la orientación se guarda con el mundo.
     */
    public void setLookingAtPlayers(boolean value, @Nullable Player facing) {
        this.entityData.set(DATA_LOOK, value);
        if (!value && facing != null) {
            double dx = facing.getX() - getX(), dz = facing.getZ() - getZ();
            if (dx * dx + dz * dz > 1.0E-4D) {
                float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
                this.setYRot(yaw);
                this.yRotO = yaw;
                this.yBodyRot = yaw;
                this.yHeadRot = yaw;
            }
        }
    }

    /** Cambia la variante. Si cambia el tipo de modelo (esfera <-> humanoide) vuelve a "quieta". */
    public void setVariant(VerityVariant variant) {
        this.entityData.set(DATA_VARIANT, variant.id);
        if (!getState().isAvailableFor(variant)) setState(VerityState.IDLE); // ese gesto no existe en la nueva variante
    }

    // ------------------------------------------------------------------
    // Tamaño: la esfera es pequeña, la humanoide mide como un jugador
    // ------------------------------------------------------------------

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (isHeld()) return EntityDimensions.scalable(0.2F, 0.2F); // en la mano no estorba al construir
        VerityVariant.Type t = getVariant().type;
        return EntityDimensions.scalable(t.width, t.height);
    }

    // ------------------------------------------------------------------
    // Cogerla en la mano
    // ------------------------------------------------------------------

    public boolean isHeld() {
        return this.entityData.get(DATA_HOLDER) >= 0;
    }

    public int getHolderId() {
        return this.entityData.get(DATA_HOLDER);
    }

    /** El jugador que la lleva, o null si está suelta (o no está cargado en este lado). */
    public Player getHolderPlayer() {
        int id = getHolderId();
        if (id < 0) return null;
        Entity e = this.level().getEntity(id);
        return e instanceof Player p ? p : null;
    }

    /** La Verity que lleva ahora mismo este jugador, o null. */
    public static VerityEntity findHeldBy(Player player) {
        List<VerityEntity> list = player.level().getEntitiesOfClass(VerityEntity.class,
                player.getBoundingBox().inflate(6.0D), e -> e.getHolderId() == player.getId());
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Punto de la mano de {@code p}: delante y hacia el lado de su mano principal, a la altura
     * del pecho. Se usa en el servidor (posición real) y en el cliente (dibujo suave).
     */
    public static Vec3 handCenter(Player p, float partialTick) {
        double x = Mth.lerp((double) partialTick, p.xOld, p.getX());
        double y = Mth.lerp((double) partialTick, p.yOld, p.getY());
        double z = Mth.lerp((double) partialTick, p.zOld, p.getZ());
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, p.yRotO, p.getYRot()));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);      // hacia delante
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);     // hacia la derecha
        double side = p.getMainArm() == HumanoidArm.RIGHT ? 1.0D : -1.0D;
        double forward = 0.65D, lateral = 0.40D * side, down = 0.38D;
        return new Vec3(x + fx * forward + rx * lateral,
                y + p.getEyeHeight() - down,
                z + fz * forward + rz * lateral);
    }

    /** Cuánto hay que bajar el punto de la mano para llegar a los "pies" de la entidad (en bloques). */
    public static double heldAnchorDrop(VerityVariant variant) {
        VerityVariant.Type t = variant.type;
        return t.anchorY * t.scale * t.heldScale;
    }

    /** Servidor: el jugador la coge. */
    public void pickUp(Player player) {
        if (this.level().isClientSide || isHeld()) return;
        this.entityData.set(DATA_HOLDER, player.getId());
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.followMoving = false;
    }

    /** Servidor: el jugador la suelta donde apunta (o delante de él). */
    public void putDown(Player player) {
        if (this.level().isClientSide || !isHeld()) return;
        this.entityData.set(DATA_HOLDER, -1);

        Vec3 pos;
        HitResult hit = player.pick(PICKUP_REACH, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bh = (BlockHitResult) hit;
            Direction face = bh.getDirection();
            Vec3 p = bh.getLocation();
            if (face == Direction.UP) pos = p;
            else if (face == Direction.DOWN) pos = p.add(0.0D, -this.getBbHeight(), 0.0D);
            else pos = p.add(face.getStepX() * 0.35D, 0.0D, face.getStepZ() * 0.35D);
        } else {
            pos = player.getEyePosition().add(player.getLookAngle().scale(1.6D)).add(0.0D, -0.3D, 0.0D);
        }

        this.moveTo(pos.x, pos.y, pos.z, this.getYRot(), 0.0F);
        if (!this.level().noCollision(this)) {
            // no cabe ahí: al lado del jugador
            Vec3 look = player.getLookAngle();
            this.moveTo(player.getX() + look.x * 0.9D, player.getY(), player.getZ() + look.z * 0.9D,
                    this.getYRot(), 0.0F);
            if (!this.level().noCollision(this)) {
                this.moveTo(player.getX(), player.getY(), player.getZ(), this.getYRot(), 0.0F);
            }
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
    }

    /** Suelta a la fuerza (el jugador murió, se desconectó, cambió de dimensión...). */
    private void release() {
        this.entityData.set(DATA_HOLDER, -1);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
    }

    // ------------------------------------------------------------------
    // Seguir al jugador
    // ------------------------------------------------------------------

    @Nullable
    public UUID getFollowOwner() {
        return this.entityData.get(DATA_FOLLOW).orElse(null);
    }

    /** ¿Te sigue (camina hacia ti)? */
    public boolean isFollowing() {
        return getFollowOwner() != null && !isFleeing();
    }

    /** ¿Se aleja de ti? */
    public boolean isFleeing() {
        return this.entityData.get(DATA_FLEE) && getFollowOwner() != null;
    }

    /** Servidor: se aleja de ese jugador, o se para si es null. */
    public void setFleeOwner(@Nullable UUID owner) {
        setFollowOwner(owner);
        this.entityData.set(DATA_FLEE, owner != null);
    }

    /** Servidor: empieza a seguir a ese jugador, o deja de seguir si es null. */
    public void setFollowOwner(@Nullable UUID owner) {
        this.entityData.set(DATA_FOLLOW, Optional.ofNullable(owner));
        this.entityData.set(DATA_FLEE, false);
        if (owner != null) {
            this.followMoving = true; // al activarlo, camina hacia ti enseguida
        } else {
            this.followMoving = false;
            Vec3 dm = getDeltaMovement();
            this.setDeltaMovement(0.0D, dm.y, 0.0D); // se para en seco
        }
    }

    /** Servidor, una vez por tick: la mueve hacia su dueño. Se llama antes de la física del tick. */
    private void serverFollowTick() {
        UUID id = getFollowOwner();
        if (id == null || isHeld()) {
            followMoving = false;
            return;
        }
        Player owner = this.level().getPlayerByUUID(id);
        if (owner == null || owner.isSpectator() || !owner.isAlive()) {
            followMoving = false;
            Vec3 dm = getDeltaMovement();
            setDeltaMovement(0.0D, dm.y, 0.0D);
            return;
        }

        double dx = owner.getX() - getX(), dy = owner.getY() - getY(), dz = owner.getZ() - getZ();
        double dist2 = dx * dx + dz * dz;

        if (isFleeing()) { // modo "alejarse": camina en sentido contrario al jugador hasta ponerse lejos
            if (followMoving) {
                if (dist2 > FLEE_STOP * FLEE_STOP) followMoving = false;
            } else if (dist2 < FLEE_RESUME * FLEE_RESUME) {
                followMoving = true;
            }
            Vec3 dmf = getDeltaMovement();
            if (!followMoving) {
                setDeltaMovement(0.0D, dmf.y, 0.0D);
                return;
            }
            double distf = Math.max(Math.sqrt(dist2), 1.0E-3D);
            double[] sepf = separation();
            double fx = -dx / distf + sepf[0] * 1.5D, fz = -dz / distf + sepf[1] * 1.5D;
            double fl = Math.max(Math.sqrt(fx * fx + fz * fz), 1.0E-3D);
            fx /= fl; fz /= fl;
            boolean ballf = getVariant().type != VerityVariant.Type.FEM;
            double vyf = dmf.y;
            if (this.onGround()) {
                if (this.horizontalCollision) vyf = 0.45D;
                else if (ballf) vyf = hopVy();
            }
            double sp = ballf ? hopSpeed() : SPEED_WALK;
            setDeltaMovement(fx * sp, vyf, fz * sp);
            this.hasImpulse = true;
            float yawf = (float) (Mth.atan2(fz, fx) * (180.0D / Math.PI)) - 90.0F;
            this.setYRot(yawf);
            this.yBodyRot = yawf;
            this.yHeadRot = yawf;
            return;
        }

        if (dist2 > FOLLOW_TELEPORT_DIST * FOLLOW_TELEPORT_DIST || Math.abs(dy) > 16.0D) {
            teleportBehind(owner);
            return;
        }

        // Cada una va a SU sitio alrededor del jugador: se reparten por rango entre las que siguen al mismo dueño
        int rank = 0;
        for (VerityEntity o : this.level().getEntitiesOfClass(VerityEntity.class, owner.getBoundingBox().inflate(40.0D),
                e -> e != this && !e.isHeld() && !e.isFleeing() && id.equals(e.getFollowOwner()))) {
            if (o.getId() < getId()) rank++;
        }
        int ring = rank / 6;
        double ang = Math.toRadians((rank % 6) * 60.0D + ring * 30.0D);
        double rad = 2.4D + ring * 1.6D;
        double tx = owner.getX() + Math.cos(ang) * rad - getX();
        double tz = owner.getZ() + Math.sin(ang) * rad - getZ();
        double tdist = Math.sqrt(tx * tx + tz * tz);
        double[] sep = separation();

        if (followMoving) {
            if (tdist < 0.5D) followMoving = false;
        } else if (tdist > 1.3D) {
            followMoving = true;
        }

        Vec3 dm = getDeltaMovement();
        if (!followMoving) {
            // parada, pero si otra Verity está encima se aparta un poco
            if (sep[2] > 0.0D) {
                setDeltaMovement(sep[0] * 0.07D, dm.y, sep[1] * 0.07D);
                this.hasImpulse = true;
                followMoving = true;
            } else {
                setDeltaMovement(0.0D, dm.y, 0.0D);
            }
            return;
        }

        double ux = tx / Math.max(tdist, 1.0E-3D), uz = tz / Math.max(tdist, 1.0E-3D);
        ux += sep[0] * 1.5D; uz += sep[1] * 1.5D;
        double ul = Math.max(Math.sqrt(ux * ux + uz * uz), 1.0E-3D);
        ux /= ul; uz /= ul;
        dx = ux; dz = uz;
        boolean ball = getVariant().type != VerityVariant.Type.FEM;

        double speed = ball ? hopSpeed() : (tdist > FOLLOW_RUN_DIST ? SPEED_RUN : SPEED_WALK);
        double vy = dm.y;
        if (this.onGround()) {
            if (this.horizontalCollision) vy = 0.45D;   // hay un bloque delante: salta
            else if (ball) vy = hopVy();                  // la bola avanza a botes
        }
        setDeltaMovement(ux * speed, vy, uz * speed);
        this.hasImpulse = true;

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** Empuje para separarse de otras Verity cercanas: {x, z, cuántas hay encima (<1 bloque)}. */
    private double[] separation() {
        double sx = 0, sz = 0, n = 0;
        for (VerityEntity o : this.level().getEntitiesOfClass(VerityEntity.class, this.getBoundingBox().inflate(2.6D), e -> e != this && !e.isHeld())) {
            double ox = getX() - o.getX(), oz = getZ() - o.getZ();
            double d = Math.sqrt(ox * ox + oz * oz);
            double minD = 1.0D + (this.getBbWidth() + o.getBbWidth()) * 0.5D; // para que los modelos no se atraviesen
            if (d > minD) continue;
            if (d < 1.0E-3D) { ox = Math.cos(getId()); oz = Math.sin(getId()); d = 1.0D; }
            double w = (minD - d) / minD;
            sx += ox / d * w; sz += oz / d * w; n++;
        }
        double l = Math.sqrt(sx * sx + sz * sz);
        if (l > 1.0E-4D) { sx /= l; sz /= l; }
        return new double[]{sx, sz, n};
    }

    /** Velocidad y salto al avanzar: la esfera bota rápido, el gordito va a saltitos lentos. */
    private double hopSpeed() {
        return getVariant().type == VerityVariant.Type.OBESITY ? 0.11D : SPEED_HOP;
    }

    private double hopVy() {
        return getVariant().type == VerityVariant.Type.OBESITY ? 0.20D : 0.30D;
    }

    private void teleportBehind(Player owner) {
        Vec3 look = owner.getLookAngle();
        this.moveTo(owner.getX() - look.x * 1.5D, owner.getY(), owner.getZ() - look.z * 1.5D, this.getYRot(), 0.0F);
        if (!this.level().noCollision(this)) {
            this.moveTo(owner.getX(), owner.getY(), owner.getZ(), this.getYRot(), 0.0F);
        }
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.followMoving = false;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) serverFollowTick();
        super.tick();

        if (!this.level().isClientSide && isHeld()) {
            Player holder = getHolderPlayer();
            if (holder == null || holder.isRemoved() || !holder.isAlive() || holder.isSpectator()
                    || holder.level() != this.level()) {
                release();
                return;
            }
            Vec3 hand = handCenter(holder, 1.0F);
            // la entidad se coloca con los "pies" por debajo del punto de la mano
            this.setPos(hand.x, hand.y - heldAnchorDrop(getVariant()), hand.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0F;
        }

        if (this.level().isClientSide) {
            // velocidad horizontal suavizada: decide si se dibuja quieta, caminando o corriendo
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            float sp = (float) Math.sqrt(dx * dx + dz * dz);
            this.moveSpeed += (sp - this.moveSpeed) * 0.5F;
            if (this.moveSpeed > 0.02F) {
                // el ciclo de caminar avanza más rápido cuanto más rápido va
                float ref = this.moveSpeed > 0.25F ? 0.30F : 0.20F;
                this.walkClock += (this.moveSpeed / ref) / 20.0F;
            }
        }
    }

    /** Si la llevan en la mano no se queda en medio de los clics del jugador. */
    @Override
    public boolean isPickable() {
        return !isHeld() && super.isPickable();
    }

    @Override
    public boolean isNoGravity() {
        return isHeld() || super.isNoGravity();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_VARIANT.equals(key) || DATA_HOLDER.equals(key)) {
            this.refreshDimensions(); // la hitbox depende de la variante y de si va en la mano
        }
        if (DATA_STATE.equals(key) && this.level().isClientSide) {
            this.stateStartTick = this.tickCount;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onStateChanged(this));
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openMenu(this));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ---- Se queda quieta (salvo que la muevan nuestros métodos) ----
    @Override public boolean isNoAi() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { }
    @Override public void push(Entity entity) { }
    @Override public void push(double x, double y, double z) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed(Player player) { return false; }

    @Override
    public void travel(Vec3 travelVector) {
        if (isHeld()) return; // en la mano la coloca el tick, sin física
        if (followMoving && !this.level().isClientSide) {
            // Movimiento propio (sin la fricción ni el atributo de velocidad de los mobs): avanza lo que fijó serverFollowTick
            this.move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            Vec3 d = getDeltaMovement();
            setDeltaMovement(d.x, (d.y - 0.08D) * 0.98D, d.z);
            return;
        }
        super.travel(Vec3.ZERO); // gravedad y rozamiento; el avance horizontal lo fija serverFollowTick
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VerityState", getStateId());
        tag.putBoolean("VerityRainbow", isRainbow());
        tag.putInt("VerityVariant", getVariant().id);
        UUID owner = getFollowOwner();
        if (owner != null) tag.putUUID("VerityFollow", owner);
        tag.putBoolean("VerityLook", isLookingAtPlayers());
        tag.putBoolean("VerityFlee", this.entityData.get(DATA_FLEE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STATE, tag.getInt("VerityState"));
        this.entityData.set(DATA_RAINBOW, tag.getBoolean("VerityRainbow"));
        this.entityData.set(DATA_VARIANT, VerityVariant.byId(tag.getInt("VerityVariant")).id);
        this.entityData.set(DATA_FOLLOW, tag.hasUUID("VerityFollow")
                ? Optional.of(tag.getUUID("VerityFollow")) : Optional.empty());
        // las guardadas antes de existir este ajuste no tienen la clave: por defecto te miran
        this.entityData.set(DATA_FLEE, tag.getBoolean("VerityFlee"));
        this.entityData.set(DATA_LOOK, !tag.contains("VerityLook") || tag.getBoolean("VerityLook"));
    }
}
