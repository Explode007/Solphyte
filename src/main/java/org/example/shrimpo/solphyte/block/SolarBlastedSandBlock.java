package org.example.shrimpo.solphyte.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.example.shrimpo.solphyte.Solphyte;

public class SolarBlastedSandBlock extends BrushableBlock {
    public SolarBlastedSandBlock(Properties properties) {
        super(
                Blocks.SAND,
                properties,
                SoundEvents.BRUSH_SAND,
                SoundEvents.SAND_BREAK
        );
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BrushableBlockEntity be) {
            if (be.getItem().isEmpty()) {
                be.setLootTable(new ResourceLocation(Solphyte.MODID, "archaeology/solar_blasted_sand"),
                        level.getRandom().nextLong());
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }
}
