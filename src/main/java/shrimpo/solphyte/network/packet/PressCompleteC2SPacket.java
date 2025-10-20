package shrimpo.solphyte.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import shrimpo.solphyte.blockentity.PressBlockEntity;

import java.util.function.Supplier;

public class PressCompleteC2SPacket {
    private final BlockPos pos;

    public PressCompleteC2SPacket(BlockPos pos) { this.pos = pos; }

    public static void encode(PressCompleteC2SPacket pkt, FriendlyByteBuf buf) { buf.writeBlockPos(pkt.pos); }

    public static PressCompleteC2SPacket decode(FriendlyByteBuf buf) { return new PressCompleteC2SPacket(buf.readBlockPos()); }

    public static void handle(PressCompleteC2SPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Level level = player.level();
            if (level == null) return;
            BlockEntity be = level.getBlockEntity(pkt.pos);
            if (be instanceof PressBlockEntity pb) {
                pb.onPressComplete(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

