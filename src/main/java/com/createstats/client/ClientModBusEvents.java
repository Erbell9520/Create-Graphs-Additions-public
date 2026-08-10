package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.flight.screen.FlightControlComputerScreen;
import com.createstats.gui.BattleScreenLinkedControllerScreen;
import com.createstats.gui.FactoryLogicControllerScreen;
import com.createstats.pathfinder.client.PathfinderComputerScreen;
import com.createstats.pathfinder.client.PathScannerScreen;
import com.createstats.registry.ModMenuTypes;
import com.createstats.registry.ModItems;
import net.createmod.ponder.foundation.PonderIndex;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientModBusEvents {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.FACTORY_LOGIC_CONTROLLER.get(), FactoryLogicControllerScreen::new);
        event.register(ModMenuTypes.BATTLE_SCREEN_LINKED_CONTROLLER.get(), BattleScreenLinkedControllerScreen::new);
        event.register(ModMenuTypes.FLIGHT_CONTROL_COMPUTER.get(), FlightControlComputerScreen::new);
        event.register(ModMenuTypes.VCC_MK_II.get(), com.createstats.vccmk2.VccMk2Screen::new);
        event.register(ModMenuTypes.PATH_SCANNER.get(), PathScannerScreen::new);
        event.register(ModMenuTypes.PATHFINDER_COMPUTER.get(), PathfinderComputerScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderIndex.addPlugin(new CreateStatsPonderPlugin());
        });
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return KineticMultimeterRenderer.getInstance();
            }
        }, ModItems.KINETIC_MULTIMETER.get());
    }
}
