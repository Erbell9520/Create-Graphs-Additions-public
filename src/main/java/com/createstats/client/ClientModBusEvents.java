package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.flight.screen.FlightControlComputerScreen;
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
        event.register(ModMenuTypes.ARC_FURNACE.get(), com.createstats.gui.ArcFurnaceScreen::new);
        event.register(ModMenuTypes.SECURITY_LOCKER.get(), com.createstats.gui.SecurityLockerScreen::new);
        event.register(ModMenuTypes.INDUCTION_SMELTER.get(),
                com.createstats.gui.InductionSmelterScreen::new);
        event.register(ModMenuTypes.FACTORY_LOGIC_CONTROLLER.get(), FactoryLogicControllerScreen::new);
        event.register(ModMenuTypes.FLIGHT_CONTROL_COMPUTER.get(), FlightControlComputerScreen::new);
        event.register(ModMenuTypes.VCC_MK_II.get(), com.createstats.vccmk2.VccMk2Screen::new);
        event.register(ModMenuTypes.VCC_MK3.get(), com.createstats.vccmk3.client.VccMk3Screen::new);
        event.register(ModMenuTypes.PATH_SCANNER.get(), PathScannerScreen::new);
        event.register(ModMenuTypes.PATHFINDER_COMPUTER.get(), PathfinderComputerScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PonderIndex.addPlugin(new CreateStatsPonderPlugin());
            registerMugIcons();
            com.createstats.client.dynlight.ClientDynamicLights.init();
        });
    }

    // one item per material with a content component, so the model comes from the stack. no property = latte looks empty
    private static void registerMugIcons() {
        final net.minecraft.resources.ResourceLocation key =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "content");
        for (net.minecraft.world.item.Item mug : new net.minecraft.world.item.Item[]{
                com.createstats.registry.ModBlocks.CERAMIC_MUG.asItem(),
                com.createstats.registry.ModBlocks.ENAMEL_MUG.asItem(),
                com.createstats.registry.ModBlocks.STEEL_MUG.asItem()}) {
            net.minecraft.client.renderer.item.ItemProperties.register(mug, key,
                    (stack, level, entity, seed) ->
                            com.createstats.item.CoffeeMugItem.contentOf(stack).ordinal());
        }
    }

    @SubscribeEvent
    public static void registerShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
        com.createstats.holomk3.render.HoloShaders.register(event);
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
