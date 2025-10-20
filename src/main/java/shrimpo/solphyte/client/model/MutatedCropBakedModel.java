package shrimpo.solphyte.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.client.extensions.IForgeBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.modeldata.MutatedCropModelData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MutatedCropBakedModel implements BakedModel, IForgeBakedModel {

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData, RenderType renderType) {
        if (side != null) return Collections.emptyList();
        ResourceLocation pId = extraData.get(MutatedCropModelData.PRIMARY);
        ResourceLocation sId = extraData.get(MutatedCropModelData.SECONDARY);

        Block pBlock = resolveBlock(pId);
        Block sBlock = resolveBlock(sId);
        if (pBlock == null) pBlock = net.minecraft.world.level.block.Blocks.WHEAT;
        if (sBlock == null) sBlock = net.minecraft.world.level.block.Blocks.CARROTS;

        int age = 0;
        if (state != null && state.hasProperty(BlockStateProperties.AGE_7)) {
            age = state.getValue(BlockStateProperties.AGE_7);
        }
        float f = Math.max(0f, Math.min(1f, age / 7f));

        BlockState pState = (pBlock instanceof CropBlock pc) ? setAge(pc, Math.round(f * pc.getMaxAge())) : pBlock.defaultBlockState();
        BlockState sState = (sBlock instanceof CropBlock sc) ? setAge(sc, Math.round(f * sc.getMaxAge())) : sBlock.defaultBlockState();

        boolean pIsCrop = pBlock instanceof CropBlock;
        boolean sIsCrop = sBlock instanceof CropBlock;

        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
        BakedModel pModel = brd.getBlockModelShaper().getBlockModel(pState);
        BakedModel sModel = brd.getBlockModelShaper().getBlockModel(sState);

        RandomSource seeded = RandomSource.create(42L);
        List<BakedQuad> pAll = new ArrayList<>();
        pAll.addAll(pModel.getQuads(pState, null, seeded));
        for (Direction d : Direction.values()) pAll.addAll(pModel.getQuads(pState, d, seeded));
        List<BakedQuad> sAll = new ArrayList<>();
        sAll.addAll(sModel.getQuads(sState, null, seeded));
        for (Direction d : Direction.values()) sAll.addAll(sModel.getQuads(sState, d, seeded));

        // Crop halves for crop-type only
        List<BakedQuad> pHalf = new ArrayList<>();
        if (pIsCrop) {
            for (BakedQuad q : pAll) {
                Direction d = q.getDirection();
                if (d == Direction.NORTH || d == Direction.SOUTH) pHalf.add(setTintIndex(q, 0));
            }
        }
        List<BakedQuad> sHalf = new ArrayList<>();
        if (sIsCrop) {
            for (BakedQuad q : sAll) {
                Direction d = q.getDirection();
                if (d == Direction.EAST || d == Direction.WEST) sHalf.add(setTintIndex(q, 1));
            }
        }

        List<BakedQuad> out = new ArrayList<>(8);

        // New behavior: if exactly one ingredient is a crop, render that crop fully (all quads)
        // and place the non-crop as mini instances. This replaces the "two sides only" behavior.
        if (pIsCrop ^ sIsCrop) {
            if (pIsCrop) {
                // Full primary crop, tinted as primary (0)
                for (BakedQuad q : pAll) out.add(setTintIndex(q, 0));
                // Mini secondary block around
                addDirectionalInstances(sAll, out, 1, true, 0.25f, f);
            } else {
                // Full secondary crop, tinted as secondary (1)
                for (BakedQuad q : sAll) out.add(setTintIndex(q, 1));
                // Mini primary block around
                addDirectionalInstances(pAll, out, 0, true, 0.25f, f);
            }
            return out;
        }

        // Default behavior for crop+crop and block+block
        if (pIsCrop) {
            out.addAll(pHalf);
        } else {
            // If primary is non-crop and secondary is crop => prefer NESW for primary, else keep diagonals for primary
            boolean useCardinals = sIsCrop;
            addDirectionalInstances(pAll, out, 0, useCardinals, 0.25f, f); // primary minis
        }
        if (sIsCrop) {
            out.addAll(sHalf);
        } else {
            // Secondary is non-crop: always cardinals (NESW) to stay clean; also satisfies block+crop case
            boolean useCardinals = true;
            addDirectionalInstances(sAll, out, 1, useCardinals, 0.25f, f); // secondary minis
        }
        return out;
    }

    private static void addDirectionalInstances(List<BakedQuad> source, List<BakedQuad> out, int tintIndex, boolean useCardinals, float radius, float growthF) {
        if (source.isEmpty()) return;
        float sx = 0.22f, sz = 0.22f; // was 0.30f
        float minSy = 0.12f, maxSy = 0.50f; // cap at ~half height, was 0.18..0.95
        float[][] cardinals = new float[][] { {0f, -radius}, {+radius, 0f}, {0f, +radius}, {-radius, 0f} }; // N,E,S,W
        float[][] diagonals = new float[][] { {-radius, -radius}, {+radius, -radius}, {+radius, +radius}, {-radius, +radius} }; // NW,NE,SE,SW
        float[][] order = useCardinals ? cardinals : diagonals;
        float stage = growthF * 4f; // 0..4
        for (int i = 0; i < 4; i++) {
            float prog = Math.max(0f, Math.min(1f, stage - i));
            float sy = minSy + prog * (maxSy - minSy);
            float ox = order[i][0];
            float oz = order[i][1];
            float oy = 0.0f;
            for (BakedQuad q : source) {
                out.add(transformQuadNonUniform(q, sx, sy, sz, ox, oy, oz, tintIndex));
            }
        }
    }

    private static BakedQuad setTintIndex(BakedQuad quad, int tintIndex) {
        int[] data = quad.getVertices().clone();
        return new BakedQuad(data, tintIndex, quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    private static BakedQuad transformQuadNonUniform(BakedQuad quad, float sx, float sy, float sz, float ox, float oy, float oz, int tintIndex) {
        int[] data = quad.getVertices().clone();
        int stride = data.length / 4;
        for (int i = 0; i < 4; i++) {
            int ix = i * stride;
            float x = Float.intBitsToFloat(data[ix]);
            float y = Float.intBitsToFloat(data[ix + 1]);
            float z = Float.intBitsToFloat(data[ix + 2]);
            float nx = (x - 0.5f) * sx + 0.5f + ox;
            float ny = y * sy + oy; // ground-anchored Y
            float nz = (z - 0.5f) * sz + 0.5f + oz;
            data[ix] = Float.floatToRawIntBits(nx);
            data[ix + 1] = Float.floatToRawIntBits(ny);
            data[ix + 2] = Float.floatToRawIntBits(nz);
        }
        return new BakedQuad(data, tintIndex, quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    private static Block resolveBlock(ResourceLocation id) {
        if (id == null) return null;
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    private static BlockState setAge(CropBlock block, int age) {
        BlockState state = block.defaultBlockState();
        IntegerProperty ageProp = findAgeProperty(block);
        if (ageProp != null) {
            int min = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
            int clamped = Math.max(min, Math.min(age, max));
            state = state.setValue(ageProp, clamped);
        }
        return state;
    }

    private static IntegerProperty findAgeProperty(Block block) {
        if (block.defaultBlockState().hasProperty(BlockStateProperties.AGE_7)) return BlockStateProperties.AGE_7;
        if (block.defaultBlockState().hasProperty(BlockStateProperties.AGE_5)) return BlockStateProperties.AGE_5;
        if (block.defaultBlockState().hasProperty(BlockStateProperties.AGE_3)) return BlockStateProperties.AGE_3;
        return null;
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() {
        BlockState pState = net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState();
        BakedModel pModel = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getBlockModel(pState);
        return pModel.getParticleIcon();
    }
    @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
