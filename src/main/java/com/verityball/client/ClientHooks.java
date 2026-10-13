package com.verityball.client;

import com.verityball.ModSounds;
import com.verityball.VerityState;
import com.verityball.entity.VerityEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

/** Todo lo que solo existe en el cliente (menú, sonidos). */
public class ClientHooks {
    public static void openMenu(VerityEntity entity) {
        Minecraft.getInstance().setScreen(new VerityMenuScreen(entity));
    }

    public static void onStateChanged(VerityEntity entity) {
        VerityState state = entity.getState();
        SoundEvent sound = ModSounds.byName(state.sound);
        if (sound == null) return;
        Minecraft.getInstance().getSoundManager().play(new VeritySound(entity, sound, state.loopSound));
    }
}
