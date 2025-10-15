package shrimpo.solphyte.registry;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.effect.StringingEffect;
import shrimpo.solphyte.effect.VisionEffect;

public class SolphyteEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Solphyte.MODID);

    public static final RegistryObject<MobEffect> STRINGING = EFFECTS.register("stringing", StringingEffect::new);
    // Vision: used by Luminthae Shot: Vision
    public static final RegistryObject<MobEffect> VISION = EFFECTS.register("vision", VisionEffect::new);
}
