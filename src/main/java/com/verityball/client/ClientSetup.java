package com.verityball.client;

import com.verityball.VerityBallMod;
import com.verityball.VerityVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VerityBallMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VerityBallMod.VERITY.get(), VerityRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.PICKUP);
    }

    /** Colorea el huevo generador según la variante que lleva. */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return 0x000000; // manchas del huevo
            CompoundTag entityTag = stack.getTagElement("EntityTag");
            int id = entityTag != null ? entityTag.getInt("VerityVariant") : 0;
            return VerityVariant.byId(id).color;
        }, VerityBallMod.VERITY_SPAWN_EGG.get());
    }
}
