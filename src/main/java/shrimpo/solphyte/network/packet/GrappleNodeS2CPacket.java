package shrimpo.solphyte.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import shrimpo.solphyte.client.GrappleNodeClient;

import java.util.function.Supplier;

public class GrappleNodeS2CPacket {
    private final BlockPos pos;

private final int lifetimeTicks;

    public GrappleNodeS2CPacket(BlockPos pos, int lifetimeTicks) {
        this.pos = pos;
        this.lifetimeTicks = lifetimeTicks;
    }

    public static GrappleNodeS2CPacket decode(net.minecraft.network.FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int life = buf.readVarInt();
        return new GrappleNodeS2CPacket(pos, life);
    }

    public static void encode(GrappleNodeS2CPacket pkt, net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeVarInt(pkt.lifetimeTicks);
    }

    public static void handle(GrappleNodeS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(GrappleNodeS2CPacket msg) {
        GrappleNodeClient.addOrRefresh(msg.pos, msg.lifetimeTicks);
    }
}


