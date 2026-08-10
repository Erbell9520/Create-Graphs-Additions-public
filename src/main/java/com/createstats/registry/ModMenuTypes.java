package com.createstats.registry;

import com.createstats.CreateStats;
import com.createstats.menu.BattleScreenLinkedControllerMenu;
import com.createstats.flight.menu.FlightControlComputerMenu;
import com.createstats.menu.FactoryLogicControllerMenu;
import com.createstats.pathfinder.menu.PathfinderComputerMenu;
import com.createstats.pathfinder.menu.PathScannerMenu;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModMenuTypes {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreateStats.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FactoryLogicControllerMenu>> FACTORY_LOGIC_CONTROLLER =
            MENUS.register("factory_logic_controller", () -> IMenuTypeExtension.create(FactoryLogicControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BattleScreenLinkedControllerMenu>> BATTLE_SCREEN_LINKED_CONTROLLER =
            MENUS.register("battle_screen_linked_controller", () -> IMenuTypeExtension.create(BattleScreenLinkedControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FlightControlComputerMenu>> FLIGHT_CONTROL_COMPUTER =
            MENUS.register("flight_control_computer", () -> IMenuTypeExtension.create(FlightControlComputerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<com.createstats.vccmk2.VccMk2Menu>> VCC_MK_II =
            MENUS.register("vcc_mk2", () -> IMenuTypeExtension.create(com.createstats.vccmk2.VccMk2Menu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PathScannerMenu>> PATH_SCANNER =
            MENUS.register("path_scanner", () -> IMenuTypeExtension.create(PathScannerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PathfinderComputerMenu>> PATHFINDER_COMPUTER =
            MENUS.register("pathfinder_computer", () -> IMenuTypeExtension.create(PathfinderComputerMenu::new));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
