package org.example.shrimpo.solphyte.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.example.shrimpo.solphyte.Solphyte;
import org.example.shrimpo.solphyte.network.packet.GrappleNodeS2CPacket;
import org.example.shrimpo.solphyte.network.packet.GrappleStartC2SPacket;
import org.example.shrimpo.solphyte.network.packet.GrappleStopC2SPacket;

public class SolphyteNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static int packetId = 0;
    private static SimpleChannel CHANNEL;

    private static int nextId() { return packetId++; }

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Solphyte.MODID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        CHANNEL.registerMessage(nextId(), GrappleStartC2SPacket.class,
                GrappleStartC2SPacket::encode,
                GrappleStartC2SPacket::decode,
                GrappleStartC2SPacket::handle);

        CHANNEL.registerMessage(nextId(), GrappleStopC2SPacket.class,
                GrappleStopC2SPacket::encode,
                GrappleStopC2SPacket::decode,
                GrappleStopC2SPacket::handle);

        CHANNEL.registerMessage(nextId(), GrappleNodeS2CPacket.class,
                GrappleNodeS2CPacket::encode,
                GrappleNodeS2CPacket::decode,
                GrappleNodeS2CPacket::handle);
    }

    public static <MSG> void sendToServer(MSG msg) {
        CHANNEL.sendToServer(msg);
    }

    public static <MSG> void sendTo(ServerPlayer player, MSG msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
