package shrimpo.solphyte.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, value = Dist.CLIENT)
public class GrappleNodeClient {
    private static final Map<BlockPos, Long> nodes = new HashMap<>();

    public static void addOrRefresh(BlockPos pos, int lifetimeTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        nodes.put(pos.immutable(), now + lifetimeTicks);
        // Removed particle creation burst in favor of static cube rendering
    }

    public static Set<BlockPos> getNodesSnapshot() {
        return new HashSet<>(nodes.keySet());
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
    }
}
