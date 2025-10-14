package org.example.shrimpo.solphyte;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Solphyte.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER.comment("Whether to log the dirt block on common setup").define("logDirtBlock", true);

    private static final ForgeConfigSpec.IntValue MAGIC_NUMBER = BUILDER.comment("A magic number").defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER.comment("What you want the introduction message to be for the magic number").define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER.comment("A list of items to log on common setup.").defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), Config::validateItemName);

    // Grapple section (only keep values used outside GrappleEvents)
    static {
        BUILDER.push("grapple");
    }
    // Distance you can get to the anchor (never get closer than this)
    private static final ForgeConfigSpec.DoubleValue GR_MIN_DISTANCE = BUILDER.comment("Minimum rope length (you can never get closer than this to the anchor)").defineInRange("minDistance", 3.0, 0.25, 64.0);
    // Limit how far the anchor can be from the player at start (clamped along ray)
    private static final ForgeConfigSpec.DoubleValue GR_MAX_ANCHOR_DISTANCE = BUILDER.comment("Maximum distance the anchor can be from the player when grapple starts (clamped along the look ray)").defineInRange("maxAnchorDistance", 12.0, 1.0, 128.0);
    // Add slack to the rope on start so you don't go taut instantly
    private static final ForgeConfigSpec.DoubleValue GR_START_SLACK = BUILDER.comment("Extra rope length added on grapple start (meters) to avoid instant taut pull").defineInRange("startSlack", 0.8, 0.0, 8.0);
    // Duration (seconds) for the Stringing effect applied by Luminthae Shot
    private static final ForgeConfigSpec.IntValue GR_EFFECT_SECONDS =
            BUILDER.comment("Duration (seconds) for the Stringing effect applied by Luminthae Shot")
                    .defineInRange("effectSeconds", 20 * 60, 1, 24 * 60 * 60);
    // Cooldown (seconds) after grappling to a NEW node; grappling to an existing node has no cooldown
    private static final ForgeConfigSpec.IntValue GR_COOLDOWN_SECONDS =
            BUILDER.comment("Cooldown (seconds) after grappling to a NEW node; grappling to an existing node has no cooldown")
                    .defineInRange("cooldownSeconds", 6, 0, 300);
    // Radius to snap the grapple start point to an existing node (meters)
    private static final ForgeConfigSpec.DoubleValue GR_SNAP_RADIUS = BUILDER
            .comment("Radius to snap the grapple start point to an existing node (meters)")
            .defineInRange("snapRadius", 2.0, 0.0, 8.0);
    // Radius of the node “hitbox” used to stop the grapple ray (meters)
    private static final ForgeConfigSpec.DoubleValue GR_NODE_HIT_RADIUS = BUILDER
            .comment("Radius of the node hitbox to stop the grapple ray (meters)")
            .defineInRange("nodeHitRadius", 1.25, 0.1, 8.0);

    static final ForgeConfigSpec SPEC = BUILDER.pop().build();

    public static boolean logDirtBlock;
    public static int magicNumber;
    public static String magicNumberIntroduction;
    public static Set<Item> items;

    // Grapple runtime values (only the ones still referenced)
    public static double grMinDistance;
    public static double grMaxAnchorDistance;
    public static double grStartSlack;
    public static int grEffectSeconds;
    public static int grCooldownTicks;
    public static double grSnapRadius;
    public static double grNodeHitRadius;

    private static boolean validateItemName(final Object obj) {
        //noinspection removal
        return obj instanceof final String itemName && ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemName));
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        logDirtBlock = LOG_DIRT_BLOCK.get();
        magicNumber = MAGIC_NUMBER.get();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();

        // convert the list of strings into a set of items
        //noinspection removal
        items = ITEM_STRINGS.get().stream().map(itemName -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName))).collect(Collectors.toSet());

        // Grapple (only values used by networking/items)
        grMinDistance = GR_MIN_DISTANCE.get();
        grMaxAnchorDistance = GR_MAX_ANCHOR_DISTANCE.get();
        grStartSlack = GR_START_SLACK.get();
        grEffectSeconds = GR_EFFECT_SECONDS.get();
        grCooldownTicks = GR_COOLDOWN_SECONDS.get() * 20;
        grSnapRadius = GR_SNAP_RADIUS.get();
        grNodeHitRadius = GR_NODE_HIT_RADIUS.get();
    }
}