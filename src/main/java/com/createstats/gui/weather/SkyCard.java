package com.createstats.gui.weather;

import com.createstats.gui.UiFx;
import com.createstats.weather.ClimateModel;
import com.createstats.weather.Condition;
import com.createstats.weather.WeatherHistory;
import com.createstats.weather.WeatherSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class SkyCard {
    private static final ResourceLocation GLYPHS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/weather_glyphs.png");
    private static final int GLYPH_COUNT = 6;
    private static final int GLYPH_PX = 64;

    private static final int CURVE_SAMPLES = 46;
    private static final int STARS = 44;
    private static final int CLOUDS = 5;

    private static final int HERO_TALL = 27;

    private SkyCard() {
    }

    public static void render(GuiGraphics g, int x, int y, int w, int h, WeatherContext ctx) {
        final WeatherTheme.Palette p = ctx.palette();
        final WeatherSnapshot snap = ctx.panel().snapshot();

        g.enableScissor(x, y, x + w, y + h);
        g.fillGradient(x, y, x + w, y + h, p.skyTop(), p.skyBottom());

        if (snap == null || snap.condition() == Condition.NONE) {
            g.disableScissor();
            WeatherTheme.outline(g, x, y, w, h, WeatherTheme.alpha(p.ink(), 0.12f));
            WeatherTheme.text(g, ctx.font(), snap == null ? "Taking a reading" : "No sky here.",
                    x + WeatherTheme.CARD_PAD, y + h / 2 - 12, 1.5f, p.ink());
            if (snap != null) {
                WeatherTheme.text(g, ctx.font(), "This dimension has no weather to report.",
                        x + WeatherTheme.CARD_PAD, y + h / 2 + 6, WeatherTheme.BODY, p.inkDim());
            }
            return;
        }

        final float dayFraction = snap.dayFraction();

        final int horizonTop = y + h - WeatherTheme.HORIZON_H;

        final var level = ctx.panel().getLevel();
        final float time = level == null ? 0f : level.getGameTime() + ctx.partialTick();

        stars(g, x, y, w, horizonTop, ctx.nightness(), time);
        celestialBody(g, x, y, w, horizonTop, dayFraction, ctx, p);
        clouds(g, x, y, w, horizonTop, ctx, time, p);
        precipitation(g, x, y, w, horizonTop, ctx, time, p);
        horizon(g, x, horizonTop, w, WeatherTheme.HORIZON_H, ctx, p);
        nowHairline(g, x, y, w, h, dayFraction, p);
        readout(g, x, y, h, ctx, p);
        glyph(g, x, y, w, ctx, p);

        g.disableScissor();
        WeatherTheme.outline(g, x, y, w, h, WeatherTheme.alpha(p.ink(), 0.12f));
    }

    private static float timeToX(int x, int w, float dayFraction) {
        return x + w * dayFraction;
    }

    private static void stars(GuiGraphics g, int x, int y, int w, int horizonTop,
                              float nightness, float time) {
        if (nightness <= 0.05f) {
            return;
        }
        final int height = horizonTop - y;
        for (int i = 0; i < STARS; i++) {
            final float sx = x + ((i * 89.7f) % 1.0f) * w;
            final float sy = y + ((i * 43.3f) % 1.0f) * height * 0.8f;
            final float twinkle = 0.55f + 0.45f * UiFx.pulse01(time * 0.04f + i * 7.3f, 1.0f);
            g.fill(Math.round(sx), Math.round(sy), Math.round(sx) + 1, Math.round(sy) + 1,
                    WeatherTheme.alpha(0xFFFFFFFF, nightness * twinkle * 0.9f));
        }
    }

    private static void celestialBody(GuiGraphics g, int x, int y, int w, int horizonTop,
                                      float dayFraction, WeatherContext ctx,
                                      WeatherTheme.Palette p) {
        final float cx = timeToX(x, w, dayFraction);
        final float altitude = -(float) Math.cos(dayFraction * Math.PI * 2.0);
        final int height = horizonTop - y;
        final float cy = horizonTop - altitude * height * 0.62f - height * 0.10f;

        final float clarity = 1f - 0.75f * ctx.anim().cloudiness();
        if (ctx.nightness() > 0.5f) {
            final int silver = WeatherTheme.alpha(0xFFE8EEF8, clarity);
            WeatherTheme.disc(g, cx, cy, 8f, silver);

            final int phase = moonPhase(ctx);
            final float offset = (phase - 4) * 4.2f;
            if (phase != 0) {
                WeatherTheme.disc(g, cx + offset, cy, 8f, WeatherTheme.alpha(p.skyTop(), clarity));
            }
        } else {
            WeatherTheme.disc(g, cx, cy, 13f, WeatherTheme.alpha(p.accent(), clarity * 0.22f));
            WeatherTheme.disc(g, cx, cy, 8f, WeatherTheme.alpha(p.accent(), clarity));
        }
    }

    private static int moonPhase(WeatherContext ctx) {
        final var level = ctx.panel().getLevel();
        if (level == null) {
            return 0;
        }
        return level.dimensionType().moonPhase(level.getDayTime());
    }

    private static void clouds(GuiGraphics g, int x, int y, int w, int horizonTop,
                               WeatherContext ctx, float time, WeatherTheme.Palette p) {
        final float cover = ctx.anim().cloudiness();
        if (cover <= 0.02f) {
            return;
        }
        final int height = horizonTop - y;
        final int body = WeatherTheme.alpha(UiFx.lerpColor(0xFFFFFFFF, p.skyTop(), 0.35f),
                Math.min(0.92f, cover * 0.95f));

        for (int i = 0; i < CLOUDS; i++) {
            final float seed = (i * 61.7f) % 1.0f;
            final float span = w + 80f;
            final float cx = x - 40f + ((time * 0.055f) + seed * span) % span;
            final float cy = y + height * (0.14f + 0.42f * ((i * 29.1f) % 1.0f));
            final float r = 7f + 5f * ((i * 17.9f) % 1.0f);
            WeatherTheme.disc(g, cx - r * 0.9f, cy + r * 0.25f, r * 0.75f, body);
            WeatherTheme.disc(g, cx, cy, r, body);
            WeatherTheme.disc(g, cx + r * 1.0f, cy + r * 0.3f, r * 0.68f, body);
        }
    }

    private static void precipitation(GuiGraphics g, int x, int y, int w, int horizonTop,
                                      WeatherContext ctx, float time, WeatherTheme.Palette p) {
        final Condition condition = ctx.panel().condition();
        if (condition != Condition.RAIN && condition != Condition.STORM && condition != Condition.SNOW) {
            return;
        }
        final int count = Math.round(58 * ctx.anim().rainLevel());
        if (count <= 0) {
            return;
        }
        final boolean snow = condition == Condition.SNOW;
        final int height = horizonTop - y;
        final float speed = snow ? 0.35f : 2.1f;
        final int colour = snow
                ? WeatherTheme.alpha(0xFFFFFFFF, 0.9f)
                : WeatherTheme.alpha(UiFx.lerpColor(p.ink(), 0xFFFFFFFF, 0.35f), 0.34f);

        for (int i = 0; i < count; i++) {
            final float seed = (i * 31.7f) % 1.0f;
            final float drift = snow ? (float) Math.sin(time * 0.02f + i) * 5f : 0f;
            final float px = x + ((seed * w) + drift + w) % w;
            final float fall = (((time * speed) + seed * height) % height) / height;
            final float py = y + fall * height;
            final float fade = Math.min(UiFx.clamp01(fall / 0.10f), UiFx.clamp01((1f - fall) / 0.20f));
            final int faded = WeatherTheme.alpha(colour, fade);
            if (snow) {
                g.fill(Math.round(px), Math.round(py), Math.round(px) + 2, Math.round(py) + 2, faded);
            } else {
                g.fill(Math.round(px), Math.round(py), Math.round(px) + 1, Math.round(py) + 5, faded);
            }
        }
    }

    private static void horizon(GuiGraphics g, int x, int top, int w, int h,
                                WeatherContext ctx, WeatherTheme.Palette p) {
        final WeatherSnapshot snap = ctx.panel().snapshot();
        if (snap == null) {
            return;
        }
        final float wet = ClimateModel.wetnessOf(snap.sim());

        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        final float[] curve = new float[CURVE_SAMPLES];
        for (int i = 0; i < CURVE_SAMPLES; i++) {
            curve[i] = ClimateModel.temperatureAt(snap, i / (float) (CURVE_SAMPLES - 1), wet);
            low = Math.min(low, curve[i]);
            high = Math.max(high, curve[i]);
        }
        final float margin = Math.max(1.0f, (high - low) * 0.15f);
        final float lo = low - margin;
        final float hi = high + margin;

        final float nowX = timeToX(x, w, snap.dayFraction());
        final int fillColour = WeatherTheme.alpha(p.accent(), 0.18f);
        final int soon = WeatherTheme.alpha(p.accent(), 0.55f);

        final int[] ys = new int[Math.max(1, w)];
        for (int px = 0; px < ys.length; px++) {
            final float f = ys.length == 1 ? 0f : px / (float) (ys.length - 1);
            ys[px] = Math.round(top + h - 3 - (sample(curve, f) - lo) / (hi - lo) * (h - 6));
        }
        for (int px = 0; px < ys.length; px++) {
            g.fill(x + px, ys[px], x + px + 1, top + h, fillColour);

            final int ink = x + px <= nowX ? p.accent() : soon;
            final int from = px == 0 ? ys[px] : Math.min(ys[px - 1], ys[px]);
            final int to = (px == 0 ? ys[px] : Math.max(ys[px - 1], ys[px])) + 1;
            g.fill(x + px, from, x + px + 1, to, ink);
        }

        recorded(g, x, top, w, h, lo, hi, ctx, p);
        marker(g, x, top, w, h, curve, lo, hi, true, ctx, p);
        marker(g, x, top, w, h, curve, lo, hi, false, ctx, p);
    }

    private static float sample(float[] curve, float f) {
        final float pos = UiFx.clamp01(f) * (curve.length - 1);
        final int i = Math.min(curve.length - 2, (int) pos);
        return UiFx.lerp(curve[i], curve[i + 1], pos - i);
    }

    private static void recorded(GuiGraphics g, int x, int top, int w, int h, float lo, float hi,
                                 WeatherContext ctx, WeatherTheme.Palette p) {
        final int[] history = ctx.panel().historyTemperatureTenths();
        final WeatherSnapshot snap = ctx.panel().snapshot();
        if (history.length < 2 || snap == null) {
            return;
        }
        final long dayTime = snap.dayTime();
        final int elapsedToday = (int) (dayTime % 24000L);
        for (int i = 0; i < history.length; i++) {
            final int age = (history.length - 1 - i) * WeatherHistory.SAMPLE_TICKS;
            if (age > elapsedToday) {
                continue;
            }
            final float f = ((dayTime - age + 6000L) % 24000L) / 24000f;
            final int px = Math.round(x + w * f);
            final float value = history[i] / 10f;
            final int py = Math.round(top + h - 3 - (value - lo) / (hi - lo) * (h - 6));
            g.fill(px, py, px + 1, py + 2, p.accent());
        }
    }

    private static void marker(GuiGraphics g, int x, int top, int w, int h, float[] curve,
                               float lo, float hi, boolean isHigh, WeatherContext ctx,
                               WeatherTheme.Palette p) {
        int best = 0;
        for (int i = 1; i < curve.length; i++) {
            if (isHigh ? curve[i] > curve[best] : curve[i] < curve[best]) {
                best = i;
            }
        }
        final float f = best / (float) (curve.length - 1);
        final int px = Math.round(x + w * f);
        final int py = Math.round(top + h - 3 - (curve[best] - lo) / (hi - lo) * (h - 6));
        g.fill(px - 1, py - 1, px + 1, py + 1, p.ink());
        final String label = Math.round(ctx.toDisplayUnits(curve[best])) + "°";

        int ly = isHigh ? py - 9 : py + 3;
        if (ly < top + 1) {
            ly = py + 3;
        } else if (ly > top + h - 8) {
            ly = py - 9;
        }
        ly = Math.max(top + 1, Math.min(ly, top + h - 8));

        final float lw = WeatherTheme.textW(ctx.font(), label, WeatherTheme.CAPTION);
        final float lx = Math.max(x + 1, Math.min(px - lw / 2f, x + w - lw - 1));
        WeatherTheme.text(g, ctx.font(), label, lx, ly, WeatherTheme.CAPTION,
                WeatherTheme.alpha(p.ink(), 0.75f));
    }

    private static void nowHairline(GuiGraphics g, int x, int y, int w, int h,
                                    float dayFraction, WeatherTheme.Palette p) {
        final int px = Math.round(timeToX(x, w, dayFraction));
        g.fill(px, y, px + 1, y + h, WeatherTheme.alpha(p.ink(), 0.35f));
    }

    private static void readout(GuiGraphics g, int x, int y, int h, WeatherContext ctx,
                                WeatherTheme.Palette p) {
        final int left = x + WeatherTheme.CARD_PAD;

        final int baseline = y + 34;

        final float roll = ctx.anim().digitRoll();
        final int shown = Math.round(ctx.temperature());
        final String digits = shown + "°";
        final int heroW = Math.round(WeatherTheme.textW(ctx.font(), digits, WeatherTheme.HERO));

        if (roll < 1f) {
            final int rise = Math.round(UiFx.easeOut(roll) * HERO_TALL);
            final String previous = ctx.anim().previousWholeTemperature() + "°";
            g.enableScissor(left, baseline - 1, left + heroW + 8, baseline + HERO_TALL);
            WeatherTheme.text(g, ctx.font(), previous, left, baseline - rise,
                    WeatherTheme.HERO, WeatherTheme.alpha(p.ink(), 1f - roll));
            WeatherTheme.text(g, ctx.font(), digits, left, baseline + HERO_TALL - rise,
                    WeatherTheme.HERO, WeatherTheme.alpha(p.ink(), roll));
            g.disableScissor();
        } else {
            WeatherTheme.text(g, ctx.font(), digits, left, baseline, WeatherTheme.HERO, p.ink());
        }

        WeatherTheme.text(g, ctx.font(), ctx.unit(), left + heroW + 3, baseline + 4,
                1.5f, WeatherTheme.alpha(p.ink(), 0.6f));

        WeatherTheme.text(g, ctx.font(), conditionWord(ctx.panel().condition()),
                left + heroW + 16, baseline + 12, 1.5f, p.ink());

        WeatherTheme.text(g, ctx.font(), caption(ctx), left,
                y + h - WeatherTheme.HORIZON_H - 10, WeatherTheme.CAPTION,
                WeatherTheme.alpha(p.ink(), 0.85f));
    }

    private static String caption(WeatherContext ctx) {
        final var days = ctx.forecast().days();
        final StringBuilder out = new StringBuilder();
        if (!days.isEmpty()) {
            out.append("high ").append(Math.round(ctx.toDisplayUnits(days.get(0).highC())))
               .append("  low ").append(Math.round(ctx.toDisplayUnits(days.get(0).lowC())))
               .append("  ");
        }
        out.append("humidity ").append(Math.round(ctx.anim().humidity())).append('%');
        return out.toString();
    }

    private static String conditionWord(Condition condition) {
        return switch (condition) {
            case CLEAR -> "clear";
            case RAIN -> "rain";
            case STORM -> "storm";
            case SNOW -> "snow";
            case DRY -> "dry here";
            case NONE -> "no sky";
        };
    }

    private static void glyph(GuiGraphics g, int x, int y, int w, WeatherContext ctx,
                              WeatherTheme.Palette p) {
        final float mix = ctx.anim().glyphMix();
        final int size = 40;
        final int gx = x + w - size - WeatherTheme.CARD_PAD;
        final int gy = y + WeatherTheme.CARD_PAD;
        if (mix < 0.999f) {
            blitGlyph(g, ctx.anim().fromCondition(), gx, gy, size, p.accent(), 1f - mix);
        }
        blitGlyph(g, ctx.anim().toCondition(), gx, gy + Math.round((1f - mix) * 4f), size,
                p.accent(), mix);
    }

    private static void blitGlyph(GuiGraphics g, Condition condition, int x, int y, int size,
                                  int accent, float alpha) {
        if (alpha <= 0.004f) {
            return;
        }
        final int index = Math.min(GLYPH_COUNT - 1, Math.max(0, condition.ordinal()));
        g.setColor(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f,
                (accent & 0xFF) / 255f, alpha);
        g.blit(GLYPHS, x, y, size, size, index * (float) GLYPH_PX, 0f, GLYPH_PX, GLYPH_PX,
                GLYPH_PX * GLYPH_COUNT, GLYPH_PX);
        g.setColor(1f, 1f, 1f, 1f);
    }
}
