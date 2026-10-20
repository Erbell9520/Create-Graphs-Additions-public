package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DriverHelmetClientSetup {
    private DriverHelmetClientSetup() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(DriverHelmetKeys.VISOR);
        event.register(DriverHelmetKeys.NIGHT_VISION);
        event.register(DriverHelmetKeys.THEME);
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_visor"),
                DriverHelmetHud::renderVisor);
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "driver_helmet_hud"),
                DriverHelmetHud::renderHud);
    }

    @SubscribeEvent
    public static void itemRenderer(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return DriverHelmetItemRenderer.get();
            }
        }, ModBlocks.DRIVER_HELMET.asItem());
    }
}
