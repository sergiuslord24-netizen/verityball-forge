package com.verityball.client;

import com.verityball.VerityBallMod;
import com.verityball.entity.VerityEntity;
import com.verityball.network.ModNetwork;
import com.verityball.network.PickupPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Coger / soltar a Verity con la tecla (E por defecto) al apuntarla. */
@Mod.EventBusSubscriber(modid = VerityBallMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // START: corre antes de que Minecraft procese las teclas, así podemos "quedarnos"
        // con la E antes de que abra el inventario.
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean pressed = false;
        while (ModKeys.PICKUP.consumeClick()) pressed = true;

        if (mc.screen != null || player.isSpectator()) return;

        VerityEntity held = findHeld(player);
        VerityEntity aimed = held == null ? findAimed(player) : null;

        if (held != null) {
            mc.gui.setOverlayMessage(
                    Component.translatable("gui.verityball.hint.drop", ModKeys.PICKUP.getTranslatedKeyMessage()), false);
        } else if (aimed != null) {
            mc.gui.setOverlayMessage(
                    Component.translatable("gui.verityball.hint.pickup", ModKeys.PICKUP.getTranslatedKeyMessage()), false);
        }

        if (pressed && (held != null || aimed != null)) {
            ModNetwork.CHANNEL.sendToServer(new PickupPacket(aimed != null ? aimed.getId() : -1));
            // Si la tecla de coger es la misma que la del inventario (E), no abrir el inventario
            if (mc.options.keyInventory.getKey().getValue() == ModKeys.PICKUP.getKey().getValue()) {
                while (mc.options.keyInventory.consumeClick()) { }
            }
        }
    }

    /** La Verity que este jugador lleva en la mano, o null. */
    private static VerityEntity findHeld(LocalPlayer player) {
        List<VerityEntity> list = player.level().getEntitiesOfClass(VerityEntity.class,
                player.getBoundingBox().inflate(6.0D), e -> e.getHolderId() == player.getId());
        return list.isEmpty() ? null : list.get(0);
    }

    /** La Verity suelta a la que apunta el jugador (sin bloques de por medio), o null. */
    private static VerityEntity findAimed(LocalPlayer player) {
        double reach = VerityEntity.PICKUP_REACH;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof VerityEntity v && !v.isHeld() && !e.isSpectator(), reach * reach);
        if (hit == null) return null;

        HitResult block = player.pick(reach, 1.0F, false);
        if (block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) {
            return null; // hay un bloque delante
        }
        return (VerityEntity) hit.getEntity();
    }
}
