package shrimpo.solphyte;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.client.render.GrappleNodeRenderer;
import shrimpo.solphyte.client.render.SolarVaporizerRenderer;
import shrimpo.solphyte.client.render.SpyglassStandRenderer;
import shrimpo.solphyte.client.render.VisionRenderer;
import shrimpo.solphyte.client.screen.PhytoAlteratorScreen;
import shrimpo.solphyte.network.SolphyteNetwork;
import shrimpo.solphyte.registry.SolphyteBlockEntity;
import shrimpo.solphyte.registry.SolphyteMenu;
import org.slf4j.Logger;

import static shrimpo.solphyte.registry.SolphyteBlock.BLOCKS;
import static shrimpo.solphyte.registry.SolphyteBlock.SPYGLASS_STAND;
import static shrimpo.solphyte.registry.SolphyteBlock.LUMINTHAE_HYPHAE;
import static shrimpo.solphyte.registry.SolphyteBlockEntity.BLOCK_ENTITY_TYPES;
import static shrimpo.solphyte.registry.SolphyteCreativeTab.TABS;
import static shrimpo.solphyte.registry.SolphyteItem.ITEMS;
import static shrimpo.solphyte.registry.SolphyteMenu.MENUS;
import static shrimpo.solphyte.registry.SolphyteEffect.EFFECTS;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Solphyte.MODID)
public class Solphyte {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "solphyte";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Solphyte() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so block entities get registered
        BLOCK_ENTITY_TYPES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so menus get registered
        MENUS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so effects get registered
        EFFECTS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
//        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
    @SubscribeEvent
    public void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
        event.enqueueWork(SolphyteNetwork::init);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity item && !event.getLevel().isClientSide()) {
            BlockPos below = item.blockPosition().below();
            BlockState bs = event.getLevel().getBlockState(below);
            if (bs.is(LUMINTHAE_HYPHAE.get())) {
                event.getLevel().scheduleTick(below, LUMINTHAE_HYPHAE.get(), 2);
            }
        }
    }

    // Periodically scan chunks around players present in each world and schedule short ticks for luminthae blocks.
    private int luminthaeScanCounter = 0;

    @SubscribeEvent
    public void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level level = event.level;
        if (level.isClientSide()) return;

        luminthaeScanCounter++;
        // run every ~100 ticks (5 seconds at 20 TPS)
        if (luminthaeScanCounter % 100 != 0) return;

        // radius in chunks around each player to scan
        final int chunkRadius = 1; // 1 => 3x3 chunks around player
        for (Player player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            int playerChunkX = playerPos.getX() >> 4;
            int playerChunkZ = playerPos.getZ() >> 4;

            int minY = Math.max(level.getMinBuildHeight(), playerPos.getY() - 16);
            int maxY = Math.min(level.getMaxBuildHeight(), playerPos.getY() + 16);

            for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
                for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                    int baseX = cx << 4;
                    int baseZ = cz << 4;
                    // Skip scanning if the chunk isn't loaded to avoid forcing chunk loads or extra work
                    BlockPos chunkCenter = new BlockPos(baseX + 8, playerPos.getY(), baseZ + 8);
                    if (!level.isLoaded(chunkCenter)) continue;
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            for (int y = minY; y <= maxY; y++) {
                                BlockPos p = new BlockPos(baseX + dx, y, baseZ + dz);
                                BlockState bs = level.getBlockState(p);
                                if (bs.is(LUMINTHAE_HYPHAE.get())) {
                                    level.scheduleTick(p, LUMINTHAE_HYPHAE.get(), 2);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            event.enqueueWork(() -> {
                ItemBlockRenderTypes.setRenderLayer(SPYGLASS_STAND.get(), RenderType.cutout());
                ItemBlockRenderTypes.setRenderLayer(LUMINTHAE_HYPHAE.get(), RenderType.cutoutMipped());
                BlockEntityRenderers.register(SolphyteBlockEntity.SPYGLASS_STAND.get(), SpyglassStandRenderer::new);
                MenuScreens.register(SolphyteMenu.PHYTO_ALTERATOR.get(), PhytoAlteratorScreen::new);
            });


            MinecraftForge.EVENT_BUS.addListener(VisionRenderer::onRenderLevel);
            MinecraftForge.EVENT_BUS.addListener(GrappleNodeRenderer::onRenderLevel);
            // SolarVaporizerRenderer is registered via @EventBusSubscriber
        }
    }
}
