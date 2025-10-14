package org.example.shrimpo.solphyte.network.packet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.example.shrimpo.solphyte.capability.StringGrapple;
import org.example.shrimpo.solphyte.Config;

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
                if (cap.isActive() && !cap.isUsingExistingNode()) {
                    int cd = Config.grCooldownTicks;
                    if (cd <= 0) cd = 120; // fallback 6s
                    cap.setNextAvailableTick(player.tickCount + cd);
                }
                cap.setActive(false);
                cap.setUsingExistingNode(false);
            });
            // Immediately restore gravity
            if (player.isNoGravity()) player.setNoGravity(false);
        });
        ctx.get().setPacketHandled(true);
    }
}