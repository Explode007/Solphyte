package shrimpo.solphyte.events;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.registry.SolphyteItem;

@Mod.EventBusSubscriber(modid = Solphyte.MODID)
public class LichenBrushHandler {

    private static final float SPORE_DROP_CHANCE = 0.30f; // 30% chance

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(Items.BRUSH)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.GLOW_LICHEN)) return;

        // Server-side effect: consume lichen, play dusty sound, and roll spores drop
        if (!level.isClientSide()) {
            // Remove the lichen
            level.removeBlock(pos, false);
            // Dusty sound
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.7f, 1.15f);
            // Chance to drop spores
            if (level.random.nextFloat() < SPORE_DROP_CHANCE) {
                ItemStack drop = new ItemStack(SolphyteItem.LUMINTHAE_SPORES.get());
                ItemEntity ent = new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        drop);
                ent.setDefaultPickUpDelay();
                level.addFreshEntity(ent);
            }
        }

        // Consume the interaction to avoid other handlers; show success on both sides
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }
}

