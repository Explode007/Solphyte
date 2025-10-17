package shrimpo.solphyte.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import shrimpo.solphyte.registry.SolphyteItem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SolarVaporizerItem extends Item {
    public static final String NBT_FUEL = "Fuel";
    private static final int FUEL_PER_CELL = 200; // ticks of continuous beam per helio cell (~10s)
    private static final int FUEL_PER_TICK = 1;   // consumption while firing
    private static final double MAX_RANGE = 32.0; // block reach for beam

    // Per-player mining state while holding beam
    private static final Map<UUID, MiningState> MINING = new HashMap<>();

    public SolarVaporizerItem(Properties props) {
        super(props);
    }

    // Display using like a bow (continuous)
    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE; // static, no bow draw animation
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Sneak-use: load one Helio Cell as fuel
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int loaded = tryLoadFuel(player, stack);
                if (loaded > 0) {
                    level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.6f, 1.3f);
                } else {
                    level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, 0.7f);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        // Start firing if has fuel
        if (getFuel(stack) <= 0) {
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.8f);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return; // logic server-side only

        // Stop if fuel empty
        if (getFuel(stack) <= 0) {
            clearMining(level, player);
            player.stopUsingItem();
            return;
        }

        // Consume fuel
        addFuel(stack, -FUEL_PER_TICK);

        // Ray trace to find targeted block
        BlockHitResult hit = raycast(player, level, MAX_RANGE);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            clearMining(level, player);
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (!level.isLoaded(pos)) {
            clearMining(level, player);
            return;
        }
        if (!level.mayInteract(player, pos)) {
            clearMining(level, player);
            return;
        }

        BlockState state = level.getBlockState(pos);
        // Skip unbreakable
        if (state.getDestroySpeed(level, pos) < 0) {
            clearMining(level, player);
            return;
        }

        MiningState ms = MINING.computeIfAbsent(player.getUUID(), id -> new MiningState());
        if (!pos.equals(ms.pos)) {
            // Switched target: clear previous crack stage
            if (ms.pos != null) {
                level.destroyBlockProgress(player.getId(), ms.pos, -1);
            }
            ms.reset(pos);
        }

        // Compute per-tick progress scaled roughly like a diamond pickaxe
        float progressDelta = computeProgressDelta(player, state, pos);
        ms.progress = Math.min(1f, ms.progress + progressDelta);
        int stage = (int) (ms.progress * 10f) - 1; // 0..9
        if (stage < 0) stage = 0;
        if (stage > 9) stage = 9;
        level.destroyBlockProgress(player.getId(), pos, stage);

        if (ms.progress >= 0.999f) {
            // Break with custom rules
            breakWithRules(level, player, pos, state);
            // Clear crack and reset
            level.destroyBlockProgress(player.getId(), pos, -1);
            ms.reset(null);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (!level.isClientSide) {
            clearMining(level, player);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, java.util.List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        int fuel = getFuel(stack);
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.solphyte.solar_vaporizer.fuel", fuel));
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.solphyte.solar_vaporizer.hint"));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // Suppress re-equip animation when only NBT (e.g., Fuel) changes while held/using
        if (slotChanged) return true;
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        // Keep mining progress intact across NBT updates while using
        return false;
    }

    private static void breakWithRules(Level level, Player player, BlockPos pos, BlockState state) {
        // Vaporize: logs/leaves/dirt variants/sand variants/water -> set to air, no drops
        boolean isLog = state.is(BlockTags.LOGS);
        boolean isLeaves = state.is(BlockTags.LEAVES);
        boolean isDirt = state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.GRASS_BLOCK);
        boolean isSand = state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
        boolean isWater = state.getFluidState().is(Fluids.WATER);

        if (isLog || isLeaves || isDirt || isSand || isWater) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.3f);
            return;
        }

        // Smelt: cobblestone -> stone; stone stays stone (drops stone)
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE)) {
            // Replace with air and drop stone item
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack drop = new ItemStack(Items.STONE);
            Block.popResource(level, pos, drop);
            level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.4f, 1.2f);
            return;
        }

        // Default: normal drop
        level.destroyBlock(pos, true, player);
    }

    private static float computeProgressDelta(Player player, BlockState state, BlockPos pos) {
        float hardness = Math.max(0.001f, state.getDestroySpeed(player.level(), pos));
        // Choose a base tool speed depending on tool type needed
        float speed;
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) speed = 8.0f; // diamond pick base
        else if (state.is(BlockTags.MINEABLE_WITH_AXE)) speed = 6.0f;
        else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) speed = 6.0f;
        else speed = 4.0f;
        // If requires correct tool for drops but not pickaxe/axe/shovel, slow down a lot
        if (state.requiresCorrectToolForDrops() && !(state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))) {
            speed *= 0.25f;
        }
        // Calibrate ticks to about vanilla feel: progress per tick ~ speed / (hardness * 30)
        return speed / (hardness * 30.0f);
    }

    private static void clearMining(Level level, Player player) {
        MiningState ms = MINING.remove(player.getUUID());
        if (ms != null && ms.pos != null) {
            level.destroyBlockProgress(player.getId(), ms.pos, -1);
        }
    }

    private static BlockHitResult raycast(Player player, Level level, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));
        ClipContext ctx = new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player);
        HitResult result = level.clip(ctx);
        if (result instanceof BlockHitResult bhr) return bhr;
        return null;
    }

    private static int getFuel(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.getInt(NBT_FUEL);
    }

    private static void addFuel(ItemStack stack, int delta) {
        CompoundTag tag = stack.getOrCreateTag();
        int cur = tag.getInt(NBT_FUEL);
        cur = Math.max(0, cur + delta);
        tag.putInt(NBT_FUEL, cur);
    }

    private static int tryLoadFuel(Player player, ItemStack tool) {
        // Find one HELIO_CELL in inventory and consume it
        Item helio = SolphyteItem.HELIO_CELL.get();
        if (helio == null) return 0;
        int slot = findInventorySlot(player, helio);
        if (slot >= 0) {
            ItemStack cell = player.getInventory().getItem(slot);
            if (!player.isCreative()) {
                cell.shrink(1);
            }
            addFuel(tool, FUEL_PER_CELL);
            player.awardStat(Stats.ITEM_USED.get(helio));
            return FUEL_PER_CELL;
        }
        return 0;
    }

    private static int findInventorySlot(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(item)) return i;
        }
        return -1;
    }

    private static class MiningState {
        BlockPos pos;
        float progress;
        void reset(@Nullable BlockPos p) {
            this.pos = p;
            this.progress = 0f;
        }
    }
}
