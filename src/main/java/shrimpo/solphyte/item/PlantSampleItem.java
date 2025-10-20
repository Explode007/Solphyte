package shrimpo.solphyte.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import shrimpo.solphyte.registry.SolphyteItem;

import java.util.List;

public class PlantSampleItem extends Item {
    public static final String NBT_BLOCK = "Block"; // stores a block id like "minecraft:wheat"

    public PlantSampleItem(Properties props) { super(props); }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = readBlockId(stack);
        if (id != null) {
            return Component.literal("Plant Sample: " + pretty(id));
        }
        return Component.literal("Plant Sample");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Combine with another Plant Sample and Wheat Seeds in a crafting table to get Mutated Seeds").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Nullable
    public static ResourceLocation readBlockId(ItemStack stack) {
        if (stack.hasTag() && stack.getTag() != null && stack.getTag().contains(NBT_BLOCK)) {
            return ResourceLocation.tryParse(stack.getTag().getString(NBT_BLOCK));
        }
        return null;
    }

    public static ItemStack of(ResourceLocation blockId) {
        ItemStack out = new ItemStack(SolphyteItem.PLANT_SAMPLE.get());
        out.getOrCreateTag().putString(NBT_BLOCK, blockId.toString());
        return out;
    }

    public static ItemStack of(Block block) {
        ResourceLocation id = block.builtInRegistryHolder().key().location();
        return of(id);
    }

    private static String pretty(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("/")) path = path.substring(path.lastIndexOf('/') + 1);
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
