package shrimpo.solphyte.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import shrimpo.solphyte.registry.SolphyteBlock;
import shrimpo.solphyte.block.LuminthaeHyphaeBlock;

public class LuminthaeSporesItem extends Item {
    public LuminthaeSporesItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState target = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // Only convert dirt-like blocks
        if (!isConvertable(target)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            Block block = SolphyteBlock.LUMINTHAE_HYPHAE.get();
            // set the block to luminthae stage 1
            level.setBlock(pos, block.defaultBlockState().setValue(LuminthaeHyphaeBlock.STAGE, 0), 3);

            // Sound & particles
            level.playSound(null, pos, SoundEvents.GRASS_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.01);
            }

            if (player != null && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean isConvertable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT);
    }
}
