package shrimpo.solphyte.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import shrimpo.solphyte.Config;
import shrimpo.solphyte.capability.StringGrapple;
import shrimpo.solphyte.events.GrappleNodesData;
import shrimpo.solphyte.network.SolphyteNetwork;
import shrimpo.solphyte.registry.SolphyteEffect;

import java.util.function.Supplier;

public class GrappleStartC2SPacket {
    public static GrappleStartC2SPacket decode(net.minecraft.network.FriendlyByteBuf buf) { return new GrappleStartC2SPacket(); }
    public static void encode(GrappleStartC2SPacket pkt, net.minecraft.network.FriendlyByteBuf buf) {}

    public static void handle(GrappleStartC2SPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (!player.hasEffect(SolphyteEffect.STRINGING.get())) {
                return;
            }

            // Range and initial ray data
            double maxRange = Config.grMaxAnchorDistance;
            Vec3 eye = player.getEyePosition(1.0f);
            Vec3 look = player.getLookAngle();
            Vec3 end = eye.add(look.scale(maxRange));

            GrappleNodesData data = GrappleNodesData.get(player.serverLevel());

            // First, check for node intersection along the segment (t in [0, len]) using a sphere radius
            Vec3 seg = end.subtract(eye);
            double len = seg.length();
            Vec3 dir = len > 1e-6 ? seg.scale(1.0 / len) : new Vec3(0, 0, 0);
            double bestT = Double.POSITIVE_INFINITY;
            Vec3 bestNode = null;
            // Enforce a generous minimum: 1.0m radius (~2m diameter)
            double hitR = Math.max(1.0, Config.grNodeHitRadius);
            double hitR2 = hitR * hitR;
            for (BlockPos p : data.getNodes()) {
                Vec3 c = Vec3.atCenterOf(p);
                Vec3 ec = c.subtract(eye);
                double t = ec.dot(dir); // distance along the ray in meters
                if (t < 0 || t > len) continue;
                Vec3 closest = eye.add(dir.scale(t));
                double dsq = c.subtract(closest).lengthSqr();
                if (dsq <= hitR2) {
                    if (t < bestT) {
                        bestT = t;
                        bestNode = c;
                    }
                }
            }

            boolean aimedAtNode = bestNode != null;
            Vec3 anchor;
            boolean usingExisting;
            if (aimedAtNode) {
                anchor = bestNode;
                usingExisting = true;
            } else {
                // No node sphere hit; do block clip fallback
                ClipContext ctxRay = new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
                BlockHitResult hit = player.level().clip(ctxRay);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos faceAir = hit.getBlockPos().relative(hit.getDirection());
                    if (player.level().isEmptyBlock(faceAir)) {
                        anchor = Vec3.atCenterOf(faceAir);
                    } else {
                        Vec3 hp = hit.getLocation();
                        // nudge outward slightly along the face normal so the node sits just in front of the surface
                        Vec3 nrm = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                        anchor = hp.add(nrm.scale(0.51));
                    }
                } else {
                    anchor = end;
                }

                // Snap anchor to nearest existing node within snap radius (magnet), if any
                double snapR = Math.max(1.25, Config.grSnapRadius); // at least a block
                Vec3 existing = data.nearestNodeCenter(anchor, snapR * snapR);
                if (existing != null) {
                    anchor = existing;
                    usingExisting = true;
                } else {
                    usingExisting = false;
                }
            }

            // Final variables for lambda capture
            final Vec3 fAnchor = anchor;
            final boolean fUsingExisting = usingExisting;
            final boolean fAimedAtNode = aimedAtNode;
            final double fStartDist = fAnchor.subtract(eye).length();
            final double fRopeStart = Math.max(Config.grMinDistance, fStartDist + Config.grStartSlack);
            final BlockPos nodePos = BlockPos.containing(fAnchor);

            // Activate the grapple if not on cooldown, unless we are aiming at an existing node (bypass)
            player.getCapability(StringGrapple.CAPABILITY).ifPresent(cap -> {
                if (!fAimedAtNode && player.tickCount < cap.getNextAvailableTick()) {
                    int ticks = Math.max(0, cap.getNextAvailableTick() - player.tickCount);
                    int secs = (ticks + 19) / 20;
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.solphyte.grapple_cooldown", secs), true);
                    // Do NOT spawn or refresh any nodes or visuals when blocked by cooldown
                    return;
                }

                // Only now that we're allowed to start do we sync visuals and (if applicable) refresh server node
                if (fUsingExisting) {
                    data.addOrRefresh(nodePos, player.serverLevel());
                }
                SolphyteNetwork.sendTo(player, new GrappleNodeS2CPacket(nodePos, GrappleNodesData.getLifetimeTicks()));

                cap.setAnchor(fAnchor);
                cap.setStartTick(player.tickCount);
                cap.setRopeLength(fRopeStart);
                cap.setUsingExistingNode(fUsingExisting);
                cap.setLastUsingExistingNode(fUsingExisting);
                cap.setActive(true);
                if (!player.isNoGravity()) player.setNoGravity(true);

                // Cooldown is applied on release packet only
            });
        });
        ctx.get().setPacketHandled(true);
    }
}