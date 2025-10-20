package shrimpo.solphyte.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.block.*;

import static shrimpo.solphyte.Solphyte.MODID;

public class SolphyteBlock {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final RegistryObject<Block> SPYGLASS_STAND = BLOCKS.register("spyglass_stand",
            () -> new SpyglassStandBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(1.5f, 3.0f)
                    .noOcclusion()));

    public static final RegistryObject<Block> SOLAR_BLASTED_SAND = BLOCKS.register("solar_blasted_sand",
            () -> new SolarBlastedSandBlock(BlockBehaviour.Properties.copy(Blocks.SAND)
                    .mapColor(MapColor.COLOR_YELLOW)
                    .noOcclusion()));

    public static final RegistryObject<Block> LUMINTHAE_HYPHAE = BLOCKS.register("luminthae_hyphae",
            () -> new LuminthaeHyphaeBlock(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.COLOR_BLUE)
                    .sound(SoundType.SHROOMLIGHT)
                    .randomTicks()));

    public static final RegistryObject<Block> PHYTO_ALTERATOR = BLOCKS.register("phyto_alterator",
            () -> new PhytoAlteratorBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(2.5f)
                    .noOcclusion()));

    public static final RegistryObject<Block> MICROSCOPE = BLOCKS.register("microscope",
            () -> new MicroscopeBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(2.5f)
                    .noOcclusion()
                    .noCollission()));

    public static final RegistryObject<Block> PRESS = BLOCKS.register("press",
            () -> new PressBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f)
                    .noOcclusion()
                    .noCollission()));

    public static final RegistryObject<Block> MUTATED_CROP = BLOCKS.register("mutated_crop",
            () -> new MutatedCropBlock(BlockBehaviour.Properties.copy(Blocks.WHEAT)
                    .noOcclusion()
                    .noCollission()
                    .randomTicks()
                    .instabreak()
                    .sound(SoundType.CROP)));
}
