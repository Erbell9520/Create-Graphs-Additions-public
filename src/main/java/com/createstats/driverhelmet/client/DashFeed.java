package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.driverhelmet.DashLine;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class DashFeed {
    static final double STALE_TICKS = 20.0;

    @Nullable
    private static DashLine latest;
    @Nullable
    private static DashLine previous;
    private static double latestAt = Double.NEGATIVE_INFINITY;

    private DashFeed() {
    }

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay()) {
            return;
        }
        final DashLine line = DashLine.parse(event.getMessage().getString());
        if (line == null) {
            return;
        }
        previous = latest;
        latest = line;
        latestAt = HelmetView.now();
        if (DriverHelmetHud.showing()) {
            event.setCanceled(true);
        }
    }

    @Nullable
    static DashLine current() {
        return latest != null && HelmetView.now() - latestAt < STALE_TICKS ? latest : null;
    }

    static double smoothKmh() {
        if (latest == null) {
            return 0.0;
        }
        if (previous == null) {
            return latest.kmh();
        }
        final double t = Math.max(0.0, Math.min(1.0, (HelmetView.now() - latestAt) / 5.0));
        return previous.kmh() + (latest.kmh() - previous.kmh()) * t;
    }
}
