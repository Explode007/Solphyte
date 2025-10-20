package shrimpo.solphyte.registry;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import shrimpo.solphyte.Solphyte;
import shrimpo.solphyte.menu.PhytoAlteratorMenu;
import shrimpo.solphyte.menu.MicroscopeMenu;
import shrimpo.solphyte.menu.PressMenu;

public class SolphyteMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Solphyte.MODID);

    public static final RegistryObject<MenuType<PhytoAlteratorMenu>> PHYTO_ALTERATOR = MENUS.register(
            "phyto_alterator",
            () -> IForgeMenuType.create((windowId, inv, buf) -> new PhytoAlteratorMenu(windowId, inv, buf))
    );

    public static final RegistryObject<MenuType<MicroscopeMenu>> MICROSCOPE = MENUS.register(
            "microscope",
            () -> IForgeMenuType.create((windowId, inv, buf) -> new MicroscopeMenu(windowId, inv, buf))
    );

    public static final RegistryObject<MenuType<PressMenu>> PRESS = MENUS.register(
            "press",
            () -> IForgeMenuType.create((windowId, inv, buf) -> new PressMenu(windowId, inv, buf))
    );
}
