package com.createstats.gui.weather;

import com.createstats.weather.Condition;
import com.createstats.weather.Forecast;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// today shows a time, its odds are 0 or 100 and printing that teaches you the % are meaningless
public final class DayStrip {
    private static final ResourceLocation GLYPHS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/weather_glyphs.png");
    private static final int GLYPH_COUNT = 6;
    private static final int GLYPH_PX = 64;
    private static final int GLYPH_SIZE = 18;

    private static final int ROW_LABEL = 4;
    private static final int ROW_GLYPH = 12;
    private static final int ROW_MIDDLE = 31;
    private static final int ROW_RANGE = 39;
    private static final int ROW_BAR = 47;

    private DayStrip() {
    }

    public static void render(GuiGraphics g, int x, int y, int w, int h, WeatherContext ctx) {
        final WeatherTheme.Palette p = ctx.palette();
        WeatherTheme.text(g, ctx.font(), "5 DAYS", x, y, WeatherTheme.SECTION,
                WeatherTheme.alpha(p.inkDim(), 0.9f));

        final Forecast forecast = ctx.forecast();
        final var days = forecast.days();
        if (days.isEmpty()) {
            return;
        }

        float weekLow = Float.POSITIVE_INFINITY;
        float weekHigh = Float.NEGATIVE_INFINITY;
        for (Forecast.DayCell d : days) {
            weekLow = Math.min(weekLow, d.lowC());
            weekHigh = Math.max(weekHigh, d.highC());
        }
        if (weekHigh - weekLow < 0.5f) {
            weekHigh = weekLow + 0.5f;
        }

        final int top = y + 12;
        final int cellH = h - 12;

        final float shift = ctx.anim().dayShift() * (WeatherTheme.DAY_CELL_W + WeatherTheme.DAY_GAP);

        g.enableScissor(x, top, x + w, top + cellH);
        for (int i = 0; i < days.size(); i++) {
            final int cellX = Math.round(x + i * (WeatherTheme.DAY_CELL_W + WeatherTheme.DAY_GAP) + shift);
            cell(g, cellX, top, WeatherTheme.DAY_CELL_W, cellH, days.get(i), i,
                    weekLow, weekHigh, ctx, p);
        }
        g.disableScissor();
    }

    private static void cell(GuiGraphics g, int x, int y, int w, int h, Forecast.DayCell day,
                             int index, float weekLow, float weekHigh,
                             WeatherContext ctx, WeatherTheme.Palette p) {
        final boolean certain = day.certain();
        WeatherTheme.card(g, x, y, w, h, p.card(), WeatherTheme.alpha(p.ink(), 0.10f));

        WeatherTheme.textCentre(g, ctx.font(), label(day, index), x + w / 2f, y + ROW_LABEL,
                WeatherTheme.CAPTION, p.inkDim());

        blitGlyph(g, day.condition(), x + (w - GLYPH_SIZE) / 2, y + ROW_GLYPH, p.accent(),
                certain ? 1f : 0.70f);

        final String middle = index == 0
                ? NextStrip.formatTicks(ctx.ticksToNextChange())
                : Math.round(ctx.anim().pop(index)) + "%";
        WeatherTheme.textCentre(g, ctx.font(), middle, x + w / 2f, y + ROW_MIDDLE,
                WeatherTheme.CAPTION, index == 0 ? p.ink() : p.accent());

        final String range = Math.round(ctx.toDisplayUnits(day.highC())) + " / "
                + Math.round(ctx.toDisplayUnits(day.lowC()));
        WeatherTheme.textCentre(g, ctx.font(), range, x + w / 2f, y + ROW_RANGE,
                WeatherTheme.CAPTION, p.ink());

        rangeBar(g, x + 6, y + ROW_BAR, w - 12, day, weekLow, weekHigh, p);
    }

    private static void rangeBar(GuiGraphics g, int x, int y, int w, Forecast.DayCell day,
                                 float weekLow, float weekHigh, WeatherTheme.Palette p) {
        g.fill(x, y, x + w, y + 2, WeatherTheme.alpha(p.inkDim(), 0.20f));
        final float span = weekHigh - weekLow;
        final int a = Math.round(x + w * (day.lowC() - weekLow) / span);
        final int b = Math.round(x + w * (day.highC() - weekLow) / span);
        g.fill(Math.min(a, b), y, Math.max(a, b) + 1, y + 2, p.accent());
    }

    private static String label(Forecast.DayCell day, int index) {
        return switch (index) {
            case 0 -> "Today";
            case 1 -> "Tomorrow";
            default -> "Day " + day.dayIndex();
        };
    }

    private static void blitGlyph(GuiGraphics g, Condition condition, int x, int y,
                                  int accent, float alpha) {
        final int glyph = Math.min(GLYPH_COUNT - 1, Math.max(0, condition.ordinal()));
        g.setColor(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f,
                (accent & 0xFF) / 255f, alpha);
        g.blit(GLYPHS, x, y, GLYPH_SIZE, GLYPH_SIZE, glyph * (float) GLYPH_PX, 0f,
                GLYPH_PX, GLYPH_PX, GLYPH_PX * GLYPH_COUNT, GLYPH_PX);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
