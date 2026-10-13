package com.verityball;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, VerityBallMod.MODID);

    public static final RegistryObject<SoundEvent> TALK = reg("talk");
    public static final RegistryObject<SoundEvent> CREEPYTALK = reg("creepytalk");
    public static final RegistryObject<SoundEvent> DANCE = reg("dance");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(VerityBallMod.MODID, name)));
    }

    public static SoundEvent byName(String name) {
        if (name == null) return null;
        switch (name) {
            case "talk": return TALK.get();
            case "creepytalk": return CREEPYTALK.get();
            case "dance": return DANCE.get();
            default: return null;
        }
    }
}
