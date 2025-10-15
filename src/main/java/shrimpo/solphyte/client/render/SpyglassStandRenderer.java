package shrimpo.solphyte.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import shrimpo.solphyte.block.SpyglassStandBlock;
import shrimpo.solphyte.blockentity.SpyglassStandBlockEntity;

public class SpyglassStandRenderer implements BlockEntityRenderer<SpyglassStandBlockEntity> {

    // Use the vanilla beacon beam texture for a familiar look
    private static final ResourceLocation BEAM_TEX = new ResourceLocation("minecraft:textures/entity/beacon_beam.png");

    public SpyglassStandRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(SpyglassStandBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;

        BlockState state = level.getBlockState(be.getBlockPos());
        if (!state.hasProperty(SpyglassStandBlock.FACING)) return;
        Direction facing = state.getValue(SpyglassStandBlock.FACING);

        if (!SpyglassStandBlockEntity.isDayFunctional(level)) return;
        if (!SpyglassStandBlockEntity.hasClearStaircaseBehind(level, be.getBlockPos(), facing)) return;
        if (!SpyglassStandBlockEntity.immediateFrontOk(level, be.getBlockPos(), facing)) return;

        // Eased-in activation
        float progress = be.getBeamProgress(partialTick);
        if (progress <= 0.001f) return;
        float eased = easeInOut(progress);

        Vec3 start = SpyglassStandBlockEntity.beamStartLocal();
        Vec3 end = SpyglassStandBlockEntity.beamEndLocal(level, be.getBlockPos(), facing);
        Vec3 dir = end.subtract(start);
        double lengthFull = dir.length();
        if (lengthFull <= 1.0e-3) return;

        // Draw only a portion of the length while forming
        float length = (float) (lengthFull * eased);

        float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
        float pitch = (float) Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));

        // Make the beam slimmer than the telescope; scale widths with eased progress
        float baseHalfStart = 0.03f;
        float baseHalfEnd = 0.02f;
        float halfStart = baseHalfStart * (0.25f + 0.75f * eased);
        float halfEnd = baseHalfEnd * (0.25f + 0.75f * eased);

        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        // Prefer additive blending like a beacon beam
        VertexConsumer vc;
        try {
            vc = buffer.getBuffer(RenderType.beaconBeam(BEAM_TEX, true));
        } catch (Throwable t) {
            // Fallback to translucent if the method signature differs in this MC version
            vc = buffer.getBuffer(RenderType.entityTranslucent(BEAM_TEX));
        }

        int overlay = OverlayTexture.NO_OVERLAY;
        int light = packedLight;
        float time = (level.getGameTime() + partialTick);
        float v0 = (time * 0.02f) % 1.0f;
        float v1 = v0 + Math.max(0.125f, length * 0.25f); // keep some tiling even for short draws

        // Warm yellow/orange; alpha grows with eased progress
        float r = 1.0f, g = 0.85f, b = 0.35f;
        float a = 0.15f + 0.85f * eased; // fade in nicely

        // Draw a rectangular prism (four side faces) along local Z+
        drawBeamPrism(vc, poseStack, length, light, overlay, halfStart, halfEnd, v0, v1, r, g, b, a);

        poseStack.popPose();

        // Emit fiery particles at the end only when beam has fully formed and burn has started
        if (be.isBurningActive()) {
            emitEndParticles(level, be.getBlockPos(), start, end, dir, halfEnd);
        }
    }

    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t); // smoothstep
    }

    private static void drawBeamPrism(VertexConsumer vc, PoseStack pose, float length, int light, int overlay,
                                      float halfStart, float halfEnd, float v0, float v1,
                                      float r, float g, float b, float a) {
        var poseMat = pose.last().pose();
        var normalMat = pose.last().normal();

        float hs = halfStart;
        float he = halfEnd;
        float z0 = 0f, z1 = length;

        // Corners at start (p) and end (q): order p0..p3 CCW starting at (-x,-y)
        float p0x = -hs, p0y = -hs;
        float p1x =  hs, p1y = -hs;
        float p2x =  hs, p2y =  hs;
        float p3x = -hs, p3y =  hs;

        float q0x = -he, q0y = -he;
        float q1x =  he, q1y = -he;
        float q2x =  he, q2y =  he;
        float q3x = -he, q3y =  he;

        float uL = 0f, uR = 1f;

        // +X face
        vc.vertex(poseMat, p1x, p1y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, p2x, p2y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q2x, q2y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q1x, q1y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();

        // -X face
        vc.vertex(poseMat, p3x, p3y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, p0x, p0y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q0x, q0y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q3x, q3y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();

        // +Y face
        vc.vertex(poseMat, p2x, p2y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();
        vc.vertex(poseMat, p3x, p3y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();
        vc.vertex(poseMat, q3x, q3y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();
        vc.vertex(poseMat, q2x, q2y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();

        // -Y face
        vc.vertex(poseMat, p0x, p0y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
        vc.vertex(poseMat, p1x, p1y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
        vc.vertex(poseMat, q1x, q1y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
        vc.vertex(poseMat, q0x, q0y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
    }

    private static void emitEndParticles(Level level, net.minecraft.core.BlockPos pos, Vec3 startLocal, Vec3 endLocal, Vec3 dir, float halfEnd) {
        // Don't emit particles while the game is paused (singleplayer pause keeps render running)
        if (Minecraft.getInstance().isPaused()) return;

        // Compute world-space end position
        Vec3 worldEnd = new Vec3(pos.getX() + endLocal.x, pos.getY() + endLocal.y, pos.getZ() + endLocal.z);
        Vec3 ndir = dir.normalize();

        // Build an orthonormal basis around the beam direction for offsetting particles within the beam cross-section
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = ndir.cross(up);
        if (side.lengthSqr() < 1.0e-6) {
            // Beam nearly vertical; choose X axis as alternate up
            side = ndir.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize();
        Vec3 binormal = ndir.cross(side).normalize();

        RandomSource rand = level.getRandom();
        // Fewer, subtler particles
        int count = 1 + (rand.nextFloat() < 0.35f ? 1 : 0); // 1-2 most frames
        double spread = halfEnd * 0.6;
        for (int i = 0; i < count; i++) {
            double ox = (rand.nextDouble() * 2 - 1) * spread;
            double oy = (rand.nextDouble() * 2 - 1) * spread;
            Vec3 offset = side.scale(ox).add(binormal.scale(oy));

            // Velocity: subtle outward along beam with slight lift
            double speed = 0.01 + rand.nextDouble() * 0.02;
            Vec3 vel = ndir.scale(speed).add(0, 0.01 + rand.nextDouble() * 0.01, 0);

            Vec3 p = worldEnd.add(offset);

            // Mostly small flame; lava pops are rarer to avoid intensity
            if (rand.nextFloat() < 0.85f) {
                level.addParticle(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, vel.x, vel.y, vel.z);
            } else {
                level.addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0.0, 0.01 + rand.nextDouble() * 0.02, 0.0);
            }
        }
    }
}
