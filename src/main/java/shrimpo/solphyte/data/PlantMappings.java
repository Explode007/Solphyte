package shrimpo.solphyte.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

import static shrimpo.solphyte.Solphyte.MODID;

public final class PlantMappings {
    private static final Gson GSON = new Gson();
    private static final Map<ResourceLocation, ResourceLocation> ITEM_TO_BLOCK = new HashMap<>();

    private PlantMappings() {}

    public static ResourceLocation getMappedBlock(ResourceLocation itemId) {
        return ITEM_TO_BLOCK.get(itemId);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class Reloader extends SimpleJsonResourceReloadListener {
        public Reloader() { super(GSON, "mutations"); }

        @SubscribeEvent
        public static void onAddReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new Reloader());
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager rm, ProfilerFiller profiler) {
            ITEM_TO_BLOCK.clear();
            for (Map.Entry<ResourceLocation, JsonElement> e : jsons.entrySet()) {
                JsonElement el = e.getValue();
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (!obj.has("seed") || !obj.has("block")) continue;
                ResourceLocation seed = ResourceLocation.tryParse(obj.get("seed").getAsString());
                ResourceLocation block = ResourceLocation.tryParse(obj.get("block").getAsString());
                if (seed != null && block != null) {
                    ITEM_TO_BLOCK.put(seed, block);
                }
            }
        }
    }
}
