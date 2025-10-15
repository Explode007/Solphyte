package shrimpo.solphyte.registry;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.menu.PhytoAlteratorMenu;

public class SolphyteMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Solphyte.MODID);

    public static final RegistryObject<MenuType<PhytoAlteratorMenu>> PHYTO_ALTERATOR = MENUS.register(
            "phyto_alterator",
            () -> IForgeMenuType.create((windowId, inv, buf) -> new PhytoAlteratorMenu(windowId, inv, buf))
    );
}
