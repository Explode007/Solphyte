package shrimpo.solphyte.network.packet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import shrimpo.solphyte.capability.StringGrapple;
import shrimpo.solphyte.Config;

import java.util.function.Supplier;

public class GrappleStopC2SPacket {
    public GrappleStopC2SPacket() {}

    public static GrappleStopC2SPacket decode(net.minecraft.network.FriendlyByteBuf buf) { return new GrappleStopC2SPacket(); }
    public static void encode(GrappleStopC2SPacket pkt, net.minecraft.network.FriendlyByteBuf buf) {}

    public static void handle(GrappleStopC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            player.getCapability(StringGrapple.CAPABILITY).ifPresent(cap -> {
                boolean wasActive = cap.isActive();
                if (wasActive) {
                    // Apply cooldown ONLY if the grapple did NOT use an existing node
                    if (!cap.getLastUsingExistingNode()) {
                        int target = player.tickCount + Config.cooldownTicks();
                        cap.setNextAvailableTick(Math.max(cap.getNextAvailableTick(), target));
                    }
                    // Always apply a brief fall damage grace
                    cap.setFallGraceEndTick(player.tickCount + 8);
                }

                cap.setActive(false);
                cap.setUsingExistingNode(false);
            });
            // Immediately soften fall for this tick
            player.fallDistance = 0.0F;
            if (player.isNoGravity()) player.setNoGravity(false);
        });
        ctx.get().setPacketHandled(true);
    }
}