package shrimpo.solphyte.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class SolphyteTags {
    private SolphyteTags() {}

    public static final class Items {
        private Items() {}
        public static final TagKey<Item> MICROSCOPE_INPUTS = TagKey.create(Registries.ITEM, new ResourceLocation("solphyte", "microscope_inputs"));
        public static final TagKey<Item> MUTATION_BASES   = TagKey.create(Registries.ITEM, new ResourceLocation("solphyte", "mutation_bases"));
    }
}
