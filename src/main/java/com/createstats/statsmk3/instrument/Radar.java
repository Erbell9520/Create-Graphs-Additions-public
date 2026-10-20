package com.createstats.statsmk3.instrument;

import java.util.List;

import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// learn the silhouette of a healthy machine, then a dent is obvious
@OnlyIn(Dist.CLIENT)
public final class Radar implements Instrument {
    private static final int MAX_SPOKES = 8;

    private static final int MIN_SPOKES = 3;

    private static final float[] RINGS = {0.5f, 1.0f};

    private static final float LABEL_RADIUS = 30.0f;

    private static final float LABEL_INSET = 0.76f;
    private static final int LABEL_CHARS = 4;

    @Override
    public String id() {
        return "radar";
    }

    @Override
    public String displayName() {
        return "Radar profile";
    }

    @Override
    public String description() {
        return "the shape of the machine";
    }

    @Override
    public boolean needsHistory() {
        return true;
    }

    @Override
    public float minSize() {
        return 34.0f;
    }

    @Override
    public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
        final List<ChannelView> series = ctx.series();
        if (series.isEmpty() || area.isEmpty()) {
            return;
        }
        final int n = Math.min(series.size(), MAX_SPOKES);
        if (n < MIN_SPOKES) {
            Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
            return;
        }

        final boolean labels = ctx.lod.showsLabels()
                && Math.min(area.w, area.h) >= LABEL_RADIUS * 2.0f;
        final float cx = area.centerX();
        final float cy = area.centerY();
        final float radius = Math.min(area.w, area.h) * 0.5f * (labels ? LABEL_INSET : 0.92f);
        if (radius < 6.0f) {
            return;
        }

        final float[] now = new float[n * 2];
        final float[] then = new float[n * 2];
        boolean hasGhost = false;
        boolean anyAlerting = false;

        for (int i = 0; i < n; i++) {
            final ChannelView ch = series.get(i);

            final double angle = -Math.PI * 0.5 + Math.PI * 2.0 * i / n;
            final float ca = (float) Math.cos(angle);
            final float sa = (float) Math.sin(angle);
            final float r = radius * clamp01(ch.fraction());
            now[i * 2] = cx + ca * r;
            now[i * 2 + 1] = cy + sa * r;

            final float was = radius * clamp01(fractionOf(ch, oldest(ch, ctx.lod.historyBudget())));
            then[i * 2] = cx + ca * was;
            then[i * 2 + 1] = cy + sa * was;
            hasGhost |= ch.hasTrend();
            anyAlerting |= ch.alerting;
        }

        drawWeb(s, cx, cy, radius, n, series, ctx);
        s.advanceLayer();

        if (hasGhost) {
            outline(s, then, n, 1.2f, Tokens.alpha(Tokens.INK_2, 0.7f));
        }

        final int shape = anyAlerting ? Tokens.CRITICAL
                : ctx.primary != null ? ctx.primary.color() : Tokens.SERIES[0];

        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            s.tri(cx, cy, Tokens.alpha(shape, 0.16f),
                    now[i * 2], now[i * 2 + 1], Tokens.alpha(shape, 0.16f),
                    now[j * 2], now[j * 2 + 1], Tokens.alpha(shape, 0.16f));
        }
        outline(s, now, n, 1.6f, shape);

        s.advanceLayer();
        for (int i = 0; i < n; i++) {
            final ChannelView ch = series.get(i);
            if (ch.alerting) {
                Shapes.circle(s, now[i * 2], now[i * 2 + 1], 3.4f,
                        Tokens.alpha(Tokens.CRITICAL, 0.35f));
            }
            Shapes.circle(s, now[i * 2], now[i * 2 + 1], 2.0f,
                    ch.alerting ? Tokens.CRITICAL : ch.color());
        }

        if (labels) {
            drawSpokeLabels(s, cx, cy, radius, n, series, ctx);
        }
        if (ctx.lod.showsLabels() && series.size() > n) {
            s.textV("+" + (series.size() - n), area.x, area.bottom() - 5.0f,
                    Tokens.fade(Tokens.INK_3, ctx.lod.labelAlpha()), Text.MICRO);
        }
    }

    private static void drawWeb(PanelSurface s, float cx, float cy, float radius, int n,
                                List<ChannelView> series, InstrumentCtx ctx) {
        if (!ctx.style.grid) {
            return;
        }
        final float alpha = Math.max(0.35f, ctx.lod.labelAlpha());
        for (final float ring : RINGS) {
            final float[] pts = new float[n * 2];
            for (int i = 0; i < n; i++) {
                final double angle = -Math.PI * 0.5 + Math.PI * 2.0 * i / n;
                pts[i * 2] = cx + (float) Math.cos(angle) * radius * ring;
                pts[i * 2 + 1] = cy + (float) Math.sin(angle) * radius * ring;
            }
            outline(s, pts, n, 1.0f, Tokens.alpha(Tokens.LINE, alpha));
        }
        for (int i = 0; i < n; i++) {
            final double angle = -Math.PI * 0.5 + Math.PI * 2.0 * i / n;
            final boolean hot = series.get(i).alerting;
            Shapes.line(s, cx, cy,
                    cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius, 1.0f,
                    hot ? Tokens.alpha(Tokens.CRITICAL, 0.55f) : Tokens.alpha(Tokens.LINE, alpha));
        }
    }

    private static void drawSpokeLabels(PanelSurface s, float cx, float cy, float radius, int n,
                                        List<ChannelView> series, InstrumentCtx ctx) {
        final int color = Tokens.fade(Tokens.INK_3, ctx.lod.labelAlpha());
        for (int i = 0; i < n; i++) {
            final double angle = -Math.PI * 0.5 + Math.PI * 2.0 * i / n;
            final float ca = (float) Math.cos(angle);
            final float sa = (float) Math.sin(angle);
            final float x = cx + ca * (radius + 4.0f);
            final float y = cy + sa * (radius + 4.0f);
            final String label = abbreviate(series.get(i).label);
            if (label.isEmpty()) {
                continue;
            }
            if (ca > 0.35f) {
                s.textV(label, x, y, color, Text.MICRO);
            } else if (ca < -0.35f) {
                s.textRightV(label, x, y, color, Text.MICRO);
            } else {
                s.textCenterV(label, x, y + (sa < 0.0f ? -2.0f : 2.0f), color, Text.MICRO);
            }
        }
    }

    static String abbreviate(String label) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        final String trimmed = label.trim();
        final String[] words = trimmed.split("\\s+");
        if (words.length >= 2) {
            final StringBuilder sb = new StringBuilder();
            for (final String word : words) {
                if (!word.isEmpty() && sb.length() < LABEL_CHARS) {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            return sb.toString();
        }
        return trimmed.substring(0, Math.min(LABEL_CHARS, trimmed.length()))
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static void outline(PanelSurface s, float[] pts, int n, float thickness, int color) {
        for (int i = 0; i < n; i++) {
            final int j = (i + 1) % n;
            Shapes.line(s, pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1], thickness, color);
        }
    }

    private static float oldest(ChannelView ch, int budget) {
        final int window = Math.min(Math.max(budget, 2), ch.historyCount);
        return window < 2 ? ch.value : ch.history[ch.historyCount - window];
    }

    private static float fractionOf(ChannelView ch, float value) {
        final float span = ch.max - ch.min;
        return span <= 1.0e-6f ? 0.0f : (value - ch.min) / span;
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0.0f : v < 0.0f ? 0.0f : Math.min(v, 1.0f);
    }
}
