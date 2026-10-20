package com.createstats.aviatorhelmet.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AviatorHelmetClientSetup {
    private AviatorHelmetClientSetup() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(AviatorHelmetKeys.GOGGLES);
        event.register(AviatorHelmetKeys.TONE);
    }

    @SubscribeEvent
    public static void guiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_helmet_lenses"),
                AviatorHud::renderLenses);
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "aviator_helmet_hud"),
                AviatorHud::renderHud);
    }

    @SubscribeEvent
    public static void itemRenderer(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return AviatorHelmetItemRenderer.get();
            }
        }, ModBlocks.AVIATOR_HELMET.asItem());
    }
}
