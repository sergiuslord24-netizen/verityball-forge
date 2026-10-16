package com.verityball.network;

import com.verityball.VerityState;
import com.verityball.VerityVariant;
import com.verityball.entity.VerityEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: cambiar animación (id de VerityState), alternar arcoíris (TOGGLE_RAINBOW),
 * alternar "seguirme" (TOGGLE_FOLLOW), alternar "mirarme" (TOGGLE_LOOK) o fijar la variante (SET_VARIANT_BASE + id de variante).
 */
public class SetStatePacket {
    public static final int TOGGLE_RAINBOW = 100;
    public static final int TOGGLE_FOLLOW = 101;
    public static final int TOGGLE_LOOK = 102;
    public static final int TOGGLE_FLEE = 103;
    public static final int SET_VARIANT_BASE = 200;

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
            } else if (msg.action == TOGGLE_FOLLOW) {
                // quien lo activa pasa a ser a quien sigue; pulsar otra vez la para
                verity.setFollowOwner(verity.isFollowing() ? null : player.getUUID());
            } else if (msg.action == TOGGLE_FLEE) {
                verity.setFleeOwner(verity.isFleeing() ? null : player.getUUID());
            } else if (msg.action == TOGGLE_LOOK) {
                verity.setLookingAtPlayers(!verity.isLookingAtPlayers(), player);
            } else if (msg.action >= SET_VARIANT_BASE) {
                verity.setVariant(VerityVariant.byId(msg.action - SET_VARIANT_BASE));
            } else {
                VerityState wanted = VerityState.byId(msg.action);
                if (!wanted.isAvailableFor(verity.getVariant())) return; // ese estado no existe para este modelo
                // pulsar de nuevo la animación activa la apaga
                if (wanted == verity.getState() && wanted != VerityState.IDLE) wanted = VerityState.IDLE;
                verity.setState(wanted);
            }
        });
        c.setPacketHandled(true);
    }
}
