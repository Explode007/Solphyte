package org.example.shrimpo.solphyte.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.shrimpo.solphyte.Solphyte;
import org.example.shrimpo.solphyte.blockentity.SpyglassStandBlockEntity;

public class SolphyteBlockEntity {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Solphyte.MODID);

    public static final RegistryObject<BlockEntityType<SpyglassStandBlockEntity>> SPYGLASS_STAND =
            BLOCK_ENTITY_TYPES.register(
            "spyglass_stand",
            () -> BlockEntityType.Builder.of(SpyglassStandBlockEntity::new, org.example.shrimpo.solphyte.registry.SolphyteBlock.SPYGLASS_STAND.get()).build(null)
    );
}
