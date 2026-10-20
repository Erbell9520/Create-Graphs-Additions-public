package com.createstats.power;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// the appliance says what it's doing, the panel only knew the switch
public record ApplianceStatus(Component text, float progress, boolean running, @Nullable Component doneText) {
    public static final float NO_PROGRESS = -1f;

    public ApplianceStatus {
        if (progress != NO_PROGRESS) {
            progress = Math.max(0f, Math.min(1f, progress));
        }
    }

    public static ApplianceStatus idle(Component text) {
        return new ApplianceStatus(text, NO_PROGRESS, false, null);
    }

    public static ApplianceStatus running(Component text, int total, int left, @Nullable Component done) {
        final float progress = total > 0 ? 1f - Math.max(0, Math.min(total, left)) / (float) total : NO_PROGRESS;
        return new ApplianceStatus(text, progress, true, done);
    }

    public static String clock(int ticks) {
        final int seconds = Math.max(0, (ticks + 19) / 20);
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }
}
