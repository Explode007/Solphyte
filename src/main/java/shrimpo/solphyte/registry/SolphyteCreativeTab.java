package shrimpo.solphyte.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Solphyte;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SolphyteCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Solphyte.MODID);

    public static final List<Supplier<? extends ItemLike>> SOLPHYTE_TAB_ITEMS = new ArrayList<>();

    public static final RegistryObject<CreativeModeTab> SOLPHYTE_TAB = TABS.register("solphyte",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.solphyte"))
                    .icon(SolphyteItem.EXAMPLE_ITEM.get()::getDefaultInstance)
                    .displayItems((displayParams, output) ->
                            SOLPHYTE_TAB_ITEMS.forEach(itemLike -> output.accept(itemLike.get())))
                    .withSearchBar()
                    .build()
    );

    public static <T extends Item> RegistryObject<T> addToTab(RegistryObject<T> itemLike) {
        SOLPHYTE_TAB_ITEMS.add(itemLike);
        return itemLike;
    }

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if(event.getTab() == SOLPHYTE_TAB.get()) {
            event.accept(Items.CROSSBOW);
        }
    }
}
