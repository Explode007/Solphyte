package shrimpo.solphyte.client.model;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import shrimpo.solphyte.block.MutatedCropBlock;

import java.util.HashMap;
import java.util.Map;

import static shrimpo.solphyte.Solphyte.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MutatedCropModelBakeHandler {
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> replacements = new HashMap<>();
        ForgeRegistries.BLOCKS.getValues().forEach(block -> {
            if (block instanceof MutatedCropBlock mcb) {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                if (id != null && id.getNamespace().equals(MODID)) {
                    replacements.put(id, new MutatedCropBakedModel());
                }
            }
        });
        if (replacements.isEmpty()) return;

        event.getModels().replaceAll((mrl, model) -> {
            BakedModel repl = replacements.get(new ResourceLocation(mrl.getNamespace(), mrl.getPath()));
            return repl != null ? repl : model;
        });
    }
}
