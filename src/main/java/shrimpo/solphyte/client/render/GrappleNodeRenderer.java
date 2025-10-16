package shrimpo.solphyte.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.client.GrappleNodeClient;

import java.util.Set;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GrappleNodeRenderer {
    private GrappleNodeRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Set<BlockPos> nodes = GrappleNodeClient.getNodesSnapshot();
        if (nodes.isEmpty()) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var mat = pose.last().pose();

        // Small cube size
        float half = 0.18f; // slightly larger cube (~0.36m)
        // Color: #AEA792
        float r = 174f/255f, g = 167f/255f, b = 146f/255f, a = 0.95f;

        for (BlockPos p : nodes) {
            float cx = p.getX() + 0.5f;
            float cy = p.getY() + 0.5f;
            float cz = p.getZ() + 0.5f;
            float x1 = cx - half, x2 = cx + half;
            float y1 = cy - half, y2 = cy + half;
            float z1 = cz - half, z2 = cz + half;
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
            // north
            bb.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z1).color(r, g, b, a).endVertex();
            // south
            bb.vertex(mat, x1, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z2).color(r, g, b, a).endVertex();
            // west
            bb.vertex(mat, x1, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y1, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x1, y2, z1).color(r, g, b, a).endVertex();
            // east
            bb.vertex(mat, x2, y1, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z1).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y2, z2).color(r, g, b, a).endVertex();
            bb.vertex(mat, x2, y1, z2).color(r, g, b, a).endVertex();
        }
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }
}
