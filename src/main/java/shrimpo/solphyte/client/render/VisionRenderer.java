package shrimpo.solphyte.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.registry.SolphyteEffect;

import java.util.*;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VisionRenderer {
    private static final List<BlockPos> TARGETS = new ArrayList<>();
    private static final List<AABB> BLOBS = new ArrayList<>();

    // Range (extended) and pulse timings
    private static final int SCAN_RADIUS_XZ = 28; // extended
    private static final int SCAN_RADIUS_Y = 24;  // extended

    private static final int PULSE_VISIBLE_TICKS = 60; // ~3.0s at 20 TPS
    private static final int PULSE_FADE_TICKS = 10;    // ~0.5s fade
    private static final int PULSE_OFF_TICKS = 100;    // ~5.0s off

    private static final int MAX_BLOBS = 400;

    // Player clear radius (skip rendering/targeting within this 3D distance)
    private static final int PLAYER_CLEAR_RADIUS = 6;

    private enum PulseState { OFF, VISIBLE }
    private static PulseState pulseState = PulseState.OFF;
    private static int pulseTicks = 0; // ticks spent in current state

    private static int shakeTicksRemaining = 0;   // camera shake duration
    private static int zoomTicksRemaining = 0;    // FOV zoom duration
    private static int vignetteTicksRemaining = 0; // screen vignette duration
    // Continuous waveform time (for pulsing vignette)
    private static long waveTicks = 0L;

    private static BlockPos lastScanCenter = BlockPos.ZERO;

    private VisionRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Player player = mc.player;

        if (!player.hasEffect(SolphyteEffect.VISION.get())) {
            // Reset everything when the effect is not active
            TARGETS.clear();
            BLOBS.clear();
            pulseState = PulseState.OFF;
            pulseTicks = 0;
            shakeTicksRemaining = 0;
            zoomTicksRemaining = 0;
            vignetteTicksRemaining = 0;
            waveTicks = 0L;
            return;
        }

        // advance oscillator for vignette pulsing when active
        waveTicks++;

        switch (pulseState) {
            case OFF -> {
                if (pulseTicks >= PULSE_OFF_TICKS) {
                    // Start a new scan burst
                    scanForTargets(mc.level, player);
                    buildBlobs(mc.level);
                    pulseState = PulseState.VISIBLE;
                    pulseTicks = 0;
                    // Trigger FX
                    shakeTicksRemaining = 12;
                    zoomTicksRemaining = 10;
                    vignetteTicksRemaining = 14;
                } else {
                    pulseTicks++;
                }
            }
            case VISIBLE -> {
                // after visible+fade, go back to OFF
                if (pulseTicks >= (PULSE_VISIBLE_TICKS + PULSE_FADE_TICKS)) {
                    TARGETS.clear();
                    BLOBS.clear();
                    pulseState = PulseState.OFF;
                    pulseTicks = 0;
                } else {
                    pulseTicks++;
                }
            }
        }

        // Decay momentary FX timers
        if (shakeTicksRemaining > 0) shakeTicksRemaining--;
        if (zoomTicksRemaining > 0) zoomTicksRemaining--;
        if (vignetteTicksRemaining > 0) vignetteTicksRemaining--;
    }

    private static void scanForTargets(Level level, Player player) {
        TARGETS.clear();
        BlockPos center = player.blockPosition();
        lastScanCenter = center;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int minX = center.getX() - SCAN_RADIUS_XZ;
        int maxX = center.getX() + SCAN_RADIUS_XZ;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - SCAN_RADIUS_Y);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + SCAN_RADIUS_Y);
        int minZ = center.getZ() - SCAN_RADIUS_XZ;
        int maxZ = center.getZ() + SCAN_RADIUS_XZ;

        // Player clear radius (3D sphere)
        int px = center.getX();
        int py = center.getY();
        int pz = center.getZ();
        int r2 = PLAYER_CLEAR_RADIUS * PLAYER_CLEAR_RADIUS;

        // Cache, per (x,z), the highest Y that contains a fully light-blocking block (lb >= 15).
        // If none exist, store Integer.MIN_VALUE.
        HashMap<Long, Integer> highestOpaqueY = new HashMap<>(((maxX - minX + 1) * (maxZ - minZ + 1)) * 2);
        int worldTop = level.getMaxBuildHeight() - 1;

        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                long key = encodeXZ(x, z);
                int yFound = Integer.MIN_VALUE;
                // Scan from top down for first fully light-blocking block
                for (int y = worldTop; y >= minY; y--) {
                    BlockPos p = new BlockPos(x, y, z);
                    var st = level.getBlockState(p);
                    if (st.getLightBlock(level, p) >= 15) { yFound = y; break; }
                }
                highestOpaqueY.put(key, yFound);
            }
        }

        final int sideRadius = 3; // 2-3 as requested; using 3 for stronger enclosure requirement

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    // Skip positions too close to the player (3D)
                    int dx = x - px, dy = y - py, dz = z - pz;
                    if (dx * dx + dy * dy + dz * dz <= r2) continue;

                    pos.set(x, y, z);
                    boolean isAir = level.isEmptyBlock(pos);
                    FluidState fs = isAir ? null : level.getFluidState(pos);
                    boolean isWater = fs != null && !fs.isEmpty() && fs.is(FluidTags.WATER);
                    if (!isAir && !isWater) continue; // only consider air or water

                    // Skip skylit columns (transparent to sky through leaves/glass)
                    long key = encodeXZ(x, z);
                    int yOpaque = highestOpaqueY.getOrDefault(key, Integer.MIN_VALUE);
                    if (y >= yOpaque) continue;

                    // Water must be enclosed: no face-adjacent air
                    if (isWater && hasAdjacentAir(level, pos)) continue;

                    // Exposed near-surface? Check side columns within radius; if any see sky at this Y, skip
                    if (isSideExposedToSky(y, highestOpaqueY, x, z, sideRadius)) continue;

                    TARGETS.add(pos.immutable());
                }
            }
        }
    }

    private static long encodeXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static boolean hasAdjacentAir(Level level, BlockPos pos) {
        // 6-neighborhood check
        return level.getBlockState(pos.above()).isAir() ||
               level.getBlockState(pos.below()).isAir() ||
               level.getBlockState(pos.north()).isAir() ||
               level.getBlockState(pos.south()).isAir() ||
               level.getBlockState(pos.west()).isAir() ||
               level.getBlockState(pos.east()).isAir();
    }

    private static boolean isSideExposedToSky(int y, Map<Long, Integer> highestOpaqueY, int x, int z, int radius) {
        for (int d = 1; d <= radius; d++) {
            // +X, -X
            if (y >= highestOpaqueY.getOrDefault(encodeXZ(x + d, z), Integer.MIN_VALUE)) return true;
            if (y >= highestOpaqueY.getOrDefault(encodeXZ(x - d, z), Integer.MIN_VALUE)) return true;
            // +Z, -Z
            if (y >= highestOpaqueY.getOrDefault(encodeXZ(x, z + d), Integer.MIN_VALUE)) return true;
            if (y >= highestOpaqueY.getOrDefault(encodeXZ(x, z - d), Integer.MIN_VALUE)) return true;
        }
        return false;
    }

    private static void buildBlobs(Level level) {
        BLOBS.clear();
        if (TARGETS.isEmpty()) return;
        // Use long-encoded positions for fast membership
        HashSet<Long> air = new HashSet<>(TARGETS.size() * 2);
        for (BlockPos bp : TARGETS) air.add(BlockPos.asLong(bp.getX(), bp.getY(), bp.getZ()));
        HashSet<Long> visited = new HashSet<>(air.size());

        for (BlockPos bp : TARGETS) {
            long key = BlockPos.asLong(bp.getX(), bp.getY(), bp.getZ());
            if (!air.contains(key) || visited.contains(key)) continue;

            int minX = bp.getX(), minY = bp.getY(), minZ = bp.getZ();
            int maxX = minX, maxY = minY, maxZ = minZ;

            // Grow +X on base row
            while (air.contains(BlockPos.asLong(maxX + 1, minY, minZ)) && !visited.contains(BlockPos.asLong(maxX + 1, minY, minZ))) {
                maxX++;
            }
            // Grow +Z using full row in X
            boolean growZ = true;
            while (growZ) {
                int nextZ = maxZ + 1;
                for (int x = minX; x <= maxX; x++) {
                    if (!air.contains(BlockPos.asLong(x, minY, nextZ)) || visited.contains(BlockPos.asLong(x, minY, nextZ))) {
                        growZ = false; break;
                    }
                }
                if (growZ) maxZ = nextZ;
            }
            // Grow +Y stacking full layers of (minX..maxX, minZ..maxZ)
            boolean growY = true;
            while (growY) {
                int nextY = maxY + 1;
                for (int x = minX; x <= maxX && growY; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!air.contains(BlockPos.asLong(x, nextY, z)) || visited.contains(BlockPos.asLong(x, nextY, z))) {
                            growY = false; break;
                        }
                    }
                }
                if (growY) maxY = nextY;
            }

            // Mark visited and create blob AABB
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        visited.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
            BLOBS.add(new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
        }

        // Cull blobs with transparent skylit tops (under leaves/glass/open shafts)
        BLOBS.removeIf(aabb -> isBlobSkylitThroughTransparent(level, aabb));

        // Sort by distance and trim to MAX_BLOBS
        if (BLOBS.size() > MAX_BLOBS) {
            BLOBS.sort((a, b) -> {
                double ax = (a.minX + a.maxX) * 0.5 - lastScanCenter.getX() - 0.5;
                double ay = (a.minY + a.maxY) * 0.5 - lastScanCenter.getY() - 0.5;
                double az = (a.minZ + a.maxZ) * 0.5 - lastScanCenter.getZ() - 0.5;
                double bx = (b.minX + b.maxX) * 0.5 - lastScanCenter.getX() - 0.5;
                double by = (b.minY + b.maxY) * 0.5 - lastScanCenter.getY() - 0.5;
                double bz = (b.minZ + b.maxZ) * 0.5 - lastScanCenter.getZ() - 0.5;
                double da = ax*ax + ay*ay + az*az;
                double db = bx*bx + by*by + bz*bz;
                return Double.compare(da, db);
            });
            while (BLOBS.size() > MAX_BLOBS) BLOBS.remove(BLOBS.size() - 1);
        }
    }

    private static boolean isBlobSkylitThroughTransparent(Level level, AABB aabb) {
        int minX = Mth.floor(aabb.minX);
        int maxX = Mth.floor(aabb.maxX) - 1;
        int minZ = Mth.floor(aabb.minZ);
        int maxZ = Mth.floor(aabb.maxZ) - 1;
        int topY = Mth.floor(aabb.maxY) - 1; // top air layer in blob

        int worldTop = level.getMaxBuildHeight() - 1;
        int stepX = (maxX - minX > 10) ? 2 : 1;
        int stepZ = (maxZ - minZ > 10) ? 2 : 1;

        for (int x = minX; x <= maxX; x += stepX) {
            for (int z = minZ; z <= maxZ; z += stepZ) {
                boolean transparentToSky = true;
                for (int y = topY + 1; y <= worldTop; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var st = level.getBlockState(p);
                    int lb = st.getLightBlock(level, p);
                    if (lb >= 15) { // fully blocks skylight
                        transparentToSky = false;
                        break;
                    }
                }
                if (transparentToSky) {
                    return true; // this column is transparent all the way to sky
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.player.hasEffect(SolphyteEffect.VISION.get())) return;
        if (pulseState != PulseState.VISIBLE || BLOBS.isEmpty()) return;

        float alphaScale = (pulseTicks <= PULSE_VISIBLE_TICKS)
                ? 1.0f
                : Math.max(0f, 1.0f - ((pulseTicks - PULSE_VISIBLE_TICKS) / (float) PULSE_FADE_TICKS));

        PoseStack pose = e.getPoseStack();
        Camera cam = e.getCamera();
        var camPos = cam.getPosition();

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        // Orange-only look (core + glow)
        float orR = 1.00f, orG = 0.52f, orB = 0.05f;

        // Build a per-frame filtered view excluding blobs near the player
        var playerPos = mc.player.position();
        AABB clearBox = new AABB(
                playerPos.x - PLAYER_CLEAR_RADIUS, playerPos.y - PLAYER_CLEAR_RADIUS, playerPos.z - PLAYER_CLEAR_RADIUS,
                playerPos.x + PLAYER_CLEAR_RADIUS, playerPos.y + PLAYER_CLEAR_RADIUS, playerPos.z + PLAYER_CLEAR_RADIUS
        );
        List<AABB> renderBlobs = BLOBS.stream().filter(b -> !b.intersects(clearBox)).toList();
        if (renderBlobs.isEmpty()) {
            // Nothing to draw
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            pose.popPose();
            return;
        }

        // Pass 1: occluded silhouette
        RenderSystem.depthFunc(GL11.GL_GREATER);
        drawFilledListImmediate(pose, renderBlobs, 0.10, orR, orG, orB, 0.24f * alphaScale); // glow
        drawFilledListImmediate(pose, renderBlobs, 0.04, orR, orG, orB, 0.32f * alphaScale); // core

        // Pass 2: normal
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        drawFilledListImmediate(pose, renderBlobs, 0.06, orR, orG, orB, 0.18f * alphaScale);
        drawFilledListImmediate(pose, renderBlobs, 0.02, orR, orG, orB, 0.24f * alphaScale);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void drawFilledListImmediate(PoseStack pose, List<AABB> volumes, double inflate, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var mat = pose.last().pose();
        for (AABB base : volumes) {
            AABB box = base.inflate(inflate);
            float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
            float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;
            // bottom
            bb.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y1, z2).color(r, g, b, a).endVertex();
            // top
            bb.vertex(mat, x1, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z1).color(r, g, b, a).endVertex();
            // north (z1)
            bb.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z1).color(r, g, b, a).endVertex();
            // south (z2)
            bb.vertex(mat, x1, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z2).color(r, g, b, a).endVertex();
            // west (x1)
            bb.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z1).color(r, g, b, a).endVertex();
            // east (x2)
            bb.vertex(mat, x2, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z2).color(r, g, b, a).endVertex();
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
    }

    // --- Camera shake & FOV zoom ---
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.hasEffect(SolphyteEffect.VISION.get())) return;
        if (shakeTicksRemaining <= 0) return;

        // Ease-out over the shake window (initially ~12 ticks)
        final float total = 12f;
        float t = Math.min(shakeTicksRemaining, 12) / total; // 0..1
        float ease = t * t; // quadratic ease-out (since t decreases)

        // Phase using continuous wave time + partials for smoothness
        double phase = (waveTicks + e.getPartialTick()) * 0.45;
        float yawOff = (float) (Math.sin(phase * 7.1) * 0.4f * ease);
        float pitchOff = (float) (Math.cos(phase * 8.3) * 0.3f * ease);
        float rollOff = (float) (Math.sin(phase * 5.5) * 0.15f * ease);

        e.setYaw(e.getYaw() + yawOff);
        e.setPitch(e.getPitch() + pitchOff);
        e.setRoll(e.getRoll() + rollOff);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.hasEffect(SolphyteEffect.VISION.get())) return;
        if (zoomTicksRemaining <= 0) return;

        // Small, quick ease-out FOV dip at pulse start
        final float total = 10f;
        float t = Math.min(zoomTicksRemaining, 10) / total; // 0..1
        float ease = t * t; // quadratic ease-out
        double maxDip = 8.0; // degrees
        e.setFOV(e.getFOV() - (maxDip * ease));
    }

    // --- Orange elliptical vignette synced to pulse ---
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.player.hasEffect(SolphyteEffect.VISION.get())) return;
        // Draw only once per frame on a stable overlay point (crosshair)
        if (e.getOverlay() != null && !e.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;

        // Pulse envelope: only during visible window; fade in then out
        float pulseAlpha = 0f;
        if (pulseState == PulseState.VISIBLE) {
            pulseAlpha = (pulseTicks <= PULSE_VISIBLE_TICKS)
                    ? 1.0f
                    : Math.max(0f, 1.0f - ((pulseTicks - PULSE_VISIBLE_TICKS) / (float) PULSE_FADE_TICKS));
        }
        if (pulseAlpha <= 0f && vignetteTicksRemaining <= 0) return;

        // Base orange (match world highlights)
        float orR = 1.00f, orG = 0.52f, orB = 0.05f;

        // Subtle extra breathing while visible
        float breathe = (float)(0.5 + 0.5 * Math.sin((waveTicks + e.getPartialTick()) * 0.08));
        float alpha = 0.42f * pulseAlpha + 0.06f * breathe;
        // Short fade-in at pulse start (uses the existing vignette timer)
        if (vignetteTicksRemaining > 0) {
            float fadeIn = Mth.clamp(1.0f - (vignetteTicksRemaining / 14.0f), 0f, 1f);
            alpha *= fadeIn;
        }
        alpha = Mth.clamp(alpha, 0f, 0.75f);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Draw a larger, less-elliptical ring to avoid any uncovered corners
        drawEllipticalVignette(e.getGuiGraphics().pose(), sw, sh, orR, orG, orB, alpha, 0.55f, 1.75f, 0.95f);
        // Reset color
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // Renders an elliptical ring gradient covering the screen. Inner radius is transparent, outer is colored.
    private static void drawEllipticalVignette(PoseStack pose, int sw, int sh,
                                               float r, float g, float b, float outerAlpha,
                                               float innerRatio, float outerRatio, float yScale) {
        // Setup state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float cx = sw * 0.5f;
        float cy = sh * 0.5f;
        float rx = cx;           // horizontal radius base
        float ry = cy * yScale;  // vertical radius for ellipse

        // Clamp
        innerRatio = Mth.clamp(innerRatio, 0.0f, 1.0f);
        outerRatio = Math.max(outerRatio, innerRatio + 0.01f);
        outerAlpha = Mth.clamp(outerAlpha, 0f, 1f);

        int segments = Math.max(48, (sw + sh) / 16); // adapt to resolution
        double twoPi = Math.PI * 2.0;

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        var mat = pose.last().pose();

        for (int i = 0; i <= segments; i++) {
            double ang = (i / (double) segments) * twoPi;
            float c = (float)Math.cos(ang);
            float s = (float)Math.sin(ang);

            // Outer edge (colored)
            float xOuter = cx + c * rx * outerRatio;
            float yOuter = cy + s * ry * outerRatio;
            bb.vertex(mat, xOuter, yOuter, 0).color(r, g, b, outerAlpha).endVertex();

            // Inner edge (transparent)
            float xInner = cx + c * rx * innerRatio;
            float yInner = cy + s * ry * innerRatio;
            bb.vertex(mat, xInner, yInner, 0).color(r, g, b, 0f).endVertex();
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
    }
}
