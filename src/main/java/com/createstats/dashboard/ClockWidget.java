package com.createstats.dashboard;

import java.time.LocalTime;
import com.createstats.renderer.BitmapFont;
import com.createstats.display.DisplayTheme;
import com.mojang.blaze3d.platform.NativeImage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClockWidget
{
    public static final int FORMAT_GAME_TIME = 0;
    public static final int FORMAT_GAME_TIME_12 = 1;
    public static final int FORMAT_REAL_TIME = 2;
    public static final int FORMAT_UPTIME = 3;
    public static final int FORMAT_HIDDEN = 4;

    private ClockWidget() {
    }

    public static void render(NativeImage image, int x, int y, int w, int h, long dayTime, int format, DisplayTheme theme) {
        if (format == 4) {
            return;
        }
        final String timeText = switch (format) {
            case 1 -> formatMinecraftTime12H(dayTime);
            case 2 -> formatRealTime();
            case 3 -> "Up " + formatUptime(0L, dayTime);
            default -> formatMinecraftTime(dayTime);
        };
        final int scale = Math.max(1, Math.min(2, h / 16));
        final int textH = BitmapFont.textHeight(scale);
        final int textY = y + (h - textH) / 2;
        BitmapFont.drawCentered(image, timeText, x, textY, w, theme.accent, scale);
    }

    public static String formatMinecraftTime(long dayTime) {
        final long timeOfDay = dayTime % 24000L;
        final int hours = (int)((timeOfDay / 1000L + 6L) % 24L);
        final int minutes = (int)(timeOfDay % 1000L * 60L / 1000L);
        final long day = dayTime / 24000L + 1L;
        return String.format("Day %d, %02d:%02d", day, hours, minutes);
    }

    public static String formatMinecraftTime12H(long dayTime) {
        final long timeOfDay = dayTime % 24000L;
        final int hours24 = (int)((timeOfDay / 1000L + 6L) % 24L);
        final int minutes = (int)(timeOfDay % 1000L * 60L / 1000L);
        final long day = dayTime / 24000L + 1L;
        final String ampm = (hours24 >= 12) ? "PM" : "AM";
        int hours25 = hours24 % 12;
        if (hours25 == 0) {
            hours25 = 12;
        }
        return String.format("Day %d, %d:%02d %s", day, hours25, minutes, ampm);
    }

    public static String formatRealTime() {
        final LocalTime now = LocalTime.now();
        return String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());
    }

    public static String formatUptime(long startTick, long currentTick) {
        final long seconds = (currentTick - startTick) / 20L;
        if (seconds < 60L) {
            return "" + seconds;
        }
        if (seconds < 3600L) {
            return seconds / 60L + "m " + seconds % 60L;
        }
        return seconds / 3600L + "h " + seconds % 3600L / 60L;
    }
}
