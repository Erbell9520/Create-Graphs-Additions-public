package com.createstats.pducabinet.client;

import com.createstats.registry.ModBlocks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid="create_stats",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CabinetClientSetup {
    private CabinetClientSetup() {}
    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return CabinetItemRenderer.get(); }
        },ModBlocks.PDU_CABINET.asItem());
    }
    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)manager->{CabinetMesh.clear();CabinetTextures.clear();});
    }
}
