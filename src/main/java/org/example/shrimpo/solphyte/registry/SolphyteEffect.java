package org.example.shrimpo.solphyte.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.example.shrimpo.solphyte.Solphyte;
import org.example.shrimpo.solphyte.effect.StringingEffect;

public class SolphyteEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Solphyte.MODID);

    public static final RegistryObject<MobEffect> STRINGING = EFFECTS.register("stringing", StringingEffect::new);
}

