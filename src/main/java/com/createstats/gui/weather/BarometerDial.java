package com.createstats.gui.weather;

import com.createstats.weather.BarometerModel;
import net.minecraft.client.gui.GuiGraphics;

// the tendency hand sets itself to an hour ago
public final class BarometerDial {
    private static final float FROM_DEGREES = -210f;
    private static final float SPAN_DEGREES = 240f;

    private static final String[] FACE_WORDS = {"STORM", "CHANGE", "DRY"};

    private BarometerDial() {
    }

    public static void render(GuiGraphics g, int x, int y, int w, int h, WeatherContext ctx) {
        final WeatherTheme.Palette p = ctx.palette();
        WeatherTheme.card(g, x, y, w, h, p.card(), WeatherTheme.alpha(p.ink(), 0.12f));
        WeatherTheme.text(g, ctx.font(), "BAROMETER", x + WeatherTheme.CARD_PAD,
                y + 7, WeatherTheme.SECTION, WeatherTheme.alpha(p.inkDim(), 0.9f));

        final float value = ctx.anim().pressure();
        if (Float.isNaN(value) || value <= 0f) {
            WeatherTheme.textCentre(g, ctx.font(), "--", x + w / 2f, y + h / 2f - 4,
                    2.0f, p.inkDim());
            return;
        }

        final float cx = x + w / 2f;
        final float cy = y + 54f;
        final float radius = 32f;

        ticks(g, cx, cy, radius, p);
        faceWords(g, cx, cy, radius, ctx, p);

        final float previous = ctx.panel().pressureOneHourAgo();
        final float tendency = Float.isNaN(previous) ? value : previous;
        wedge(g, cx, cy, radius, tendency, value, p);
        hand(g, cx, cy, radius - 8f, tendency, 1f, WeatherTheme.alpha(p.ink(), 0.40f));
        hand(g, cx, cy, radius - 5f, value, 2f, p.ink());
        WeatherTheme.disc(g, cx, cy, 2.5f, p.ink());

        WeatherTheme.textCentre(g, ctx.font(), Math.round(value) + " hPa", cx, y + h - 25,
                1.5f, p.ink());
        WeatherTheme.textCentre(g, ctx.font(), trend(value, tendency), cx, y + h - 11,
                WeatherTheme.CAPTION, p.inkDim());
    }

    private static float angleOf(float hPa) {
        final float t = (hPa - BarometerModel.MIN) / (BarometerModel.MAX - BarometerModel.MIN);
        return FROM_DEGREES + SPAN_DEGREES * Math.max(0f, Math.min(1f, t));
    }

    private static void ticks(GuiGraphics g, float cx, float cy, float r, WeatherTheme.Palette p) {
        for (int hPa = (int) BarometerModel.MIN; hPa <= (int) BarometerModel.MAX; hPa += 10) {
            final boolean major = hPa % 20 == 0;
            final float a = (float) Math.toRadians(angleOf(hPa));
            final float inner = r - (major ? 6f : 3f);
            plot(g, cx, cy, a, inner, r,
                    WeatherTheme.alpha(p.inkDim(), major ? 0.85f : 0.45f));
        }
    }

    private static void plot(GuiGraphics g, float cx, float cy, float angle,
                             float from, float to, int colour) {
        final float dx = (float) Math.cos(angle);
        final float dy = (float) Math.sin(angle);
        for (float d = from; d <= to; d += 0.5f) {
            final int px = Math.round(cx + dx * d);
            final int py = Math.round(cy + dy * d);
            g.fill(px, py, px + 1, py + 1, colour);
        }
    }

    private static void faceWords(GuiGraphics g, float cx, float cy, float r,
                                  WeatherContext ctx, WeatherTheme.Palette p) {
        for (int i = 0; i < FACE_WORDS.length; i++) {
            final float t = FACE_WORDS.length == 1 ? 0.5f : i / (float) (FACE_WORDS.length - 1);
            final float a = (float) Math.toRadians(FROM_DEGREES + SPAN_DEGREES * t);
            final float px = cx + (float) Math.cos(a) * (r - 12f);
            final float py = cy + (float) Math.sin(a) * (r - 12f);
            WeatherTheme.textCentre(g, ctx.font(), FACE_WORDS[i], px, py - 2,
                    WeatherTheme.CAPTION, WeatherTheme.alpha(p.inkDim(), 0.35f));
        }
    }

    private static void wedge(GuiGraphics g, float cx, float cy, float r,
                              float from, float to, WeatherTheme.Palette p) {
        final float a0 = angleOf(Math.min(from, to));
        final float a1 = angleOf(Math.max(from, to));
        if (a1 - a0 < 0.6f) {
            return;
        }
        final int colour = WeatherTheme.alpha(to < from ? p.accent() : p.inkDim(), 0.25f);
        for (float a = a0; a <= a1; a += 0.8f) {
            plot(g, cx, cy, (float) Math.toRadians(a), r - 6f, r - 1f, colour);
        }
    }

    private static void hand(GuiGraphics g, float cx, float cy, float length,
                             float hPa, float weight, int colour) {
        final float a = (float) Math.toRadians(angleOf(hPa));
        final float dx = (float) Math.cos(a);
        final float dy = (float) Math.sin(a);
        for (float d = 0; d <= length; d += 0.4f) {
            final float taper = weight * (1f - 0.55f * (d / length));
            final int px = Math.round(cx + dx * d);
            final int py = Math.round(cy + dy * d);
            final int half = Math.max(0, Math.round(taper / 2f));
            g.fill(px - half, py - half, px + half + 1, py + half + 1, colour);
        }
    }

    private static String trend(float now, float before) {
        final float delta = now - before;
        if (Math.abs(delta) < 0.75f) {
            return "steady";
        }
        return (delta < 0 ? "falling, " : "rising, ")
                + (delta > 0 ? "+" : "") + Math.round(delta) + " in 1h";
    }
}
