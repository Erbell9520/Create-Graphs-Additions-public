package com.createstats.gui.weather;

import com.createstats.weather.Condition;
import com.createstats.weather.Forecast;
import com.createstats.weather.ForecastEngine;
import net.minecraft.client.gui.GuiGraphics;

// solid = known, fading gradient = we stop knowing here. no legend needed
public final class NextStrip {
    private static final int BAR_H = 12;

    private NextStrip() {
    }

    public static void render(GuiGraphics g, int x, int y, int w, int h, WeatherContext ctx) {
        final WeatherTheme.Palette p = ctx.palette();
        WeatherTheme.text(g, ctx.font(), "NEXT", x, y, WeatherTheme.SECTION,
                WeatherTheme.alpha(p.inkDim(), 0.9f));

        final Forecast forecast = ctx.forecast();
        if (forecast.bands().isEmpty()) {
            WeatherTheme.text(g, ctx.font(), noForecastReason(ctx), x + 34, y,
                    WeatherTheme.BODY, p.inkDim());
            return;
        }

        WeatherTheme.text(g, ctx.font(), headline(ctx), x + 34, y, WeatherTheme.BODY, p.ink());

        final int barY = y + 13;

        WeatherTheme.pill(g, x, barY, w, BAR_H, WeatherTheme.alpha(p.ink(), 0.10f));

        final float elapsed = ctx.elapsedSinceSync();
        for (Forecast.Band band : forecast.bands()) {
            drawBand(g, x, barY, w, band, elapsed, p);
        }

        g.fill(x, barY - 2, x + 1, barY + BAR_H + 2, p.accent());

        hourTicks(g, x, barY + BAR_H + 2, w, ctx, p);
    }

    private static void drawBand(GuiGraphics g, int x, int barY, int w, Forecast.Band band,
                                 float elapsed, WeatherTheme.Palette p) {
        final float a = (band.startTick() - elapsed) / (float) ForecastEngine.STRIP_TICKS;
        final float b = (band.endTick() - elapsed) / (float) ForecastEngine.STRIP_TICKS;
        final int x0 = Math.round(x + w * Math.max(0f, Math.min(1f, a)));
        final int x1 = Math.round(x + w * Math.max(0f, Math.min(1f, b)));
        if (x1 - x0 < 1) {
            return;
        }

        final int colour = band.condition() == Condition.CLEAR
                ? WeatherTheme.alpha(p.inkDim(), 0.55f)
                : p.accent();

        if (band.certain()) {
            g.fill(x0, barY, x1, barY + BAR_H, colour);
        } else {
            for (int px = x0; px < x1; px++) {
                final float t = (px - x0) / (float) Math.max(1, x1 - x0);
                g.fill(px, barY, px + 1, barY + BAR_H,
                        WeatherTheme.alpha(colour, 0.65f * (1f - t)));
            }
        }
    }

    private static void hourTicks(GuiGraphics g, int x, int y, int w,
                                  WeatherContext ctx, WeatherTheme.Palette p) {
        for (int hour = 0; hour <= 12; hour += 3) {
            final int px = Math.round(x + w * (hour / 12f));
            g.fill(px, y, px + 1, y + 2, WeatherTheme.alpha(p.inkDim(), 0.5f));
            final String label = hour == 0 ? "now" : "+" + hour + "h";
            WeatherTheme.text(g, ctx.font(), label,
                    hour == 12 ? px - WeatherTheme.textW(ctx.font(), label, WeatherTheme.CAPTION) : px + 2,
                    y + 3, WeatherTheme.CAPTION, WeatherTheme.alpha(p.inkDim(), 0.75f));
        }
    }

    public static String headline(WeatherContext ctx) {
        final Forecast forecast = ctx.forecast();
        final int next = ctx.ticksToNextChange();
        final boolean raining = ctx.panel().condition() != Condition.CLEAR
                && ctx.panel().condition() != Condition.NONE;

        if (next < 0) {
            return "Weather cycle is off";
        }
        if (next > ForecastEngine.STRIP_TICKS && forecast.certainUntilTicks() >= next) {
            return (raining ? "Wet for " : "Clear for ") + spell(next);
        }
        return (raining ? "Clearing in " : "Rain in ") + formatTicks(next);
    }

    private static String noForecastReason(WeatherContext ctx) {
        final var snapshot = ctx.panel().snapshot();
        if (snapshot == null) {
            return "Taking a reading";
        }
        return snapshot.hasSkyLight() ? "Weather cycle is off" : "No sky here";
    }

    private static String spell(int ticks) {
        final int days = ticks / 24000;
        if (days >= 1) {
            return days + (days == 1 ? " day" : " days");
        }
        return formatTicks(ticks);
    }

    public static String formatTicks(int ticks) {
        if (ticks < 0) {
            return "--";
        }
        final int minutes = (int) (ticks / (24000.0 / 1440.0));
        if (minutes >= 60) {
            return (minutes / 60) + "h " + String.format("%02dm", minutes % 60);
        }
        if (minutes >= 1) {
            return minutes + "m";
        }
        return Math.max(1, ticks / 20) + "s";
    }
}
