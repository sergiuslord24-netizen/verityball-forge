package com.verityball.network;

import com.verityball.VerityBallMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VerityBallMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, SetStatePacket.class,
                SetStatePacket::encode, SetStatePacket::decode, SetStatePacket::handle);
        CHANNEL.registerMessage(1, PickupPacket.class,
                PickupPacket::encode, PickupPacket::decode, PickupPacket::handle);
    }
}
