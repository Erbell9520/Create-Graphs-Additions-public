package com.createstats.cinecam.client;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CineClientSetup {
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "cine_camera");

    public static final KeyMapping RECORD = new KeyMapping("key.create_stats.cine_camera_record",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.create_stats");

    private CineClientSetup() {
    }

    @SubscribeEvent
    public static void itemRenderer(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CineCameraItemRenderer.get();
            }
        }, ModBlocks.CINE_CAMERA.asItem());
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(RECORD);
    }

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, CineOverlay::render);
    }
}
