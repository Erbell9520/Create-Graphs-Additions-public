package com.createstats.client;

import com.createstats.ModConfiguration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// in-memory override, ModConfigSpec only refreshes on reload
@OnlyIn(Dist.CLIENT)
public final class ClientGraphPrefs {
    private static Boolean signalGlowOverride = null;

    private ClientGraphPrefs() {
    }

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
