package shrimpo.solphyte.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.blockentity.SpyglassStandBlockEntity;
import shrimpo.solphyte.blockentity.PhytoAlteratorBlockEntity;
import shrimpo.solphyte.blockentity.MutatedCropBlockEntity;
import shrimpo.solphyte.blockentity.MicroscopeBlockEntity;
import shrimpo.solphyte.blockentity.PressBlockEntity;

public class SolphyteBlockEntity {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Solphyte.MODID);

    public static final RegistryObject<BlockEntityType<SpyglassStandBlockEntity>> SPYGLASS_STAND =
            BLOCK_ENTITY_TYPES.register(
            "spyglass_stand",
            () -> BlockEntityType.Builder.of(SpyglassStandBlockEntity::new, SolphyteBlock.SPYGLASS_STAND.get()).build(null)
    );

    // Phyto-Alterator (two-block wide workstation, left half owns BE)
    public static final RegistryObject<BlockEntityType<PhytoAlteratorBlockEntity>> PHYTO_ALTERATOR =
            BLOCK_ENTITY_TYPES.register(
                    "phyto_alterator",
                    () -> BlockEntityType.Builder.of(PhytoAlteratorBlockEntity::new, SolphyteBlock.PHYTO_ALTERATOR.get()).build(null)
            );

    public static final RegistryObject<BlockEntityType<MutatedCropBlockEntity>> MUTATED_CROP =
            BLOCK_ENTITY_TYPES.register(
                    "mutated_crop",
                    () -> BlockEntityType.Builder.of(MutatedCropBlockEntity::new, SolphyteBlock.MUTATED_CROP.get()).build(null)
            );

    // New single-block workstations
    public static final RegistryObject<BlockEntityType<MicroscopeBlockEntity>> MICROSCOPE =
            BLOCK_ENTITY_TYPES.register(
                    "microscope",
                    () -> BlockEntityType.Builder.of(MicroscopeBlockEntity::new, SolphyteBlock.MICROSCOPE.get()).build(null)
            );

    public static final RegistryObject<BlockEntityType<PressBlockEntity>> PRESS =
            BLOCK_ENTITY_TYPES.register(
                    "press",
                    () -> BlockEntityType.Builder.of(PressBlockEntity::new, SolphyteBlock.PRESS.get()).build(null)
            );
}
