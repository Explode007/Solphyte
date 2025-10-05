package org.example.shrimpo.solphyte.registry;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.shrimpo.solphyte.item.LuminthaeSporesItem;

import static org.example.shrimpo.solphyte.Solphyte.MODID;
import static org.example.shrimpo.solphyte.registry.SolphyteBlock.EXAMPLE_BLOCK;
import static org.example.shrimpo.solphyte.registry.SolphyteCreativeTab.addToTab;

public class SolphyteItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> EXAMPLE_ITEM = addToTab(ITEMS.register("example_item",
            () -> new Item(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(5)
                            .saturationMod(0.2f)
                            .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 200, 2), 1f)
                            .build())
                    .rarity(Rarity.EPIC)
            )));

    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SOLAR_SHARD = ITEMS.register("solar_shard", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> HELIO_CELL = ITEMS.register("helio_cell", () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SOLAR_DUST = ITEMS.register("solar_dust", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SOLAR_BLASTED_SAND = ITEMS.register("solar_blasted_sand", () -> new BlockItem(SolphyteBlock.SOLAR_BLASTED_SAND.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> LUMINTHAE_HYPHAE = ITEMS.register("luminthae_hyphae", () -> new BlockItem(SolphyteBlock.LUMINTHAE_HYPHAE.get(), new Item.Properties().rarity(Rarity.RARE)));

    // Luminthae spores item that seeds a dirt block into stage 1 Luminthae
    public static final RegistryObject<Item> LUMINTHAE_SPORES = addToTab(ITEMS.register("luminthae_spores", () -> new LuminthaeSporesItem(new Item.Properties())));
}
