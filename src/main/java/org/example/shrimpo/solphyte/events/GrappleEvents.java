package org.example.shrimpo.solphyte.events;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.shrimpo.solphyte.Solphyte;
import org.example.shrimpo.solphyte.capability.StringGrapple;

@Mod.EventBusSubscriber(modid = Solphyte.MODID)
public class GrappleEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;

        player.getCapability(StringGrapple.CAPABILITY).ifPresent(cap -> {
            if (!cap.isActive()) {
                // Safety: ensure gravity is restored when not grappling
                if (player.isNoGravity()) player.setNoGravity(false);
                return;
            }

            if (!player.hasEffect(org.example.shrimpo.solphyte.registry.SolphyteEffect.STRINGING.get())) {
                if (cap.isActive() && !cap.isUsingExistingNode()) {
                    int cd = org.example.shrimpo.solphyte.Config.grCooldownTicks;
                    if (cd <= 0) cd = 120;
                    cap.setNextAvailableTick(player.tickCount + cd);
                }
                cap.setActive(false);
                cap.setUsingExistingNode(false);
                if (player.isNoGravity()) player.setNoGravity(false);
                return;
            }

            // Disable gravity while grappling to allow smooth swinging
            if (!player.isNoGravity()) player.setNoGravity(true);

            Vec3 anchor = cap.getAnchor();
            Vec3 eye = player.getEyePosition(1.0f);
            Vec3 toAnchor = anchor.subtract(eye);
            double dist = toAnchor.length();
            if (dist < 0.5) {
                if (!cap.isUsingExistingNode()) {
                    int cd = org.example.shrimpo.solphyte.Config.grCooldownTicks;
                    if (cd <= 0) cd = 120;
                    cap.setNextAvailableTick(player.tickCount + cd);
                }
                cap.setActive(false);
                cap.setUsingExistingNode(false);
                if (player.isNoGravity()) player.setNoGravity(false);
                return;
            }

            Vec3 dirToAnchor = toAnchor.scale(1.0 / Math.max(dist, 1e-6));
            Vec3 look = player.getLookAngle().normalize();
            double alignment = Math.max(0.0, look.dot(dirToAnchor));

            // Ramp factor (0.65 -> 1.0 over ~4s)
            int elapsed = Math.max(0, player.tickCount - cap.getStartTick());
            double ramp = Math.min(1.0, elapsed / 80.0);
            double factor = 0.65 + 0.35 * ramp;

            // Distance-based scaling: farther means stronger reel and weaker look, but never zero
            double ref = Math.max(1.0, org.example.shrimpo.solphyte.Config.grMaxAnchorDistance);
            double distFactor = Math.min(1.0, dist / ref); // 0 near, 1 at ~ref
            double lookAtten = 0.25 + (1.0 - distFactor) * 0.75; // min 0.25 even when far
            double anchorScale = 0.5 + 0.5 * distFactor;        // 0.5 near, 1.0 far

            // Acceleration components
            double anchorAccel = (0.50 * factor) * anchorScale; // strong pull toward anchor, stronger when far
            double lookAccel = (0.14 * factor * alignment) * lookAtten; // slightly increased, fades with distance
            Vec3 push = dirToAnchor.scale(anchorAccel).add(look.scale(lookAccel));

            Vec3 current = player.getDeltaMovement();
            Vec3 next = current.add(push);

            // Cap total speed
            double maxSpeed = 1.20; // ~24 blocks/sec at full ramp
            double targetSpeed = maxSpeed * factor;
            double nextLen = next.length();
            if (nextLen > targetSpeed) next = next.scale(targetSpeed / nextLen);

            // Cap velocity along the look direction but allow higher forward speed for better swing
            double vLook = next.dot(look);
            double vLookMax = 0.85 * targetSpeed; // allow up to ~85% of target speed forward
            if (vLook > vLookMax) {
                next = next.subtract(look.scale(vLook - vLookMax));
            }
            double vLookBackMax = 0.50 * targetSpeed; // allow more backward than before
            if (vLook < -vLookBackMax) {
                next = next.subtract(look.scale(vLook + vLookBackMax));
            }

            // Rope constraint and corrections
            double rope = cap.getRopeLength();
            double reelRate = 0.05; // blocks per tick (~1 block/sec)
            rope = Math.max(org.example.shrimpo.solphyte.Config.grMinDistance, rope - reelRate);
            cap.setRopeLength(rope);

            boolean atLimit = dist >= rope - 0.02;
            boolean beyond = dist > rope + 0.05;
            double vRad = next.dot(dirToAnchor); // + toward anchor
            if (atLimit && vRad < 0) {
                next = next.subtract(dirToAnchor.scale(vRad));
            }
            if (beyond) {
                double penetration = dist - rope;
                double correction = Math.min(0.35, 0.15 + penetration * 0.5);
                next = next.add(dirToAnchor.scale(correction));
            }

            // Ensure a minimum inward component each tick, toned down so you can arc out
            vRad = next.dot(dirToAnchor);
            double minRadial = (0.06 + 0.10 * ramp) + 0.08 * distFactor; // softer than before
            if (vRad < minRadial) {
                next = next.add(dirToAnchor.scale(minRadial - vRad));
            }

            // Final clamp
            nextLen = next.length();
            if (nextLen > targetSpeed) next = next.scale(targetSpeed / nextLen);

            player.setDeltaMovement(next);
            player.fallDistance = 0;
        });
    }
}