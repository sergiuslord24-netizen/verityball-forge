package com.verityball.network;

import com.verityball.VerityState;
import com.verityball.entity.VerityEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: cambiar animación (0..6) o alternar arcoíris (action = TOGGLE_RAINBOW). */
public class SetStatePacket {
    public static final int TOGGLE_RAINBOW = 100;

    private final int entityId;
    private final int action;

    public SetStatePacket(int entityId, int action) {
        this.entityId = entityId;
        this.action = action;
    }

    public static void encode(SetStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.action);
    }

    public static SetStatePacket decode(FriendlyByteBuf buf) {
        return new SetStatePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(SetStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            Entity e = player.level().getEntity(msg.entityId);
            if (!(e instanceof VerityEntity verity)) return;
            if (player.distanceToSqr(verity) > 64.0D) return; // máx. 8 bloques

            if (msg.action == TOGGLE_RAINBOW) {
                verity.setRainbow(!verity.isRainbow());
            } else {
                VerityState wanted = VerityState.byId(msg.action);
                // pulsar de nuevo la animación activa la apaga
                if (wanted == verity.getState() && wanted != VerityState.IDLE) wanted = VerityState.IDLE;
                verity.setState(wanted);
            }
        });
        c.setPacketHandled(true);
    }
}
