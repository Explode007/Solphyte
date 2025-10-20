package shrimpo.solphyte.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.network.packet.GrappleNodeS2CPacket;
import shrimpo.solphyte.network.packet.GrappleStartC2SPacket;
import shrimpo.solphyte.network.packet.GrappleStopC2SPacket;
import shrimpo.solphyte.network.packet.MicroscopeFoundC2SPacket;
import shrimpo.solphyte.network.packet.PressCompleteC2SPacket;

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

        CHANNEL.registerMessage(nextId(), MicroscopeFoundC2SPacket.class,
                MicroscopeFoundC2SPacket::encode,
                MicroscopeFoundC2SPacket::decode,
                MicroscopeFoundC2SPacket::handle);

        CHANNEL.registerMessage(nextId(), PressCompleteC2SPacket.class,
                PressCompleteC2SPacket::encode,
                PressCompleteC2SPacket::decode,
                PressCompleteC2SPacket::handle);
    }

    public static <MSG> void sendToServer(MSG msg) {
        CHANNEL.sendToServer(msg);
    }

    public static <MSG> void sendTo(ServerPlayer player, MSG msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
