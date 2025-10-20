package shrimpo.solphyte.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import shrimpo.solphyte.blockentity.MicroscopeBlockEntity;

import java.util.function.Supplier;

public class MicroscopeFoundC2SPacket {
    private final BlockPos pos;

    public MicroscopeFoundC2SPacket(BlockPos pos) { this.pos = pos; }

    public static void encode(MicroscopeFoundC2SPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
    }

    public static MicroscopeFoundC2SPacket decode(FriendlyByteBuf buf) {
        return new MicroscopeFoundC2SPacket(buf.readBlockPos());
    }

    public static void handle(MicroscopeFoundC2SPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            if (level == null) return;
            BlockEntity be = level.getBlockEntity(pkt.pos);
            if (be instanceof MicroscopeBlockEntity mb) {
                mb.onFoundOne();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

