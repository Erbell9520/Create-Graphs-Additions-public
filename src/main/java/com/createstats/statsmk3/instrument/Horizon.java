package com.createstats.statsmk3.instrument;

import java.util.Arrays;
import java.util.List;

import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Horizon implements Instrument {
    private static final int BANDS = 3;
    private static final int BANDS_SHORT = 2;
    private static final float SHORT_STRIP = 13.0f;

    private static final float[] MIX_3 = {0.52f, 0.76f, 1.0f};
    private static final float[] MIX_2 = {0.60f, 1.0f};

    private static final float HEADER_H = 10.0f;
    private static final float HEADER_ABOVE = 26.0f;

    private static final float HEADER_TIGHT = 44.0f;
    private static final float HEADER_WIDTH_FLOOR = 74.0f;

    private static final float HEADER_ROOMY = 116.0f;

    private static final float STACK_ABOVE = 42.0f;

    private static final float STACK_WIDTH_FLOOR = 70.0f;

    private static final float ROW_MIN = 11.0f;
    private static final float ROW_GAP = 3.0f;
    private static final float SWATCH = 5.0f;

    private static final float SCALE_COLUMN_FLOOR = 128.0f;

    private static final float STRIP_SHARE = 0.44f;
    private static final float FOOTER_H = 9.0f;

    private static final float FOOTER_ABOVE = 58.0f;

    private float[] sorted = new float[0];
    private float[] rowReference = new float[0];
    private float[] rowSpan = new float[0];
    private final int[] warm = new int[BANDS];
    private final int[] cool = new int[BANDS];
    private final float[] edges = new float[BANDS + 1];

    private float reference;
    private float span;

    @Override
    public String id() {
        return "horizon";
    }

    @Override
    public String displayName() {
        return "Horizon";
    }

    @Override
    public String description() {
        return "a long window, folded";
    }

    @Override
    public float minSize() {
        return 16.0f;
    }

    @Override
    public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
        if (area.isEmpty()) {
            return;
        }
        final List<ChannelView> series = ctx.series();
        final ChannelView primary = ctx.primary != null ? ctx.primary
                : (series.isEmpty() ? null : series.get(0));
        if (primary == null) {
            return;
        }
        if (series.size() >= 2 && ctx.lod.showsLabels()
                && area.h >= STACK_ABOVE && area.w >= STACK_WIDTH_FLOOR) {
            drawStack(s, area, series, ctx);
        } else {
            drawOne(s, area, primary, ctx);
        }
    }

    private void drawOne(PanelSurface s, Rect area, ChannelView ch, InstrumentCtx ctx) {
        final int window = windowOf(ch, ctx);
        if (window < 2) {
            Instruments.Numeric.drawValue(s, area, ch, ctx);
            return;
        }
        final int start = ch.historyCount - window;
        final boolean usable = measure(ch, start, window);

        final boolean header = ctx.lod.showsLabels() && area.h >= HEADER_ABOVE
                && area.w >= HEADER_TIGHT;
        final Rect strip = header ? area.below(HEADER_H) : area;
        if (strip.isEmpty()) {
            return;
        }
        final int bands = strip.h >= SHORT_STRIP ? BANDS : BANDS_SHORT;
        if (header) {
            drawHeader(s, area.top(HEADER_H), ch, this.reference, this.span, bands, ctx);
        }

        ground(s, strip);
        s.advanceLayer();
        if (usable) {
            folds(s, strip, ch, start, window, this.reference, this.span, bands, colorOf(ch));
        } else {
            flatRule(s, strip, ch);
        }

        s.advanceLayer();

        Shapes.rect(s, strip.right() - 1.0f, strip.y, 1.0f, strip.h,
                Tokens.alpha(Tokens.INK, 0.55f));
    }

    private void drawStack(PanelSurface s, Rect area, List<ChannelView> series,
                           InstrumentCtx ctx) {
        Rect body = area;
        int rows = rowsIn(body, series.size());
        final boolean footer = rows < series.size() && area.h >= FOOTER_ABOVE;
        if (footer) {
            body = area.withHeight(area.h - FOOTER_H);
            rows = rowsIn(body, series.size());
        }
        if (rows <= 0) {
            return;
        }
        final float rowH = (body.h - ROW_GAP * (rows - 1)) / rows;

        if (this.rowReference.length < rows) {
            this.rowReference = new float[Math.max(rows, 8)];
            this.rowSpan = new float[Math.max(rows, 8)];
        }
        final boolean scales = area.w >= SCALE_COLUMN_FLOOR;
        float scaleW = 0.0f;
        for (int i = 0; i < rows; i++) {
            final ChannelView ch = series.get(i);
            final int window = windowOf(ch, ctx);
            if (window < 2 || !measure(ch, ch.historyCount - window, window)) {
                this.rowReference[i] = window < 2 ? ch.value : this.reference;
                this.rowSpan[i] = 0.0f;
                continue;
            }
            this.rowReference[i] = this.reference;
            this.rowSpan[i] = this.span;
            if (scales) {
                scaleW = Math.max(scaleW, s.textWidth(scaleText(this.span), Text.MICRO));
            }
        }
        if (scaleW > 0.0f) {
            scaleW = Math.min(scaleW + 5.0f, area.w * 0.26f);
        }

        float natural = 0.0f;
        for (int i = 0; i < rows; i++) {
            natural = Math.max(natural, s.textWidth(series.get(i).label, Text.MICRO));
        }
        float labelW = Math.min(natural, Math.min(Math.max(area.w * 0.30f, 30.0f), 68.0f));
        final float chrome = 4.0f + SWATCH + 3.0f + 4.0f + scaleW;
        if (area.w - chrome - labelW < area.w * STRIP_SHARE) {
            labelW -= area.w * STRIP_SHARE - (area.w - chrome - labelW);
        }

        if (labelW < Math.min(natural, 16.0f)) {
            labelW = 0.0f;
        }

        for (int i = 0; i < rows; i++) {
            ground(s, stripOf(rowAt(body, i, rowH), labelW, scaleW));
        }
        s.advanceLayer();
        for (int i = 0; i < rows; i++) {
            final ChannelView ch = series.get(i);
            final Rect strip = stripOf(rowAt(body, i, rowH), labelW, scaleW);
            if (strip.isEmpty()) {
                continue;
            }
            if (this.rowSpan[i] <= 0.0f) {
                flatRule(s, strip, ch);
                continue;
            }
            final int window = windowOf(ch, ctx);
            folds(s, strip, ch, ch.historyCount - window, window, this.rowReference[i],
                    this.rowSpan[i], strip.h >= SHORT_STRIP ? BANDS : BANDS_SHORT, colorOf(ch));
        }

        s.advanceLayer();

        final float nowX = body.right() - scaleW - 1.0f;
        Shapes.rect(s, nowX, body.y, 1.0f, body.h, Tokens.alpha(Tokens.INK, 0.45f));
        for (int i = 0; i < rows; i++) {
            drawRowChrome(s, rowAt(body, i, rowH), series.get(i), i == 0, labelW, scaleW,
                    this.rowSpan[i], ctx);
        }
        if (footer) {
            s.textV("+" + (series.size() - rows) + " more", area.x,
                    area.bottom() - FOOTER_H * 0.5f,
                    Tokens.fade(Tokens.INK_3, ctx.lod.labelAlpha()), Text.MICRO);
        }
    }

    private static int rowsIn(Rect body, int channels) {
        final int fits = (int) ((body.h + ROW_GAP) / (ROW_MIN + ROW_GAP));
        return Math.max(1, Math.min(channels, fits));
    }

    private static Rect rowAt(Rect body, int i, float rowH) {
        return new Rect(body.x, body.y + i * (rowH + ROW_GAP), body.w, rowH);
    }

    private static Rect stripOf(Rect row, float labelW, float scaleW) {
        final float x = row.x + 4.0f + SWATCH + 3.0f + (labelW > 0.0f ? labelW + 4.0f : 0.0f);
        return new Rect(x, row.y, Math.max(0.0f, row.right() - scaleW - x), row.h);
    }

    private void drawRowChrome(PanelSurface s, Rect row, ChannelView ch, boolean bound,
                               float labelW, float scaleW, float span, InstrumentCtx ctx) {
        final float alpha = ctx.lod.labelAlpha();
        float x = row.x;
        if (bound) {
            Shapes.rect(s, row.x, row.y, 1.5f, row.h, Tokens.alpha(Tokens.ACCENT, 0.85f));
        }
        x += 4.0f;
        Shapes.roundedRect(s, new Rect(x, row.centerY() - SWATCH * 0.5f, SWATCH, SWATCH), 1.5f,
                colorOf(ch));
        x += SWATCH + 3.0f;
        if (labelW > 0.0f) {
            s.textV(Text.truncate(s, ch.label, labelW, Text.MICRO), x, row.centerY(),
                    Tokens.fade(bound ? Tokens.INK_2 : Tokens.INK_3, alpha), Text.MICRO);
        }
        if (scaleW > 0.0f && span > 0.0f) {
            s.textRightV(scaleText(span), row.right(), row.centerY(),
                    Tokens.fade(Tokens.INK_3, alpha), Text.MICRO);
        }
    }

    private static void ground(PanelSurface s, Rect strip) {
        if (!strip.isEmpty()) {
            Shapes.rect(s, strip, Tokens.CANVAS);
        }
    }

    private static void flatRule(PanelSurface s, Rect strip, ChannelView ch) {
        Shapes.rect(s, strip.x, strip.bottom() - 1.0f, strip.w, 1.0f,
                Tokens.alpha(colorOf(ch), 0.55f));
    }

    private void folds(PanelSurface s, Rect strip, ChannelView ch, int start, int window,
                       float ref, float span, int bands, int color) {
        ramp(this.warm, color, bands);
        ramp(this.cool, Tokens.DEVIATION_LO, bands);
        final int cols = Math.max(1, Math.round(strip.w));
        final float colW = strip.w / cols;

        for (int c = 0; c < cols; c++) {
            final float dev = deviationAt(ch, ref, start, window, cols, c);
            final float t = Math.abs(dev) / span;
            final int[] shades = dev >= 0.0f ? this.warm : this.cool;
            final float x = strip.x + c * colW;

            final float w = Math.min(colW + 0.5f, strip.right() - x);

            Shapes.rect(s, x, strip.bottom() - 1.0f, w, 1.0f, shades[0]);

            for (int band = 0; band <= bands; band++) {
                this.edges[band] = band == bands ? 0.0f
                        : Math.min(1.0f, Math.max(0.0f, t * bands - band)) * strip.h;
            }
            for (int band = 0; band < bands; band++) {
                final float top = this.edges[band];
                final float bottom = this.edges[band + 1];
                if (top - bottom < 0.05f) {
                    continue;
                }
                Shapes.rect(s, x, strip.bottom() - top, w, top - bottom, shades[band]);
            }
        }
    }

    private static void ramp(int[] out, int color, int bands) {
        final float[] mix = bands >= BANDS ? MIX_3 : MIX_2;
        for (int i = 0; i < bands; i++) {
            out[i] = Tokens.mix(Tokens.CANVAS, color, mix[Math.min(i, mix.length - 1)]);
        }
    }

    private static float deviationAt(ChannelView ch, float ref, int start, int window, int cols,
                                     int c) {
        if (window >= cols) {
            final int from = start + (int) ((long) c * window / cols);
            final int to = Math.max(from + 1, start + (int) ((long) (c + 1) * window / cols));
            float dev = 0.0f;
            for (int i = from; i < to && i < ch.historyCount; i++) {
                final float d = ch.history[i] - ref;
                if (Math.abs(d) > Math.abs(dev)) {
                    dev = d;
                }
            }
            return dev;
        }
        final float pos = cols <= 1 ? 0.0f : c * (window - 1) / (float) (cols - 1);
        final int i0 = Math.min(window - 1, (int) pos);
        final int i1 = Math.min(window - 1, i0 + 1);
        final float f = pos - i0;
        return ch.history[start + i0] * (1.0f - f) + ch.history[start + i1] * f - ref;
    }

    private static int windowOf(ChannelView ch, InstrumentCtx ctx) {
        if (!ch.hasTrend()) {
            return 0;
        }
        return Math.min(Math.max(ctx.lod.historyBudget(), 2), ch.historyCount);
    }

    private boolean measure(ChannelView ch, int start, int window) {
        if (this.sorted.length < window) {
            this.sorted = new float[Math.max(window, 128)];
        }
        System.arraycopy(ch.history, start, this.sorted, 0, window);
        Arrays.sort(this.sorted, 0, window);

        this.reference = percentile(this.sorted, window, 0.5f);
        this.span = Math.max(percentile(this.sorted, window, 0.9f) - this.reference,
                this.reference - percentile(this.sorted, window, 0.1f));

        final float eps = Math.max(1.0e-6f, Math.abs(this.reference) * 1.0e-6f);
        if (this.span <= eps) {
            this.span = Math.max(this.sorted[window - 1] - this.reference,
                    this.reference - this.sorted[0]);
        }
        return this.span > eps;
    }

    private static float percentile(float[] sorted, int n, float p) {
        if (n <= 1) {
            return n == 1 ? sorted[0] : 0.0f;
        }
        final float pos = Math.max(0.0f, Math.min(1.0f, p)) * (n - 1);
        final int i = (int) pos;
        final int j = Math.min(n - 1, i + 1);
        return sorted[i] + (sorted[j] - sorted[i]) * (pos - i);
    }

    private static int colorOf(ChannelView ch) {
        return ch.alerting ? Tokens.CRITICAL : ch.color();
    }

    private static String scaleText(float span) {
        return "±" + Text.compact(span);
    }

    private static void drawHeader(PanelSurface s, Rect row, ChannelView ch, float ref, float span,
                                   int bands, InstrumentCtx ctx) {
        final float alpha = ctx.lod.labelAlpha();

        final String scale = row.w >= HEADER_ROOMY
                ? scaleText(span) + " / " + bands + "x" + Text.compact(span / bands)
                : scaleText(span);
        final float scaleW = s.textWidth(scale, Text.MICRO);
        s.textRightV(scale, row.right(), row.centerY(), Tokens.fade(Tokens.INK_3, alpha),
                Text.MICRO);

        if (row.w < HEADER_WIDTH_FLOOR) {
            return;
        }

        final String reference = "typ " + Text.compact(ref)
                + (ch.unit.isEmpty() ? "" : " " + ch.unit);
        s.textV(Text.truncate(s, reference, row.w - scaleW - 6.0f, Text.MICRO), row.x,
                row.centerY(), Tokens.fade(Tokens.INK_2, alpha), Text.MICRO);
    }
}
