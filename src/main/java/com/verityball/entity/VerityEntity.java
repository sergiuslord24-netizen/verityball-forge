package com.verityball.entity;

import com.verityball.VerityState;
import com.verityball.client.ClientHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Verity: un mob que se queda quieto y se controla desde un menú. */
public class VerityEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAINBOW =
            SynchedEntityData.defineId(VerityEntity.class, EntityDataSerializers.BOOLEAN);

    /** Tick (del cliente) en el que empezó el estado actual; sirve para animar. */
    public int stateStartTick = 0;

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
        super.travel(Vec3.ZERO); // solo gravedad, no camina
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("VerityState", getStateId());
        tag.putBoolean("VerityRainbow", isRainbow());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_STATE, tag.getInt("VerityState"));
        this.entityData.set(DATA_RAINBOW, tag.getBoolean("VerityRainbow"));
    }
}
