package org.example.shrimpo.solphyte.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.example.shrimpo.solphyte.Solphyte;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, value = Dist.CLIENT)
public class GrappleNodeClient {
    private static final Map<BlockPos, Long> nodes = new HashMap<>();

    public static void addOrRefresh(BlockPos pos, int lifetimeTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        nodes.put(pos.immutable(), now + lifetimeTicks);
        // Lighter creation burst client-side only
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        DustParticleOptions red = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.0f, 0.0f), 1.0f);
        int seg = 8;
        double r = 0.55;
        for (int i = 0; i < seg; i++) {
            double ang = i * (Math.PI * 2 / seg);
            double px = cx + Math.cos(ang) * r;
            double pz = cz + Math.sin(ang) * r;
            mc.level.addParticle(red, px, cy, pz, 0, 0, 0);
        }
        // small core
        mc.level.addParticle(red, cx, cy, cz, 0, 0, 0);
    }

    /**
     * Return the center position of the nearest node hit by a ray within hitRadius, or null if none.
     * eye: start of ray; dir: normalized direction; maxDist: maximum ray length in meters.
     */
    public static Vec3 rayHitNode(Vec3 eye, Vec3 dir, double maxDist, double hitRadius) {
        if (nodes.isEmpty() || eye == null || dir == null || maxDist <= 0) return null;
        double len = maxDist;
        double bestT = Double.POSITIVE_INFINITY;
        Vec3 best = null;
        double r = Math.max(0.1, hitRadius);
        double r2 = r * r;
        for (BlockPos p : nodes.keySet()) {
            Vec3 c = Vec3.atCenterOf(p);
            Vec3 ec = c.subtract(eye);
            double t = ec.dot(dir);
            if (t < 0 || t > len) continue;
            Vec3 closest = eye.add(dir.scale(t));
            double dsq = c.subtract(closest).lengthSqr();
            if (dsq <= r2) {
                if (t < bestT) { bestT = t; best = c; }
            }
        }
        return best;
    }

    /**
     * Find the nearest node center within a given radius from a point; returns null if none.
     */
    public static Vec3 nearestNode(Vec3 point, double radius) {
        if (nodes.isEmpty() || point == null || radius <= 0) return null;
        double best = radius * radius;
        BlockPos bestPos = null;
        for (BlockPos p : nodes.keySet()) {
            Vec3 c = Vec3.atCenterOf(p);
            double d2 = c.distanceToSqr(point);
            if (d2 <= best) { best = d2; bestPos = p; }
        }
        return bestPos == null ? null : Vec3.atCenterOf(bestPos);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (nodes.isEmpty()) return;

        long now = mc.level.getGameTime();
        // Purge expired
        nodes.entrySet().removeIf(e -> e.getValue() <= now);
        if (nodes.isEmpty()) return;

        // Throttle continuous visuals to every other tick to reduce particle load
        if ((now & 1L) == 1L) return;

        // Simple red dust ring around each node
        double phase = (now % 40) / 40.0 * (Math.PI * 2);
        int ringCount = 8;
        double ringRadius = 0.50;
        DustParticleOptions red = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.0f, 0.0f), 1.0f);

        for (BlockPos pos : nodes.keySet()) {
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            for (int i = 0; i < ringCount; i++) {
                double ang = phase + (i * (Math.PI * 2 / ringCount));
                double px = cx + Math.cos(ang) * ringRadius;
                double pz = cz + Math.sin(ang) * ringRadius;
                mc.level.addParticle(red, px, cy, pz, 0, 0, 0);
            }
            // occasional core sparkle
            if ((now % 10) == 0) mc.level.addParticle(red, cx, cy, cz, 0, 0, 0);
        }
    }
}
