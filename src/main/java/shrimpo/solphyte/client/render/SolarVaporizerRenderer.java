package shrimpo.solphyte.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.item.SolarVaporizerItem;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class SolarVaporizerRenderer {
    private static final ResourceLocation BEAM_TEX = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player player = mc.player;
        if (player == null) return;

        if (!player.isUsingItem()) return;
        InteractionHand usedHand = player.getUsedItemHand();
        if (usedHand == null) return;
        ItemStack using = usedHand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
        if (using.isEmpty() || !(using.getItem() instanceof SolarVaporizerItem)) return;

        int fuel = using.getOrCreateTag().getInt(SolarVaporizerItem.NBT_FUEL);
        if (fuel <= 0) return;

        boolean offhand = usedHand == InteractionHand.OFF_HAND;

        // Instant full intensity while using
        float eased = 1.0f;

        // Raycast for block target on client
        BlockHitResult hit = raycast(player, mc.level, 32.0);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        Vec3 start = handMuzzle(player, e.getPartialTick(), offhand);
        Vec3 end = Vec3.atCenterOf(hit.getBlockPos());
        Vec3 dir = end.subtract(start);
        double dist = dir.length();
        if (dist < 1.0e-3) return;

        float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
        float pitch = (float) Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));

        // Thicker beam: core + glow
        float coreHalfStart = 0.055f * (0.25f + 0.75f * eased);
        float coreHalfEnd   = 0.040f * (0.25f + 0.75f * eased);
        float glowHalfStart = coreHalfStart * 1.7f;
        float glowHalfEnd   = coreHalfEnd * 1.7f;

        PoseStack pose = e.getPoseStack();
        var cam = e.getCamera();
        var camPos = cam.getPosition();

        pose.pushPose();
        // Move into world space relative to camera
        pose.translate(-camPos.x, -camPos.y, -camPos.z);
        pose.translate(start.x, start.y, start.z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-pitch));

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc;
        try {
            vc = buffers.getBuffer(RenderType.beaconBeam(BEAM_TEX, true));
        } catch (Throwable t) {
            vc = buffers.getBuffer(RenderType.entityTranslucent(BEAM_TEX));
        }

        int overlay = OverlayTexture.NO_OVERLAY;
        int light = 0x00F000F0; // full-bright like beacon
        float time = (mc.level.getGameTime() + e.getPartialTick());
        float v0 = (time * 0.02f) % 1.0f;
        float v1 = v0 + Math.max(0.125f, (float) dist * 0.25f);

        // Core: bright, near-white
        float cr = 1.0f, cg = 0.97f, cb = 0.85f;
        float ca = 1.0f; // fully bright while using
        drawBeamPrism(vc, pose, (float) dist, light, overlay, coreHalfStart, coreHalfEnd, v0, v1, cr, cg, cb, ca);

        // Glow: warmer, larger, more transparent
        float gr = 1.0f, gg = 0.88f, gb = 0.35f;
        float ga = 0.6f;
        drawBeamPrism(vc, pose, (float) dist, light, overlay, glowHalfStart, glowHalfEnd, v0, v1, gr, gg, gb, ga);

        buffers.endBatch();
        pose.popPose();
    }

    private static BlockHitResult raycast(Player player, net.minecraft.world.level.Level level, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        ClipContext ctx = new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player);
        HitResult hr = level.clip(ctx);
        if (hr instanceof BlockHitResult bhr) return bhr;
        return null;
    }

    private static Vec3 handMuzzle(Player player, float partialTick, boolean offhand) {
        // Base from eye
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up).normalize();
        // Further forward and slightly down for better FPV alignment
        double forward = 0.28;   // forward/back along view
        double baseSide = 0.24;  // horizontal offset magnitude; increase to move further right (for right hand)
        double mainSign = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
        if (offhand) mainSign = -mainSign; // flip to the other side when held in offhand
        double side = mainSign * baseSide;
        double drop = 0.08;      // vertical offset (down)
        return eye.add(look.scale(forward)).add(right.scale(side)).add(0, -drop, 0);
    }

    private static void drawBeamPrism(VertexConsumer vc, PoseStack pose, float length, int light, int overlay,
                                      float halfStart, float halfEnd, float v0, float v1,
                                      float r, float g, float b, float a) {
        var poseMat = pose.last().pose();
        var normalMat = pose.last().normal();

        float z0 = 0f, z1 = length;

        float p0x = -halfStart, p0y = -halfStart;
        float p1x =  halfStart, p1y = -halfStart;
        float p2x =  halfStart, p2y =  halfStart;
        float p3x = -halfStart, p3y =  halfStart;

        float q0x = -halfEnd, q0y = -halfEnd;
        float q1x =  halfEnd, q1y = -halfEnd;
        float q2x =  halfEnd, q2y =  halfEnd;
        float q3x = -halfEnd, q3y =  halfEnd;

        float uL = 0f, uR = 1f;

        // +X
        vc.vertex(poseMat, p1x, p1y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, p2x, p2y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q2x, q2y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q1x, q1y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 1f, 0f, 0f).endVertex();

        // -X
        vc.vertex(poseMat, p3x, p3y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, p0x, p0y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q0x, q0y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();
        vc.vertex(poseMat, q3x, q3y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, -1f, 0f, 0f).endVertex();

        // +Y
        vc.vertex(poseMat, p2x, p2y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();
        vc.vertex(poseMat, p3x, p3y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();
        vc.vertex(poseMat, q3x, q3y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();
        vc.vertex(poseMat, q2x, q2y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, 1f, 0f).endVertex();

        // -Y
        vc.vertex(poseMat, p0x, p0y, z0).color(r, g, b, a).uv(uL, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
        vc.vertex(poseMat, p1x, p1y, z0).color(r, g, b, a).uv(uR, v0).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
        vc.vertex(poseMat, q1x, q1y, z1).color(r, g, b, a).uv(uR, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
        vc.vertex(poseMat, q0x, q0y, z1).color(r, g, b, a).uv(uL, v1).overlayCoords(overlay).uv2(light).normal(normalMat, 0f, -1f, 0f).endVertex();
    }
}
