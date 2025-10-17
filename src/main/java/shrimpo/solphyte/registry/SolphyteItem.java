package shrimpo.solphyte.registry;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Config;
import shrimpo.solphyte.item.LuminthaeShotItem;
import shrimpo.solphyte.item.LuminthaeSporesItem;
import shrimpo.solphyte.item.SolarVaporizerItem;

import static shrimpo.solphyte.Solphyte.MODID;
import static shrimpo.solphyte.registry.SolphyteCreativeTab.addToTab;

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


    public static final RegistryObject<Item> SOLAR_SHARD = addToTab(ITEMS.register("solar_shard", () -> new Item(new Item.Properties().rarity(Rarity.RARE))));
    public static final RegistryObject<Item> HELIO_CELL = addToTab(ITEMS.register("helio_cell", () -> new Item(new Item.Properties().rarity(Rarity.RARE))));
    public static final RegistryObject<Item> SOLAR_DUST = addToTab(ITEMS.register("solar_dust", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))));
    public static final RegistryObject<Item> SOLAR_BLASTED_SAND = addToTab(ITEMS.register("solar_blasted_sand", () -> new BlockItem(SolphyteBlock.SOLAR_BLASTED_SAND.get(), new Item.Properties().rarity(Rarity.UNCOMMON))));
    public static final RegistryObject<Item> LUMINTHAE_HYPHAE = addToTab(ITEMS.register("luminthae_hyphae", () -> new BlockItem(SolphyteBlock.LUMINTHAE_HYPHAE.get(), new Item.Properties().rarity(Rarity.RARE))));


    public static final RegistryObject<Item> LUMINTHAE_SPORES = addToTab(ITEMS.register("luminthae_spores", () -> new LuminthaeSporesItem(new Item.Properties())));
    public static final RegistryObject<Item> LUMINTHAE_FIBER= addToTab(ITEMS.register("luminthae_fiber", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))));

    // Luminthae Shot: Stringing (applies custom Stringing effect for 20 minutes)
    public static final RegistryObject<Item> LUMINTHAE_SHOT_STRINGING = addToTab(ITEMS.register(
            "luminthae_shot_stringing",
            () -> new LuminthaeShotItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16),
                    () -> new net.minecraft.world.effect.MobEffectInstance(SolphyteEffect.STRINGING.get(), Config.grEffectSeconds * 20)))
    );

    // Luminthae Shot: Vision
    public static final RegistryObject<Item> LUMINTHAE_SHOT_VISION = addToTab(ITEMS.register(
            "luminthae_shot_vision",
            () -> new LuminthaeShotItem(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16),
                    () -> new net.minecraft.world.effect.MobEffectInstance(SolphyteEffect.VISION.get(), Config.grEffectSeconds * 5)))
    );

    public static final RegistryObject<Item> LUMINTHAE_SHOT_EMPTY = addToTab(ITEMS.register(
            "luminthae_shot_empty",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16))

    ));

    public static final RegistryObject<Item> PHYTO_ALTERATOR = addToTab(ITEMS.register(
            "phyto_alterator",
            () -> new BlockItem(SolphyteBlock.PHYTO_ALTERATOR.get(), new Item.Properties().rarity(Rarity.UNCOMMON))));

    // Solar Vaporizer: beam miner/smelter that consumes Helio Cell as fuel
    public static final RegistryObject<Item> SOLAR_VAPORIZER = addToTab(ITEMS.register(
            "solar_vaporizer",
            () -> new SolarVaporizerItem(new Item.Properties().rarity(Rarity.RARE).stacksTo(1))
    ));
}
