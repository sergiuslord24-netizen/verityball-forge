package com.verityball.client;

import com.verityball.entity.VerityEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Sonido que sigue a Verity y se corta cuando cambia de estado. */
public class VeritySound extends AbstractTickableSoundInstance {
    private final VerityEntity entity;
    private final int stateId;

    public VeritySound(VerityEntity entity, SoundEvent sound, boolean loop) {
        super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.stateId = entity.getStateId();
        this.looping = loop;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (entity.isRemoved() || entity.getStateId() != stateId) {
            this.stop();
            return;
        }
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }
}
