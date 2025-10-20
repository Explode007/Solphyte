package shrimpo.solphyte.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.blockentity.MutatedCropBlockEntity;
import shrimpo.solphyte.registry.SolphyteItem;

import java.util.ArrayList;
import java.util.List;

public class MutatedCropBlock extends CropBlock implements EntityBlock {

    public MutatedCropBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return SolphyteItem.MUTATED_SEEDS.get();
    }

    @Override
    public int getMaxAge() { return 7; }

    @Override
    protected IntegerProperty getAgeProperty() { return BlockStateProperties.AGE_7; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MutatedCropBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // Compute height based on tallest of the two visual inputs at this stage
        int age = state.hasProperty(BlockStateProperties.AGE_7) ? state.getValue(BlockStateProperties.AGE_7) : 0;
        float f = Math.max(0f, Math.min(1f, age / 7f));

        // Constants must mirror renderer minis
        final float MINI_MIN_SY = 0.12f; // ~2px
        final float MINI_MAX_SY = 0.50f; // half block

        float primaryHeight = 0f;
        float secondaryHeight = 0f;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MutatedCropBlockEntity mcb) {
            var pId = mcb.getPrimaryId();
            var sId = mcb.getSecondaryId();

            // Primary
            Block pb = pId != null ? ForgeRegistries.BLOCKS.getValue(pId) : null;
            if (pb instanceof CropBlock) {
                primaryHeight = f; // approximate crop height by growth fraction (up to full block)
            } else if (pb != null) {
                float stage = f * 4f;
                float prog0 = Math.max(0f, Math.min(1f, stage - 0f)); // tallest mini
                primaryHeight = MINI_MIN_SY + prog0 * (MINI_MAX_SY - MINI_MIN_SY);
            }

            // Secondary
            Block sb = sId != null ? ForgeRegistries.BLOCKS.getValue(sId) : null;
            if (sb instanceof CropBlock) {
                secondaryHeight = f;
            } else if (sb != null) {
                float stage = f * 4f;
                float prog0 = Math.max(0f, Math.min(1f, stage - 0f));
                secondaryHeight = MINI_MIN_SY + prog0 * (MINI_MAX_SY - MINI_MIN_SY);
            }
        }

        float height = Math.max(primaryHeight, secondaryHeight);
        // Fallback to default crop height if nothing set
        if (height <= 0f) height = f;
        // Clamp
        height = Math.max(0.0625f, Math.min(1f, height));

        double h = height * 16.0;
        return Block.box(0.0, 0.0, 0.0, 16.0, h, 16.0);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // Only drop seeds for this mutated crop when not mature; mature handled in playerDestroy
        List<ItemStack> results = new ArrayList<>();
        int age = state.hasProperty(BlockStateProperties.AGE_7) ? state.getValue(BlockStateProperties.AGE_7) : 0;
        boolean mature = age >= this.getMaxAge();
        if (!mature) {
            // Exactly 1 seed, preserve NBT if BE is available
            ItemStack seeds = new ItemStack(this.getBaseSeedId(), 1);
            BlockEntity be = builder.getParameter(LootContextParams.BLOCK_ENTITY);
            if (be instanceof MutatedCropBlockEntity mcb) {
                CompoundTag tag = seeds.getOrCreateTag();
                CompoundTag bet = tag.getCompound("BlockEntityTag");
                if (mcb.getPrimaryId() != null) bet.putString("Primary", mcb.getPrimaryId().toString());
                if (mcb.getSecondaryId() != null) bet.putString("Secondary", mcb.getSecondaryId().toString());
                tag.put("BlockEntityTag", bet);
                seeds.setTag(tag);
            }
            results.add(seeds);
        }
        return results;
    }

    @Override
    public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        if (!level.isClientSide && be instanceof MutatedCropBlockEntity mcb) {
            int age = state.hasProperty(BlockStateProperties.AGE_7) ? state.getValue(BlockStateProperties.AGE_7) : 0;
            if (age >= this.getMaxAge()) {
                RandomSource rand = level.getRandom();

                // Drop mutated seeds (1-4), preserving NBT
                int seedCount = 1 + rand.nextInt(4);
                ItemStack seeds = new ItemStack(this.getBaseSeedId(), seedCount);
                CompoundTag tag = seeds.getOrCreateTag();
                CompoundTag bet = tag.getCompound("BlockEntityTag");
                if (mcb.getPrimaryId() != null) bet.putString("Primary", mcb.getPrimaryId().toString());
                if (mcb.getSecondaryId() != null) bet.putString("Secondary", mcb.getSecondaryId().toString());
                tag.put("BlockEntityTag", bet);
                seeds.setTag(tag);
                popResource(level, pos, seeds);

                // Drop produce from each base crop (1-4 each)
                Item prodA = resolveProduce(mcb.getPrimaryId());
                Item prodB = resolveProduce(mcb.getSecondaryId());
                int aCount = 1 + rand.nextInt(4);
                int bCount = 1 + rand.nextInt(4);
                popResource(level, pos, new ItemStack(prodA, aCount));
                popResource(level, pos, new ItemStack(prodB, bCount));

                super.playerDestroy(level, player, pos, state, null, tool);
                return;
            }
        }
        super.playerDestroy(level, player, pos, state, be, tool);
    }

    private static Item resolveProduce(net.minecraft.resources.ResourceLocation cropBlockId) {
        if (cropBlockId == null) return Items.WHEAT;
        Block b = ForgeRegistries.BLOCKS.getValue(cropBlockId);
        if (b == net.minecraft.world.level.block.Blocks.CARROTS) return Items.CARROT;
        if (b == net.minecraft.world.level.block.Blocks.POTATOES) return Items.POTATO;
        if (b == net.minecraft.world.level.block.Blocks.BEETROOTS) return Items.BEETROOT;
        if (b == net.minecraft.world.level.block.Blocks.WHEAT) return Items.WHEAT;
        Item item = b.asItem();
        return item != null ? item : Items.WHEAT;
    }
}
