package shrimpo.solphyte.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.Config;
import shrimpo.solphyte.network.SolphyteNetwork;
import shrimpo.solphyte.network.packet.GrappleStartC2SPacket;
import shrimpo.solphyte.network.packet.GrappleStopC2SPacket;
import org.lwjgl.glfw.GLFW;
import shrimpo.solphyte.registry.SolphyteEffect;
import net.minecraft.core.BlockPos;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class Keybinds {
    public static final KeyMapping GRAPPLE = new KeyMapping(
            "key.solphyte.grapple",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.gameplay");

    // Register the key mapping on the MOD bus via a nested subscriber to avoid an extra public top-level class
    @Mod.EventBusSubscriber(modid = Solphyte.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(GRAPPLE);
        }
    }

    private static boolean wasPressed = false;
    private static Vec3 clientAnchor = null; // local predicted anchor while grappling
    private static double clientRopeLen = 0.0; // predicted rope length with slack
    private static long clientCooldownEnd = 0L; // gameTime tick when grapple can be used again locally
    private static boolean clientLastUsedExistingNode = false; // track if last start used an existing node

    // Expose the current anchor for potential renderers (optional)
    public static Vec3 getClientAnchor() { return clientAnchor; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean pressed = GRAPPLE.isDown();
        long now = mc.level.getGameTime();
        boolean onCooldown = now < clientCooldownEnd;

        if (pressed && !wasPressed) {
            // Allow bypassing cooldown if aiming directly at a node
            boolean aimingAtNode;
            {
                double maxRange = Config.grMaxAnchorDistance;
                Vec3 eye = mc.player.getEyePosition(1.0f);
                Vec3 look = mc.player.getLookAngle().normalize();
                double hitRadius = Math.max(1.0, Config.grNodeHitRadius);
                aimingAtNode = GrappleNodeClient.rayHitNode(eye, look, maxRange, hitRadius) != null;
            }

            if (onCooldown && !aimingAtNode) {
                int ticks = (int)Math.max(0, clientCooldownEnd - now);
                int secs = (ticks + 19) / 20;
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.solphyte.grapple_cooldown", secs), true);
            } else {
                clientAnchor = tryStart(mc.player, mc.level); // capture local anchor, may be null if no effect
            }
        } else if (!pressed && wasPressed) {
            boolean hadAnchor = clientAnchor != null;
            clientAnchor = null;
            clientRopeLen = 0.0;
            // Start local cooldown immediately on release only if a grapple was actually active and wasn't using an existing node
            if (hadAnchor && !clientLastUsedExistingNode) {
                clientCooldownEnd = now + Config.cooldownTicks();
            }
            clientLastUsedExistingNode = false; // reset after release
            SolphyteNetwork.sendToServer(new GrappleStopC2SPacket());
        }

        // Lightweight client-side prediction; visuals moved to GrappleRopeRenderer
        if (pressed && clientAnchor != null && mc.player.hasEffect(SolphyteEffect.STRINGING.get())) {
            Vec3 eye = mc.player.getEyePosition(1.0f);
            Vec3 toAnchor = clientAnchor.subtract(eye);
            double dist = toAnchor.length();
            if (dist > 1e-3) {
                Vec3 dirToAnchor = toAnchor.scale(1.0 / dist);
                Vec3 look = mc.player.getLookAngle();

                // Reel in rope slightly each tick on client to match server feel
                double reelRate = 0.05; // blocks per tick
                clientRopeLen = Math.max(Config.grMinDistance, clientRopeLen - reelRate);

                // Distance-based scaling to match server: farther => weaker look, stronger reel-in
                double ref = Math.max(1.0, Config.grMaxAnchorDistance);
                double distFactor = Math.min(1.0, dist / ref); // 0 near, 1 far
                double lookAtten = 1.0 - distFactor; // 1 near, 0 far
                double wLook = 0.65 * lookAtten;
                double wAnchor = 0.35 + 0.35 * distFactor; // 0.35 near, 0.70 far

                Vec3 blended = look.normalize().scale(wLook).add(dirToAnchor.scale(wAnchor)).normalize();

                // If taut or nearly taut and the blended push would move away from anchor, remove the outward component
                boolean atLimit = dist >= clientRopeLen - 0.02;
                double radial = blended.dot(dirToAnchor); // positive = toward anchor
                if (atLimit && radial < 0) {
                    // Remove the away (negative radial) component
                    blended = blended.subtract(dirToAnchor.scale(radial)).normalize();
                }

                // Keep client prediction conservative; server applies full logic
                double base = 0.24; // per-tick push; small to avoid overshoot
                mc.player.push(blended.x * base, blended.y * base, blended.z * base);
                mc.player.fallDistance = 0;

                // Rope visuals handled by GrappleRopeRenderer (removed particles)
            }
        }

        wasPressed = pressed;
    }

    private static Vec3 tryStart(Player player, Level level) {
        if (!player.hasEffect(SolphyteEffect.STRINGING.get())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.solphyte.need_stringing"), true);
            return null;
        }
        // Raycast towards look direction up to max range (from config)
        double maxRange = Config.grMaxAnchorDistance; // configurable
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(maxRange));

        // Prefer existing node if the aim ray intersects a node sphere client-side
        double hitRadius = Math.max(1.0, Config.grNodeHitRadius);
        Vec3 nodeHit = GrappleNodeClient.rayHitNode(eye, look, maxRange, hitRadius);
        Vec3 anchor;
        boolean usedExisting = false;
        if (nodeHit != null) {
            anchor = nodeHit;
            usedExisting = true;
        } else {
            // Do block clip fallback
            net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player);
            net.minecraft.world.phys.BlockHitResult hit = level.clip(ctx);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                anchor = Vec3.atCenterOf(hit.getBlockPos());
            } else {
                // No block hit: conjure a midair anchor at the end point (strand-like)
                anchor = end;
            }
            // Snap to nearest node within snap radius if present
            double snapR = Math.max(1.25, Config.grSnapRadius);
            Vec3 nearest = GrappleNodeClient.nearestNode(anchor, snapR);
            if (nearest != null) { anchor = nearest; usedExisting = true; }
        }

        // Initialize predicted rope length (start distance + slack, clamped by min)
        double startDist = anchor.subtract(eye).length();
        clientRopeLen = Math.max(Config.grMinDistance, startDist + Config.grStartSlack);

        // Client-side immediate visual as a fallback; server will also refresh/sync
        final int lifetimeTicks = 20 * 20; // keep in sync with server GrappleNodesData
        GrappleNodeClient.addOrRefresh(BlockPos.containing(anchor), lifetimeTicks);

        clientLastUsedExistingNode = usedExisting;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.solphyte.grapple_start"), true);
        SolphyteNetwork.sendToServer(new GrappleStartC2SPacket());
        return anchor;
    }
}
