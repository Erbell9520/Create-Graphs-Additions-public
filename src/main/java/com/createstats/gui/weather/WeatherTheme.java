package com.createstats.gui.weather;

import com.createstats.gui.UiFx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.createstats.weather.Condition;

public final class WeatherTheme {
    private WeatherTheme() {
    }

    public record Palette(int skyTop, int skyBottom, int ground, int card,
                          int ink, int inkDim, int accent) {
    }

    public static final Palette CLEAR_DAY = new Palette(
            0xFF7FB6E8, 0xFFCFE6F7, 0xFFF7F9FB, 0xFFFFFFFF, 0xFF16202B, 0xFF5D6B7A, 0xFFF0A93B);
    public static final Palette CLEAR_NIGHT = new Palette(
            0xFF101A2E, 0xFF22304C, 0xFF10151F, 0xFF171E2B, 0xFFE6ECF5, 0xFF8896AB, 0xFFC7D4E8);
    public static final Palette RAIN = new Palette(
            0xFF4E6274, 0xFF8FA3B2, 0xFFF0F3F5, 0xFFFFFFFF, 0xFF1A242C, 0xFF5A6874, 0xFF2E93A8);
    public static final Palette STORM = new Palette(
            0xFF1E232B, 0xFF444C57, 0xFF14171C, 0xFF1C2027, 0xFFE8ECF1, 0xFF8590A0, 0xFF8A7FE0);
    public static final Palette SNOW = new Palette(
            0xFF9FB4C8, 0xFFE4EDF4, 0xFFF6F9FB, 0xFFFFFFFF, 0xFF1C2630, 0xFF5F6E7C, 0xFF5FA9C9);

    public static final Palette DRY = new Palette(
            0xFF8AAED0, 0xFFDADFE2, 0xFFF7F9FB, 0xFFFFFFFF, 0xFF16202B, 0xFF5D6B7A, 0xFF2E93A8);

    public static final Palette NO_SKY = new Palette(
            0xFF2A2D33, 0xFF4A4F59, 0xFF15171A, 0xFF1D2025, 0xFFE4E7EC, 0xFF868C97, 0xFF5A5F6A);

    public static final int AMBER = 0xFFE0A03A;

    public static final int PAGE_W = 320;
    public static final int PAGE_H = 256;
    public static final int PAD = 8;
    public static final int GAP = 8;
    public static final int CARD_PAD = 10;
    public static final int RADIUS = 6;

    public static final int CONTENT_W = PAGE_W - PAD * 2;
    public static final int HEADER_H = 22;
    public static final int HERO_H = 104;
    public static final int SKY_W = 184;
    public static final int BARO_W = CONTENT_W - SKY_W - GAP;
    public static final int NEXT_H = 38;
    public static final int DAYS_H = 62;
    public static final int DAY_CELLS = 5;
    public static final int DAY_GAP = 6;
    public static final int DAY_CELL_W = (CONTENT_W - DAY_GAP * (DAY_CELLS - 1)) / DAY_CELLS;

    public static final int HEADER_Y = PAD;
    public static final int HERO_Y = HEADER_Y + HEADER_H;
    public static final int NEXT_Y = HERO_Y + HERO_H + GAP;
    public static final int DAYS_Y = NEXT_Y + NEXT_H + GAP;

    public static final int HORIZON_H = 26;

    public static float surfaceMix(float t) {
        return UiFx.clamp01((UiFx.clamp01(t) - 0.5f) * SURFACE_SNAP + 0.5f);
    }

    private static final float SURFACE_SNAP = 16f;

    public static Palette paletteFor(Condition condition, float nightness) {
        if (condition == Condition.NONE) {
            return NO_SKY;
        }
        final float n = UiFx.clamp01(nightness);
        final Palette day = dayPalette(condition);
        final Palette night = nightSkyOf(day, condition);
        final float chrome = surfaceMix(n);
        return guard(new Palette(
                UiFx.lerpColor(day.skyTop(), night.skyTop(), n),
                UiFx.lerpColor(day.skyBottom(), night.skyBottom(), n),
                UiFx.lerpColor(day.ground(), NIGHT_CHROME.ground(), chrome),
                UiFx.lerpColor(day.card(), NIGHT_CHROME.card(), chrome),
                UiFx.lerpColor(day.ink(), NIGHT_CHROME.ink(), chrome),
                UiFx.lerpColor(day.inkDim(), NIGHT_CHROME.inkDim(), chrome),
                UiFx.lerpColor(day.accent(), night.accent(), n)));
    }

    public static Palette crossfade(Palette a, Palette b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        final float chrome = surfaceMix(t);
        return new Palette(
                UiFx.lerpColor(a.skyTop(), b.skyTop(), t),
                UiFx.lerpColor(a.skyBottom(), b.skyBottom(), t),
                UiFx.lerpColor(a.ground(), b.ground(), chrome),
                UiFx.lerpColor(a.card(), b.card(), chrome),
                UiFx.lerpColor(a.ink(), b.ink(), chrome),
                UiFx.lerpColor(a.inkDim(), b.inkDim(), chrome),
                UiFx.lerpColor(a.accent(), b.accent(), t));
    }

    private static final Palette NIGHT_CHROME = CLEAR_NIGHT;

