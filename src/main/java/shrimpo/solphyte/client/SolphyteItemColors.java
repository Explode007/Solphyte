package shrimpo.solphyte.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.item.PlantSampleItem;
import shrimpo.solphyte.registry.SolphyteItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SolphyteItemColors {

    private static final Map<ResourceLocation, Integer> COLOR_CACHE = new HashMap<>();

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (!stack.is(SolphyteItem.MUTATED_SEEDS.get())) return 0xFFFFFF;

            ResourceLocation p = readId(stack, "Primary");
            ResourceLocation s = readId(stack, "Secondary");

            if (tintIndex == 0) {
                if (p == null) p = ResourceLocation.tryParse("minecraft:wheat");
                return getDominantColorWithFallback(p);
            } else if (tintIndex == 1) {
                if (s == null) s = ResourceLocation.tryParse("minecraft:carrots");
                return getDominantColorWithFallback(s);
            }
            return 0xFFFFFF;
        }, SolphyteItem.MUTATED_SEEDS.get());

        // Plant Sample: single-layer tint derived from stored block id
        event.register((stack, tintIndex) -> {
            if (!stack.is(SolphyteItem.PLANT_SAMPLE.get())) return 0xFFFFFF;
            if (tintIndex != 0) return 0xFFFFFF;
            ResourceLocation id = PlantSampleItem.readBlockId(stack);
            if (id == null) id = ResourceLocation.tryParse("minecraft:wheat");
            return getDominantColorWithFallback(id);
        }, SolphyteItem.PLANT_SAMPLE.get());
    }

    private static ResourceLocation readId(ItemStack stack, String key) {
        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag != null && tag.contains("BlockEntityTag")) {
                var bet = tag.getCompound("BlockEntityTag");
                if (bet.contains(key)) {
                    ResourceLocation id = ResourceLocation.tryParse(bet.getString(key));
                    if (id != null) return id;
                }
            }
            if (tag != null && tag.contains(key)) {
                ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
                if (id != null) return id;
            }
        }
        return null;
    }

    private static int getDominantColorWithFallback(ResourceLocation requested) {
        if (requested == null) return 0xFFFFFF;
        Integer cached = COLOR_CACHE.get(requested);
        if (cached != null) return cached;
        Integer computed = tryComputeDominantColor(requested);
        if (computed == null) computed = 0xFFFFFF;
        COLOR_CACHE.put(requested, computed);
        return computed;
    }

    private static Integer tryComputeDominantColor(ResourceLocation blockId) {
        try {
            // Prefer the crop's produce item sprite (e.g., carrot, beetroot, sugar cane)
            Item produce = resolveProduceItem(blockId);
            if (produce != null) {
                ItemStack is = new ItemStack(produce);
                BakedModel im = Minecraft.getInstance().getItemRenderer().getModel(is, null, null, 0);
                TextureAtlasSprite sprite = im.getParticleIcon();
                if (sprite == null) {
                    RandomSource seeded = RandomSource.create(42L);
                    List<BakedQuad> quads = im.getQuads(null, null, seeded);
                    if (!quads.isEmpty()) sprite = quads.get(0).getSprite();
                }
                if (sprite != null) {
                    int base = dominantSpriteColor(sprite);

                    // If the source block uses a biome tint, apply it to avoid grayscale (e.g., grass, sugar cane)
                    Block blockForTint = ForgeRegistries.BLOCKS.getValue(blockId);
                    if (blockForTint != null) {
                        BlockState tintState;
                        if (blockForTint instanceof CropBlock crop) tintState = setAge(crop, crop.getMaxAge());
                        else tintState = blockForTint.defaultBlockState();
                        // detect any tint index on the model
                        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
                        BakedModel m = brd.getBlockModelShaper().getBlockModel(tintState);
                        RandomSource seeded2 = RandomSource.create(42L);
                        Integer tintIdx = null;
                        for (Direction d : new Direction[]{null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN}) {
                            List<BakedQuad> qs = (d == null) ? m.getQuads(tintState, null, seeded2) : m.getQuads(tintState, d, seeded2);
                            for (BakedQuad q : qs) {
                                if (q.getTintIndex() >= 0) { tintIdx = q.getTintIndex(); break; }
                            }
                            if (tintIdx != null) break;
                        }
                        if (tintIdx != null) {
                            int tint = Minecraft.getInstance().getBlockColors().getColor(tintState, null, null, tintIdx);
                            if (tint == -1) tint = GrassColor.getDefaultColor();
                            int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
                            int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
                            base = ((br * tr) / 255 << 16) | ((bg * tg) / 255 << 8) | ((bb * tb) / 255);
                        }
                    }
                    return brightenColor(base, 0.4f);
                }
            }

            // Fallback to block's model (mature state for crops)
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block == null) return null;

            BlockState state;
            if (block instanceof CropBlock crop) {
                state = setAge(crop, crop.getMaxAge());
            } else {
                state = block.defaultBlockState();
            }

            BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();
            BakedModel model = brd.getBlockModelShaper().getBlockModel(state);

            RandomSource seeded = RandomSource.create(42L);
            List<BakedQuad> allQuads = new ArrayList<>();
            allQuads.addAll(model.getQuads(state, null, seeded));
            for (Direction d : Direction.values()) {
                allQuads.addAll(model.getQuads(state, d, seeded));
            }

            TextureAtlasSprite sprite = null;
            Integer tintIdx = null;
            for (BakedQuad q : allQuads) {
                if (sprite == null && q.getSprite() != null) sprite = q.getSprite();
                if (tintIdx == null && q.getTintIndex() >= 0) tintIdx = q.getTintIndex();
                if (sprite != null && tintIdx != null) break;
            }
            if (sprite == null) sprite = model.getParticleIcon();
            if (sprite == null) return null;

            int base = dominantSpriteColor(sprite);

            // Apply default block tint if any quad is tinted (handles biome-tinted blocks like sugar cane)
            if (tintIdx != null) {
                int tint = Minecraft.getInstance().getBlockColors().getColor(state, null, null, tintIdx);
                if (tint == -1) tint = GrassColor.getDefaultColor();
                int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
                int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
                base = ((br * tr) / 255 << 16) | ((bg * tg) / 255 << 8) | ((bb * tb) / 255);
            }
            return brightenColor(base, 0.4f);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int brightenColor(int rgb, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = r + Math.round((255 - r) * amount);
        g = g + Math.round((255 - g) * amount);
        b = b + Math.round((255 - b) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private static Item resolveProduceItem(ResourceLocation blockId) {
        if (blockId == null) return null;
        Block b = ForgeRegistries.BLOCKS.getValue(blockId);
        if (b == null) return null;
        // Mirror the logic used for block drops
        if (b == Blocks.CARROTS) return net.minecraft.world.item.Items.CARROT;
        if (b == Blocks.POTATOES) return net.minecraft.world.item.Items.POTATO;
        if (b == Blocks.BEETROOTS) return net.minecraft.world.item.Items.BEETROOT;
        if (b == Blocks.WHEAT) return net.minecraft.world.item.Items.WHEAT;
        // Common plants
        if (b == Blocks.SUGAR_CANE) return net.minecraft.world.item.Items.SUGAR_CANE;
        if (b == Blocks.CACTUS) return net.minecraft.world.item.Items.CACTUS;
        Item asItem = b.asItem();
        return asItem != null ? asItem : net.minecraft.world.item.Items.WHEAT;
    }

    private static BlockState setAge(CropBlock block, int age) {
        BlockState state = block.defaultBlockState();
        IntegerProperty ageProp = findAgeProperty(block);
        if (ageProp != null) {
            int min = ageProp.getPossibleValues().stream().min(Integer::compareTo).orElse(0);
            int max = ageProp.getPossibleValues().stream().max(Integer::compareTo).orElse(age);
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

    private static int dominantSpriteColor(TextureAtlasSprite sprite) {
        int w = sprite.contents().width();
        int h = sprite.contents().height();
        int stepX = Math.max(1, w / 16);
        int stepY = Math.max(1, h / 16);
        Map<Integer, Integer> counts = new HashMap<>();
        int bestColor = 0xFFFFFF;
        int bestCount = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int abgr = sprite.getPixelRGBA(0, x, y);
                int a = (abgr >>> 24) & 0xFF;
                if (a < 16) continue; // ignore nearly transparent pixels
                // TextureAtlasSprite pixels are ABGR; remap to RGB
                int b = (abgr >>> 16) & 0xFF;
                int g = (abgr >>> 8) & 0xFF;
                int r = (abgr) & 0xFF;
                int rgb = (r << 16) | (g << 8) | b;
                int c = counts.getOrDefault(rgb, 0) + 1;
                counts.put(rgb, c);
                if (c > bestCount) {
                    bestCount = c;
                    bestColor = rgb;
                }
            }
        }
        return bestColor;
    }
}
