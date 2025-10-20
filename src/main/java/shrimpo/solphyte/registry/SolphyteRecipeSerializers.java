package shrimpo.solphyte.registry;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.recipe.CombinePlantSamplesRecipe;

public class SolphyteRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Solphyte.MODID);

    public static final RegistryObject<RecipeSerializer<CombinePlantSamplesRecipe>> COMBINE_PLANT_SAMPLES =
            RECIPE_SERIALIZERS.register("combine_plant_samples", () -> new SimpleCraftingRecipeSerializer<>(CombinePlantSamplesRecipe::new));
}

