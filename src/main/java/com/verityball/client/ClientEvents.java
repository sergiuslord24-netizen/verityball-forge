package com.verityball.client;

import com.verityball.VerityBallMod;
import com.verityball.entity.VerityEntity;
import com.verityball.network.ModNetwork;
import com.verityball.network.PickupPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.verityball.VerityState;
import com.verityball.network.SetStatePacket;
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

        if (mc.screen != null || player.isSpectator()) { zeroDown = false; return; }

        quickMenu(mc, player);

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

    private static boolean zeroDown = false;

    /** Menú rápido para grabar: apuntando a Verity, teclas 5-0 (sin abrir el menú). */
    private static void quickMenu(Minecraft mc, LocalPlayer player) {
        int action = -1;
        // 5..9 son las teclas de la barra de objetos 5..9: las "consumimos" antes de que cambien de casilla
        VerityEntity target = null;
        boolean any = false;
        for (int i = 4; i <= 8; i++) {
            if (mc.options.keyHotbarSlots[i].consumeClick()) {
                if (!any) target = findAimedWithin(player, 7.5D);
                any = true;
                if (target != null) {
                    switch (i) {
                        case 4 -> action = SetStatePacket.TOGGLE_FOLLOW;
                        case 5 -> action = SetStatePacket.TOGGLE_FLEE;
                        case 6 -> action = VerityState.TALK.id;
                        case 7 -> action = VerityState.IDLE.id;
                        default -> action = VerityState.ITS_COMING.id;
                    }
                    ModNetwork.CHANNEL.sendToServer(new SetStatePacket(target.getId(), action));
                } else {
                    // no apuntamos a Verity: la tecla funciona como siempre (cambiar de casilla)
                    player.getInventory().selected = i;
                }
            }
        }
        boolean zero = InputConstants.isKeyDown(mc.getWindow().getWindow(), 48 /* GLFW_KEY_0 */);
        if (zero && !zeroDown) {
            VerityEntity t = findAimedWithin(player, 7.5D);
            if (t != null) ModNetwork.CHANNEL.sendToServer(new SetStatePacket(t.getId(), VerityState.DANCE.id));
        }
        zeroDown = zero;
    }

    private static VerityEntity findAimedWithin(LocalPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof VerityEntity v && !v.isHeld() && !e.isSpectator(), reach * reach);
        if (hit == null) return null;
        HitResult block = player.pick(reach, 1.0F, false);
        if (block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) return null;
        return (VerityEntity) hit.getEntity();
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
