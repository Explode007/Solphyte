package shrimpo.solphyte.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.blockentity.MutatedCropBlockEntity;
import shrimpo.solphyte.registry.SolphyteBlock;

import static shrimpo.solphyte.Solphyte.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SolphyteBlockColors {

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null) return GrassColor.getDefaultColor();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MutatedCropBlockEntity mcb)) return -1;

            ResourceLocation id = null;
            if (tintIndex == 0) id = mcb.getPrimaryId();
            else if (tintIndex == 1) id = mcb.getSecondaryId();
            if (id == null) return -1;

            Block block = ForgeRegistries.BLOCKS.getValue(id);
            if (block == null) return -1;

            // Map mutated crop age ratio to target crop age if applicable
            int age = state.hasProperty(BlockStateProperties.AGE_7) ? state.getValue(BlockStateProperties.AGE_7) : 0;
            float f = Math.max(0f, Math.min(1f, age / 7f));

            BlockState target = block.defaultBlockState();
            if (block instanceof CropBlock crop) {
                IntegerProperty ageProp = findAgeProperty(block);
                if (ageProp != null) {
                    int tAge = Math.round(f * crop.getMaxAge());
                    int min = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
                    int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(tAge);
                    int clamped = Math.max(min, Math.min(tAge, max));
                    target = target.setValue(ageProp, clamped);
                }
            }

            // Most blocks use tint index 0 for biome tinting.
            int color = net.minecraft.client.Minecraft.getInstance().getBlockColors().getColor(target, level, pos, 0);
            if (color == -1) return -1;
            return color;
        }, SolphyteBlock.MUTATED_CROP.get());
    }

    private static IntegerProperty findAgeProperty(Block block) {
        BlockState base = block.defaultBlockState();
        if (base.hasProperty(BlockStateProperties.AGE_7)) return BlockStateProperties.AGE_7;
        if (base.hasProperty(BlockStateProperties.AGE_5)) return BlockStateProperties.AGE_5;
        if (base.hasProperty(BlockStateProperties.AGE_3)) return BlockStateProperties.AGE_3;
        return null;
    }
}

