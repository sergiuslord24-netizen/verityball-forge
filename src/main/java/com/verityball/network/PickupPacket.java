package com.verityball.network;

import com.verityball.entity.VerityEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: el jugador pulsó la tecla de coger.
 * Si ya lleva una Verity, la suelta; si no, coge la que apunta ({@code targetId}).
 */
public class PickupPacket {
    private final int targetId;

    public PickupPacket(int targetId) {
        this.targetId = targetId;
    }

    public static void encode(PickupPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.targetId + 1); // -1 (nada) se guarda como 0
    }

    public static PickupPacket decode(FriendlyByteBuf buf) {
        return new PickupPacket(buf.readVarInt() - 1);
    }

    public static void handle(PickupPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null || player.isSpectator()) return;

            VerityEntity held = VerityEntity.findHeldBy(player);
            if (held != null) {
                held.putDown(player);
                return;
            }

            if (msg.targetId < 0) return;
            Entity e = player.level().getEntity(msg.targetId);
            if (!(e instanceof VerityEntity verity) || verity.isHeld()) return;
            double reach = VerityEntity.PICKUP_REACH + 1.0D; // un poco de margen por el retraso de red
            if (player.distanceToSqr(verity) > reach * reach) return;
            verity.pickUp(player);
        });
        c.setPacketHandled(true);
    }
}
