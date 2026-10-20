package com.createstats.event;

import com.createstats.CreateStats;
import com.createstats.web.LocalWebServer;
import com.createstats.web.WebFeed;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class WebInterfaceClientEvents {
    private WebInterfaceClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LocalWebServer.get().stop();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (LocalWebServer.get().isRunning()) {
            WebFeed.tick();
        }
    }
}
