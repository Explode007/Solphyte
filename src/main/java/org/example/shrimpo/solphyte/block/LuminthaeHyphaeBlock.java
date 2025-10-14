package org.example.shrimpo.solphyte.block;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import org.example.shrimpo.solphyte.registry.SolphyteItem;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LuminthaeHyphaeBlock extends Block {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 3);

    private static final Set<Item> ORGANIC_MOB_DROPS = new ObjectOpenHashSet<>();
    static {
        ORGANIC_MOB_DROPS.add(Items.ROTTEN_FLESH);
        ORGANIC_MOB_DROPS.add(Items.BONE);
        ORGANIC_MOB_DROPS.add(Items.SPIDER_EYE);
        ORGANIC_MOB_DROPS.add(Items.GHAST_TEAR);
        ORGANIC_MOB_DROPS.add(Items.BLAZE_ROD);
        ORGANIC_MOB_DROPS.add(Items.ENDER_PEARL);
        ORGANIC_MOB_DROPS.add(Items.GUNPOWDER);
        ORGANIC_MOB_DROPS.add(Items.SLIME_BALL);
        ORGANIC_MOB_DROPS.add(Items.PHANTOM_MEMBRANE);
    }

    public LuminthaeHyphaeBlock(Properties properties) {
        super(properties.sound(SoundType.SHROOMLIGHT));
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            // schedule a periodic tick so the block will poll for item entities above it
            // using a longer initial delay to avoid excessive ticking right after placement
            level.scheduleTick(pos, this, 20);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        // scan for ItemEntities resting on/above this block and process them
        // tighter box centered on block top; we only need to catch items roughly above the block
        AABB box = new AABB(pos).expandTowards(1, 1.5, 0.6);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        boolean anyProcessed = false;
        for (ItemEntity item : items) {
            // only process items that are basically resting on the block (small downward speed)
            // Also accept items that have been on the ground a bit (age > 10) to handle slower landings
            if (item.getAge() > 10) {
                // reuse the same logic as entityInside
                ItemStack stack = item.getItem();
                if (stack.isEmpty()) continue;
                Item it = stack.getItem();
                if (!ORGANIC_MOB_DROPS.contains(it)) continue;

                // process this item by delegating to entityInside-equivalent logic
                processFeed(state, level, pos, item);
                anyProcessed = true;
            }
        }
        // Always reschedule another tick so the block continuously polls for items that may land later.
        if (anyProcessed) {
            level.scheduleTick(pos, this, 5);
        } else {
            level.scheduleTick(pos, this, 20);
        }
    }

//    @Override
//    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
//        super.entityInside(state, level, pos, entity);
//        if (level.isClientSide) return;
//        if (!(entity instanceof ItemEntity itemEntity)) return;
//
//        processFeed(state, level, pos, itemEntity);
//    }

    private void processFeed(BlockState state, Level level, BlockPos pos, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        Item it = stack.getItem();
        if (!ORGANIC_MOB_DROPS.contains(it)) return;

        LOGGER.info("Luminthae feed detected at {} with item {} and stage {}", pos, it, state.getValue(STAGE));

        int stage = state.getValue(STAGE);
        if (stage < 3) {
            level.setBlock(pos, state.setValue(STAGE, stage + 1), 3);
        } else {
            // If this block is already fully-grown, prefer to grow nearby non-fully-grown luminthae
            List<BlockPos> candidates = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos p = pos.offset(dx, dy, dz);
                        BlockState bs = level.getBlockState(p);
                        if (bs.is(this) && bs.getValue(STAGE) < 3) candidates.add(p);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                BlockPos target = candidates.get(level.getRandom().nextInt(candidates.size()));
                BlockState ts = level.getBlockState(target);
                level.setBlock(target, ts.setValue(STAGE, ts.getValue(STAGE) + 1), 3);
            } else {
                // No nearby non-full blocks; keep at stage 3 and fall through to spreading/convert logic below
                level.setBlock(pos, state.setValue(STAGE, 3), 3);
            }
        }

        // Consume one item from the entity
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.remove(Entity.RemovalReason.DISCARDED);
        } else {
            itemEntity.setItem(stack);
        }


        // Emit glow-squid-like particles at the consumed item's position
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.GLOW_SQUID_INK, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), 8, 0.15, 0.15, 0.15, 0.02);
        }
        level.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), SoundEvents.GENERIC_EAT, SoundSource.BLOCKS, 1.0f, 1.0f);

        // Spread to nearby blocks (1-block radius) only when fed
        RandomSource rand = level.getRandom();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (rand.nextFloat() < 0.5f) {
                        BlockPos p = pos.offset(dx, dy, dz);
                        BlockState target = level.getBlockState(p);
                        if (target.is(this)) {
                            int cur = target.getValue(STAGE);
                            if (cur < 3) level.setBlock(p, target.setValue(STAGE, cur + 1), 3);
                        } else if (isConvertable(target)) {
                            level.setBlock(p, this.defaultBlockState().setValue(STAGE, 1), 3);
                        }
                    }
                }
            }
        }
    }

    private boolean isConvertable(BlockState state) {
        List<Block> validBlocks = List.of(
                Blocks.DIRT,
                Blocks.GRASS_BLOCK,
                Blocks.PODZOL,
                Blocks.MYCELIUM
        );

        for (Block b : validBlocks) {
            if (state.is(b)) return true;
        }

        return false;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(STAGE) >= 3) {
            popResource(level, pos, new ItemStack(SolphyteItem.LUMINTHAE_FIBER.get()));
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
