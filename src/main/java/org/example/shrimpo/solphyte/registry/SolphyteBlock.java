package org.example.shrimpo.solphyte.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.shrimpo.solphyte.block.LuminthaeHyphaeBlock;
import org.example.shrimpo.solphyte.block.SolarBlastedSandBlock;
import org.example.shrimpo.solphyte.block.SpyglassStandBlock;

import static org.example.shrimpo.solphyte.Solphyte.MODID;

public class SolphyteBlock {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);

    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.ANVIL)
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(5.0f, 17f)
                    .instrument(NoteBlockInstrument.BANJO)
                    .lightLevel(state -> 10)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.DESTROY)
            ));


    public static final RegistryObject<Block> SPYGLASS_STAND = BLOCKS.register("spyglass_stand",
            () -> new SpyglassStandBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(1.5f, 3.0f)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> SOLAR_BLASTED_SAND = BLOCKS.register("solar_blasted_sand",
            () -> new SolarBlastedSandBlock(
                    (BlockBehaviour.Properties.copy(Blocks.SAND)
                            .mapColor(MapColor.COLOR_YELLOW)
                            .noOcclusion()
                    )
            ));

    public static final RegistryObject<Block> LUMINTHAE_HYPHAE = BLOCKS.register("luminthae_hyphae",
            () -> new LuminthaeHyphaeBlock(BlockBehaviour.Properties.copy(Blocks.DIRT)
                    .mapColor(MapColor.COLOR_BLUE)
                    .sound(SoundType.SHROOMLIGHT)
                    .lightLevel(state -> {
                        int s = state.hasProperty(LuminthaeHyphaeBlock.STAGE) ? state.getValue(LuminthaeHyphaeBlock.STAGE) : 0;
                        return switch (s) {
                            case 2 -> 6; // small glow
                            case 3 -> 12; // more glow
                            default -> 0;
                        };
                    })
                    .randomTicks()
            ));

}
