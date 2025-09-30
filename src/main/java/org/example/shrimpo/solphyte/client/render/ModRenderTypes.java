//package org.example.shrimpo.solphyte.client.render;
//
//import com.mojang.blaze3d.platform.GlStateManager;
//import com.mojang.blaze3d.systems.RenderSystem;
//import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//import com.mojang.blaze3d.vertex.VertexFormat;
//import net.minecraft.client.renderer.RenderStateShard;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.client.renderer.ShaderInstance;
//import net.minecraft.resources.ResourceLocation;
//
//public class ModRenderTypes {
//    public static ShaderInstance BEAM_SHADER;
//
//    public static RenderType beam() {
//        if (BEAM_SHADER == null) {
//            // Fallback to a white texture so vertex color controls the beam color
//            return RenderType.entityTranslucent(new ResourceLocation("minecraft", "textures/misc/white.png"));
//        }
//
//        RenderStateShard.TextureStateShard textureState = new RenderStateShard.TextureStateShard(new ResourceLocation("minecraft", "textures/misc/white.png"), false, false);
//
//        // Additive-style blending for glow: SRC_ALPHA, ONE
//        RenderStateShard.TransparencyStateShard additive = new RenderStateShard.TransparencyStateShard(
//                "solphyte_additive",
//                () -> {
//                    RenderSystem.enableBlend();
//                    RenderSystem.blendFuncSeparate(
//                            GlStateManager.SourceFactor.SRC_ALPHA,
//                            GlStateManager.DestFactor.ONE,
//                            GlStateManager.SourceFactor.ONE,
//                            GlStateManager.DestFactor.ONE);
//                },
//                () -> {
//                    RenderSystem.disableBlend();
//                    RenderSystem.defaultBlendFunc();
//                }
//        );
//
//        RenderStateShard.CullStateShard noCulling = new RenderStateShard.CullStateShard(false);
//        // Disable depth writes to avoid z-fighting between overlapping planes/halos
//        RenderStateShard.WriteMaskStateShard writeMask = new RenderStateShard.WriteMaskStateShard(true, false);
//
//        RenderType.CompositeState state = RenderType.CompositeState.builder()
//                .setShaderState(new RenderStateShard.ShaderStateShard(() -> BEAM_SHADER))
//                .setTextureState(textureState)
//                .setTransparencyState(additive)
//                .setCullState(noCulling)
//                .setLightmapState(new RenderStateShard.LightmapStateShard(true))
//                .setOverlayState(new RenderStateShard.OverlayStateShard(true))
//                .setWriteMaskState(writeMask)
//                .createCompositeState(true);
//
//        return RenderType.create("solphyte_beam", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, state);
//    }
//}
