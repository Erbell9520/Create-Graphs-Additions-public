package com.createstats.web;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

import com.createstats.blockentity.StatsDisplayPanelBE;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class WebFeed {
    private static final int TICKS_PER_REBUILD = 5;

    private static final AtomicReference<String> LATEST = new AtomicReference<>("");

    private static volatile WeakReference<StatsDisplayPanelBE> panel = new WeakReference<>(null);
    private static volatile String panelLabel = "";
    private static long startedAtNanos;
    private static int ticks;

    private WebFeed() {
    }

    public static void bind(StatsDisplayPanelBE be, String label) {
        panel = new WeakReference<>(be);
        panelLabel = label == null ? "" : label;
        startedAtNanos = System.nanoTime();
        ticks = 0;
        LATEST.set(be == null
                ? WebSnapshot.unavailable("No panel is bound to the web interface.", 0L)
                : WebSnapshot.of(be, 0L));
    }

    public static void clear() {
        panel = new WeakReference<>(null);
        panelLabel = "";
        LATEST.set("");
        ticks = 0;
    }

    public static String label() {
        return panelLabel;
    }

    public static void tick() {
        if (++ticks < TICKS_PER_REBUILD) {
            return;
        }
        ticks = 0;
        publish();
    }

    public static String json() {
        final String latest = LATEST.get();
        return latest == null || latest.isEmpty()
                ? WebSnapshot.unavailable("The interface is starting.", 0L)
                : latest;
    }

    private static void publish() {
        final StatsDisplayPanelBE be = panel.get();
        final long uptime = startedAtNanos == 0L
                ? 0L
                : (System.nanoTime() - startedAtNanos) / 1_000_000L;
        LATEST.set(be == null
                ? WebSnapshot.unavailable("The panel this was serving is no longer loaded.", uptime)
                : WebSnapshot.of(be, uptime));
    }
}
