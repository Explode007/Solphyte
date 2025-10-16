package shrimpo.solphyte.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.client.Keybinds;

/**
 * Renders a simple "lead-like" rope as two crossed quads between the player's hand and the grapple anchor.
 * Color is blue; geometry is lightweight and requires no texture. Replace later with a textured strip if desired.
 */
@Mod.EventBusSubscriber(modid = Solphyte.MODID, value = Dist.CLIENT)
public final class GrappleRopeRenderer {
    private GrappleRopeRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player player = mc.player;
        if (player == null) return;

        // Use client-predicted anchor from Keybinds; if none, don't render
        Vec3 anchor = Keybinds.getClientAnchor();
        if (anchor == null) return;

        // Start point: approximate main-hand near eye position for simplicity
        float pt = e.getPartialTick();
        Vec3 eye = player.getEyePosition(pt);
        Vec3 look = player.getLookAngle();
        // Offset to the side and down a bit to mimic a hand position
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up).normalize();
        Vec3 start = eye.add(right.scale(0.35)).subtract(up.scale(0.25));

        // Segment
        Vec3 to = anchor.subtract(start);
        double len = to.length();
        if (len < 1e-3) return;
        Vec3 dir = to.scale(1.0 / len);

        Camera cam = e.getCamera();
        Vec3 camForward = new Vec3(cam.getLookVector().x, cam.getLookVector().y, cam.getLookVector().z);

        // Build two perpendicular offsets around the segment
        double halfWidth = 0.04; // thickness of rope
        Vec3 perp1 = dir.cross(camForward).normalize();
        if (perp1.lengthSqr() < 1e-6) {
            // Fallback if segment is parallel to camera forward
            perp1 = dir.cross(new Vec3(0, 1, 0)).normalize();
            if (perp1.lengthSqr() < 1e-6) perp1 = new Vec3(1, 0, 0);
        }
        perp1 = perp1.scale(halfWidth);
        Vec3 perp2 = dir.cross(perp1).normalize().scale(halfWidth);

        // Render
        var pose = e.getPoseStack();
        pose.pushPose();
        Vec3 camPos = cam.getPosition();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        // Blue color with slight transparency
        float r = 0.20f, g = 0.60f, b = 1.00f, a = 0.85f;

        drawQuadStrip(pose, start, anchor, perp1, r, g, b, a);
        drawQuadStrip(pose, start, anchor, perp2, r, g, b, a);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void drawQuadStrip(PoseStack pose, Vec3 from, Vec3 to, Vec3 offset, float r, float g, float b, float a) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var mat = pose.last().pose();

        // From quad
        float x1a = (float) (from.x + offset.x);
        float y1a = (float) (from.y + offset.y);
        float z1a = (float) (from.z + offset.z);
        float x1b = (float) (from.x - offset.x);
        float y1b = (float) (from.y - offset.y);
        float z1b = (float) (from.z - offset.z);
        // To quad
        float x2a = (float) (to.x + offset.x);
        float y2a = (float) (to.y + offset.y);
        float z2a = (float) (to.z + offset.z);
        float x2b = (float) (to.x - offset.x);
        float y2b = (float) (to.y - offset.y);
        float z2b = (float) (to.z - offset.z);

        // Quad strip: (1a,1b,2b,2a)
        bb.vertex(mat, x1a, y1a, z1a).color(r, g, b, a).endVertex();
        bb.vertex(mat, x1b, y1b, z1b).color(r, g, b, a).endVertex();
        bb.vertex(mat, x2b, y2b, z2b).color(r, g, b, a).endVertex();
        bb.vertex(mat, x2a, y2a, z2a).color(r, g, b, a).endVertex();

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
    }
}

