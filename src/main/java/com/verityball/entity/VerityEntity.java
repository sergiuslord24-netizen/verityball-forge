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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
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

import java.util.List;

/** Verity: un mob que se queda quieto y se controla desde un menú. Se puede coger en la mano. */
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

    /** Distancia máxima (en bloques) para cogerla. */
    public static final double PICKUP_REACH = 5.0D;

    /** Tick (del cliente) en el que empezó el estado actual; sirve para animar. */
    public int stateStartTick = 0;

    // ---- Solo cliente: hacia dónde mira (sigue al jugador con la mirada) ----
    public float lookYaw, lookYawO, lookPitch, lookPitchO;
    public int lookTick = -1;

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

    public void setVariant(VerityVariant variant) {
        this.entityData.set(DATA_VARIANT, variant.id);
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
     * Centro de la bola cuando está en la mano de {@code p}: delante y hacia el lado de su mano
     * principal, a la altura del pecho. Se usa en el servidor (posición real) y en el cliente (dibujo suave).
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

    /** Servidor: el jugador la coge. */
    public void pickUp(Player player) {
        if (this.level().isClientSide || isHeld()) return;
        this.entityData.set(DATA_HOLDER, player.getId());
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
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

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && isHeld()) {
            Player holder = getHolderPlayer();
            if (holder == null || holder.isRemoved() || !holder.isAlive() || holder.isSpectator()
                    || holder.level() != this.level()) {
                release();
                return;
            }
            Vec3 hand = handCenter(holder, 1.0F);
            // la entidad se coloca con los "pies" por debajo del centro de la bola
            this.setPos(hand.x, hand.y - 0.275D * 0.5D, hand.z);
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0.0F;
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

    // ---- Se queda quieta ----
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
        super.travel(Vec3.ZERO); // solo gravedad, no camina
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VerityState", getStateId());
        tag.putBoolean("VerityRainbow", isRainbow());
        tag.putInt("VerityVariant", getVariant().id);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STATE, tag.getInt("VerityState"));
        this.entityData.set(DATA_RAINBOW, tag.getBoolean("VerityRainbow"));
        this.entityData.set(DATA_VARIANT, VerityVariant.byId(tag.getInt("VerityVariant")).id);
    }
}
