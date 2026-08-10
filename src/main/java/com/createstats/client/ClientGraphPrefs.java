package com.createstats.client;

import com.createstats.ModConfiguration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** [1] */
@OnlyIn(Dist.CLIENT)
public final class ClientGraphPrefs {

    private static Boolean signalGlowOverride = null;

    private ClientGraphPrefs() {
    }

    /** Whether the soft phosphor glow is drawn around line-chart traces. */
    public static boolean signalGlow() {
        if (signalGlowOverride != null) {
            return signalGlowOverride;
        }
        try {
            return ModConfiguration.CLIENT.signalTraceGlow.get();
        }
        catch (Throwable ignored) {
            return true;
        }
    }

    /** Flips the glow preference; effective immediately and persisted best-effort to the config. */
    public static void toggleSignalGlow() {
        final boolean v = !signalGlow();
        signalGlowOverride = v;
        try {
            ModConfiguration.CLIENT.signalTraceGlow.set(v);
        }
        catch (Throwable ignored) {
        }
    }
}
