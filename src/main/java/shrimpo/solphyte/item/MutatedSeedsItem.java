package shrimpo.solphyte.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import shrimpo.solphyte.blockentity.MutatedCropBlockEntity;

public class MutatedSeedsItem extends ItemNameBlockItem {

    public MutatedSeedsItem(java.util.function.Supplier<? extends Block> block, Properties properties) {
        super(block.get(), properties);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
        normalizeTag(stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MutatedCropBlockEntity mcb) {
            ResourceLocation p = readId(stack, "Primary");
            ResourceLocation s = readId(stack, "Secondary");
            if (p != null && s != null) mcb.setPair(p, s);
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        normalizeTag(stack);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation p = readId(stack, "Primary");
        ResourceLocation s = readId(stack, "Secondary");
        if (p != null && s != null) {
            return Component.literal("Mutated Seeds: " + pretty(p) + " + " + pretty(s));
        }
        return Component.literal("Mutated Seeds (unset)");
    }

    private static void normalizeTag(ItemStack stack) {
        if (!stack.hasTag()) return;
        var tag = stack.getTag();
        if (tag == null) return;
        boolean moved = false;
        var bet = tag.getCompound("BlockEntityTag");
        if (tag.contains("Primary")) { bet.putString("Primary", tag.getString("Primary")); tag.remove("Primary"); moved = true; }
        if (tag.contains("Secondary")) { bet.putString("Secondary", tag.getString("Secondary")); tag.remove("Secondary"); moved = true; }
        if (moved) {
            tag.put("BlockEntityTag", bet);
            stack.setTag(tag);
        }
    }

    private static String pretty(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static ResourceLocation readId(ItemStack stack, String key) {
        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag != null && tag.contains("BlockEntityTag")) {
                var bet = tag.getCompound("BlockEntityTag");
                if (bet.contains(key)) return ResourceLocation.tryParse(bet.getString(key));
            }
            if (tag != null && tag.contains(key)) return ResourceLocation.tryParse(tag.getString(key));
        }
        return null;
    }
}

