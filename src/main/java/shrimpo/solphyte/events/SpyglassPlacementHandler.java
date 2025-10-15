package shrimpo.solphyte.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.block.SpyglassStandBlock;
import shrimpo.solphyte.registry.SolphyteBlock;

@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SpyglassPlacementHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Only main hand to avoid double trigger
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        var player = event.getEntity();
        Level level = event.getLevel();
        Direction face = event.getFace();
        if (player == null || face == null) return;

        // Must be sneaking and clicking top face
        if (!player.isShiftKeyDown() || face != Direction.UP) return;

        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.SPYGLASS)) return;

        BlockPos clicked = event.getPos();
        BlockPos placePos = clicked.above();

        // Ensure the space is replaceable and entity-free
        BlockState existing = level.getBlockState(placePos);
        if (!existing.canBeReplaced()) return;
        if (!level.noCollision(new AABB(placePos))) return;

        Direction dir = player.getDirection();
        BlockState toPlace = SolphyteBlock.SPYGLASS_STAND.get().defaultBlockState().setValue(SpyglassStandBlock.FACING, dir);

        // Only on top of sturdy face and survivable
        if (!toPlace.canSurvive(level, placePos)) return;

        // Server-side placement, client gets success for hand animation
        if (!level.isClientSide) {
            level.setBlock(placePos, toPlace, 3);
            SoundType soundType = toPlace.getSoundType();
            level.playSound(null, placePos, soundType.getPlaceSound(), SoundSource.BLOCKS, (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }
}
