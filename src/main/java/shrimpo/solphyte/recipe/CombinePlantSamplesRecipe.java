package shrimpo.solphyte.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import shrimpo.solphyte.item.PlantSampleItem;
import shrimpo.solphyte.registry.SolphyteItem;
import shrimpo.solphyte.registry.SolphyteRecipeSerializers;
import shrimpo.solphyte.registry.SolphyteTags;

public class CombinePlantSamplesRecipe extends CustomRecipe {
    public CombinePlantSamplesRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        // Require exactly two plant samples and exactly one base seed (tag: solphyte:mutation_bases), nothing else
        int samples = 0;
        int bases = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            Item it = s.getItem();
            if (it == SolphyteItem.PLANT_SAMPLE.get()) {
                ResourceLocation id = PlantSampleItem.readBlockId(s);
                if (id == null) return false; // sample must carry a block id
                samples++;
            } else if (s.is(SolphyteTags.Items.MUTATION_BASES)) {
                bases++;
                if (bases > 1) return false; // only one base allowed
            } else {
                return false; // unknown ingredient present
            }
        }
        return samples == 2 && bases == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ResourceLocation first = null, second = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() == SolphyteItem.PLANT_SAMPLE.get()) {
                ResourceLocation id = PlantSampleItem.readBlockId(s);
                if (id != null) {
                    if (first == null) first = id; else if (second == null) second = id;
                }
            }
        }
        if (first == null || second == null) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(SolphyteItem.MUTATED_SEEDS.get());
        var tag = out.getOrCreateTag();
        var bet = tag.getCompound("BlockEntityTag");
        bet.putString("Primary", first.toString());
        bet.putString("Secondary", second.toString());
        tag.put("BlockEntityTag", bet);
        out.setTag(tag);
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) { return w * h >= 3; }

    @Override
    public RecipeSerializer<?> getSerializer() { return SolphyteRecipeSerializers.COMBINE_PLANT_SAMPLES.get(); }
}
