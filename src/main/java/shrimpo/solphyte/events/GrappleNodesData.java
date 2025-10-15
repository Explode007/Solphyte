package shrimpo.solphyte.events;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Stores time-limited grapple nodes per-dimension and provides simple spatial queries.
 * Nodes are NOT persisted; they expire automatically and only live in-memory.
 */
public class GrappleNodesData extends SavedData {
    private static final String DATA_NAME = "solphyte_grapple_nodes";
    private static final int LIFETIME_TICKS = 20 * 20; // 20 seconds

    public static int getLifetimeTicks() { return LIFETIME_TICKS; }

    // Map of node position to world-time expiration tick
    private final Map<BlockPos, Long> nodes = new HashMap<>();

    public GrappleNodesData() {}

    public static GrappleNodesData load(CompoundTag tag) {
        // We intentionally do not load any nodes; nodes are ephemeral.
        return new GrappleNodesData();
    }

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag) {
        // Ephemeral: don't persist nodes. Keep tag empty.
        return tag;
    }

    public static GrappleNodesData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GrappleNodesData::load, GrappleNodesData::new, DATA_NAME);
    }

    /**
     * Return a snapshot of active node positions.
     */
    public Set<BlockPos> getNodes() {
        return Collections.unmodifiableSet(new HashSet<>(nodes.keySet()));
    }

    /**
     * Add or refresh a node with a fresh expiry using the provided level time.
     */
    public void addOrRefresh(BlockPos pos, ServerLevel level) {
        if (pos == null || level == null) return;
        long now = level.getGameTime();
        Long prev = nodes.get(pos);
        boolean isNew = (prev == null) || (prev <= now); // newly created or was expired
        long expiry = now + LIFETIME_TICKS;
        nodes.put(pos.immutable(), expiry);
        // No server-side particles here; client handles visuals via S2C packets to reduce duplication.
    }

    /**
     * Add or refresh a node at the given position for the default lifetime, expiry initialized on next tick.
     */
    public void addNode(BlockPos pos) {
        if (pos == null) return;
        nodes.put(pos.immutable(), Long.MIN_VALUE); // placeholder; actual expiry set in tick()
        // Do not mark dirty; we don't want to persist
    }

    /**
     * Find the nearest node center to the given point within maxDistSq. Returns null if none in range.
     */
    public Vec3 nearestNodeCenter(Vec3 point, double maxDistSq) {
        if (nodes.isEmpty() || point == null) return null;
        double best = maxDistSq;
        BlockPos bestPos = null;
        for (BlockPos p : nodes.keySet()) {
            Vec3 c = Vec3.atCenterOf(p);
            double d2 = c.distanceToSqr(point);
            if (d2 <= best) {
                best = d2;
                bestPos = p;
            }
        }
        return bestPos == null ? null : Vec3.atCenterOf(bestPos);
    }

    /**
     * Advance lifetime, initialize expirations based on current time if needed.
     * Server no longer emits particles; client is responsible for visuals.
     */
    public void tick(ServerLevel level) {
        if (nodes.isEmpty()) return;
        long now = level.getGameTime();

        // Initialize any placeholder expirations to now + lifetime
        for (Map.Entry<BlockPos, Long> e : nodes.entrySet()) {
            if (e.getValue() == Long.MIN_VALUE) {
                e.setValue(now + LIFETIME_TICKS);
            }
        }

        // Remove expired nodes
        Iterator<Map.Entry<BlockPos, Long>> it = nodes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> e = it.next();
            long expire = e.getValue();
            if (expire <= now) {
                it.remove();
            }
        }

        // No continuous server-side particle emission to avoid duplicate visuals.
    }
}