    private static Palette dayPalette(Condition condition) {
        return switch (condition) {
            case CLEAR -> CLEAR_DAY;
            case RAIN -> RAIN;
            case STORM -> STORM;
            case SNOW -> SNOW;
            case DRY -> DRY;
            case NONE -> NO_SKY;
        };
    }

    private static Palette nightSkyOf(Palette day, Condition condition) {
        if (condition == Condition.CLEAR) {
            return CLEAR_NIGHT;
        }
        final float amount = switch (condition) {
            case RAIN, DRY -> 0.55f;
            case SNOW -> 0.50f;
            case STORM -> 0.25f;
            default -> 0f;
        };
        return new Palette(
                UiFx.lerpColor(day.skyTop(), CLEAR_NIGHT.skyTop(), amount),
                UiFx.lerpColor(day.skyBottom(), CLEAR_NIGHT.skyBottom(), amount),
                day.ground(), day.card(), day.ink(), day.inkDim(), day.accent());
    }

    public static final float HERO = 3.0f;
    public static final float SECTION = 1.0f;
    public static final float BODY = 1.0f;
    public static final float CAPTION = 0.75f;

    public static void text(GuiGraphics g, Font font, String s, float x, float y,
                            float scale, int colour) {
        if (s == null || s.isEmpty()) {
            return;
        }
        if (scale == 1.0f) {
            g.drawString(font, s, x, y, colour, false);
            return;
        }
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, s, x / scale, y / scale, colour, false);
        g.pose().popPose();
    }

    public static void textRight(GuiGraphics g, Font font, String s, float right, float y,
                                 float scale, int colour) {
        text(g, font, s, right - textW(font, s, scale), y, scale, colour);
    }

    public static void textCentre(GuiGraphics g, Font font, String s, float centreX, float y,
                                  float scale, int colour) {
        text(g, font, s, centreX - textW(font, s, scale) / 2f, y, scale, colour);
    }

    public static float textW(Font font, String s, float scale) {
        return s == null ? 0f : font.width(s) * scale;
    }

    public static void card(GuiGraphics g, int x, int y, int w, int h, int fill, int line) {
        g.fill(x + 1, y, x + w - 1, y + h, fill);
        g.fill(x, y + 1, x + 1, y + h - 1, fill);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, fill);
        outline(g, x, y, w, h, line);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x + 1, y, x + w - 1, y + 1, colour);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    public static void pill(GuiGraphics g, int x, int y, int w, int h, int colour) {
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, colour);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int colour) {
        if (r <= 0f) {
            return;
        }
        final int top = (int) Math.floor(cy - r);
        final int bottom = (int) Math.ceil(cy + r);
        for (int y = top; y < bottom; y++) {
            final float dy = y + 0.5f - cy;
            final float half = r * r - dy * dy;
            if (half <= 0f) {
                continue;
            }
            final float run = (float) Math.sqrt(half);
            g.fill(Math.round(cx - run), y, Math.round(cx + run), y + 1, colour);
        }
    }

    public static int alpha(int argb, float factor) {
        final int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static final float INK_FLOOR = 9.0f;

    public static final float DIM_FLOOR = 4.5f;

    public static final float ACCENT_FLOOR = 1.9f;

    public static float luminance(int argb) {
        return 0.2126f * channel((argb >> 16) & 0xFF)
                + 0.7152f * channel((argb >> 8) & 0xFF)
                + 0.0722f * channel(argb & 0xFF);
    }

    private static float channel(int value) {
        final float c = value / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    public static float contrast(int a, int b) {
        final float la = luminance(a);
        final float lb = luminance(b);
        return (Math.max(la, lb) + 0.05f) / (Math.min(la, lb) + 0.05f);
    }

    public static int readableOn(int ink, int surface, float minRatio) {
        return readableOn(ink, surface, surface, minRatio);
    }

    public static int readableOn(int ink, int surfaceA, int surfaceB, float minRatio) {
        if (worstContrast(ink, surfaceA, surfaceB) >= minRatio) {
            return ink;
        }
        final int black = ink & 0xFF000000;
        final int white = black | 0xFFFFFF;
        final float bestBlack = worstContrast(black, surfaceA, surfaceB);
        final float bestWhite = worstContrast(white, surfaceA, surfaceB);

        final boolean bothReach = bestBlack >= minRatio && bestWhite >= minRatio;
        final int target = bothReach
                ? (luminance(surfaceA) + luminance(surfaceB) > 0.36f ? black : white)
                : (bestBlack >= bestWhite ? black : white);

        float lo = 0f;
        float hi = 1f;
        for (int i = 0; i < 12; i++) {
            final float mid = (lo + hi) * 0.5f;
            if (worstContrast(UiFx.lerpColor(ink, target, mid), surfaceA, surfaceB) >= minRatio) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return UiFx.lerpColor(ink, target, hi);
    }

    private static float worstContrast(int ink, int a, int b) {
        return Math.min(contrast(ink, a), contrast(ink, b));
    }

    public static Palette guard(Palette p) {
        final int ink = readableOn(p.ink(), p.card(), p.ground(), INK_FLOOR);
        final int dim = readableOn(p.inkDim(), p.card(), p.ground(), DIM_FLOOR);
        final int accent = readableOn(p.accent(), p.card(), p.card(), ACCENT_FLOOR);
        return new Palette(p.skyTop(), p.skyBottom(), p.ground(), p.card(), ink, dim, accent);
    }
}
