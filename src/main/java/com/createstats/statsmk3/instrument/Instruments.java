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

@OnlyIn(Dist.CLIENT)
public final class Instruments {
    private Instruments() {
    }

    public static final class Area implements Instrument {
        @Override
        public String id() {
            return "area";
        }

        @Override
        public String displayName() {
            return "Area";
        }

        @Override
        public String description() {
            return "trend over time";
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView ch = ctx.primary;
            if (ctx.style.grid && ctx.lod.showsGrid()) {
                Plot.grid(s, area, 3, ctx.lod.labelAlpha());
            }
            s.advanceLayer();
            if (!ch.hasTrend()) {
                Numeric.drawValue(s, area, ch, ctx);
                return;
            }
            final Plot.Points p = Plot.map(ch, area, ctx.lod.historyBudget(), 0.14f,
                    ctx.yAxis, ctx.plotPoints);
            final int color = ch.color();
            Shapes.areaFill(s, p.xy, p.count, area.bottom(),
                    Tokens.alpha(color, 0.22f), Tokens.alpha(color, 0.0f));
            s.advanceLayer();
            Shapes.polyline(s, p.xy, p.count, Tokens.DATA_STROKE, color);
            Plot.endpoint(s, p.xy[(p.count - 1) * 2], p.xy[(p.count - 1) * 2 + 1],
                    Tokens.DATA_STROKE, color);
            Plot.overflowMarkers(s, area, p, color);
        }
    }

    public static final class Line implements Instrument {
        @Override
        public String id() {
            return "line";
        }

        @Override
        public String displayName() {
            return "Line";
        }

        @Override
        public String description() {
            return "multi series";
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            if (ctx.style.grid && ctx.lod.showsGrid()) {
                Plot.grid(s, area, 3, ctx.lod.labelAlpha());
            }
            s.advanceLayer();
            final List<ChannelView> series = ctx.all.isEmpty() ? List.of(ctx.primary) : ctx.all;

            float lo = ctx.yAxis.isFixed() ? ctx.yAxis.min() : Float.MAX_VALUE;
            float hi = ctx.yAxis.isFixed() ? ctx.yAxis.max() : -Float.MAX_VALUE;
            if (!ctx.yAxis.isFixed()) {
                for (final ChannelView ch : series) {
                    if (!ch.hasTrend()) {
                        continue;
                    }
                    ch.historyExtent(ctx.lod.historyBudget(), 0.14f, ctx.extent);
                    lo = Math.min(lo, ctx.extent[0]);
                    hi = Math.max(hi, ctx.extent[1]);
                }
            }
            if (lo > hi) {
                Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final float span = Math.max(1.0e-6f, hi - lo);

            final int budget = Math.min(ctx.lod.historyBudget(), Plot.columnsFor(area));
            boolean clippedHigh = false;
            boolean clippedLow = false;
            for (final ChannelView ch : series) {
                if (!ch.hasTrend()) {
                    continue;
                }

                final int n = Math.min(Math.max(budget, 2), ch.historyCount);
                if (n < 2) {
                    continue;
                }
                if (ctx.linePoints.length < n * 2) {
                    ctx.linePoints = new float[Math.max(n * 2, ctx.linePoints.length * 2 + 16)];
                }
                final float[] xy = ctx.linePoints;
                final int window = Math.min(ctx.lod.historyBudget(), ch.historyCount);
                final int start = ch.historyCount - window;
                for (int i = 0; i < n; i++) {
                    final int at = start + (int) ((long) i * (window - 1) / (n - 1));
                    final float value = ch.history[at];
                    clippedHigh |= value > hi;
                    clippedLow |= value < lo;
                    final float t = Math.max(0.0f, Math.min(1.0f, (value - lo) / span));
                    xy[i * 2] = area.x + area.w * (i / (float) (n - 1));
                    xy[i * 2 + 1] = area.bottom() - t * area.h;
                }
                Shapes.polyline(s, xy, n, Tokens.DATA_STROKE * 0.9f, ch.color());
                Plot.endpoint(s, xy[(n - 1) * 2], xy[(n - 1) * 2 + 1], Tokens.DATA_STROKE * 0.7f, ch.color());
            }
            if (ctx.yAxis.isFixed()) {
                Plot.overflowMarkers(s, area, clippedHigh, clippedLow, ctx.primary.color());
            }
        }
    }

    public static final class Bar implements Instrument {
        @Override
        public String id() {
            return "bar";
        }

        @Override
        public String displayName() {
            return "Bar";
        }

        @Override
        public String description() {
            return "discrete buckets";
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView ch = ctx.primary;
            if (!ch.hasTrend()) {
                Numeric.drawValue(s, area, ch, ctx);
                return;
            }
            final int budget = Math.max(4, Math.min(24, (int) (area.w / 6.0f)));
            final int n = Math.min(budget, ch.historyCount);
            if (!ctx.yAxis.isFixed()) {
                ch.historyExtent(n, 0.05f, ctx.extent);
            }
            final float lo = ctx.yAxis.isFixed() ? ctx.yAxis.min() : ctx.extent[0];
            final float hi = ctx.yAxis.isFixed() ? ctx.yAxis.max() : ctx.extent[1];
            final float span = Math.max(1.0e-6f, hi - lo);
            final float gap = Tokens.FILL_GAP;
            final float bw = Math.max(2.0f, (area.w - gap * (n - 1)) / n);
            final int start = ch.historyCount - n;
            final int color = ch.color();
            boolean clippedHigh = false;
            boolean clippedLow = false;
            for (int i = 0; i < n; i++) {
                final float value = ch.history[start + i];
                clippedHigh |= value > hi;
                clippedLow |= value < lo;
                final float t = Math.max(0.0f, Math.min(1.0f, (value - lo) / span));
                final float bh = Math.max(2.0f, t * area.h);
                final Rect bar = new Rect(area.x + i * (bw + gap), area.bottom() - bh, bw, bh);
                Shapes.bar(s, bar, i == n - 1 ? color : Tokens.alpha(color, 0.55f));
            }
            if (ctx.yAxis.isFixed()) {
                Plot.overflowMarkers(s, area, clippedHigh, clippedLow, color);
            }
        }
    }

    public static final class Arc implements Instrument {
        private static final float A0 = (float) (Math.PI * 0.78);
        private static final float A1 = (float) (Math.PI * 2.22);

        @Override
        public String id() {
            return "arc";
        }

        @Override
        public String displayName() {
            return "Arc gauge";
        }

        @Override
        public String description() {
            return "bounded magnitude";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView ch = ctx.primary;
            final float cx = area.centerX();
            final float cy = area.y + area.h * 0.62f;
            final float radius = Math.min(area.w, area.h) * 0.34f;
            final float thickness = Math.max(3.0f, radius * 0.22f);
            final float frac = ch.fraction();

            Shapes.arc(s, cx, cy, radius, thickness, A0, A1 - A0, Tokens.LINE);
            s.advanceLayer();
            final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
            Shapes.arc(s, cx, cy, radius, thickness, A0, (A1 - A0) * frac, color);
            Shapes.arcCap(s, cx, cy, radius, A0, thickness, color);
            Shapes.arcCap(s, cx, cy, radius, A0 + (A1 - A0) * frac, thickness, color);

            s.advanceLayer();
            final float angle = A0 + (A1 - A0) * frac;
            Shapes.line(s, cx, cy,
                    cx + (float) Math.cos(angle) * (radius - thickness * 0.5f),
                    cy + (float) Math.sin(angle) * (radius - thickness * 0.5f),
                    1.8f, Tokens.INK);
            Shapes.circle(s, cx, cy, 2.8f, Tokens.INK);

            if (ctx.lod.showsLabels() && area.h > 46.0f) {
                s.textCenter(Text.truncate(s, Text.compact(ch.value), area.w, Text.META),
                        cx, area.bottom() - 10.0f,
                        Tokens.fade(Tokens.INK, ctx.lod.labelAlpha()), Text.META);
            }
        }
    }

    public static final class Tape implements Instrument {
        @Override
        public String id() {
            return "tape";
        }

        @Override
        public String displayName() {
            return "Tape scale";
        }

        @Override
        public String description() {
            return "precise reading";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView ch = ctx.primary;
            Shapes.roundedRect(s, area, Tokens.R_CHIP, Tokens.CANVAS);
            s.advanceLayer();

            final float range = Math.max(1.0e-6f, ch.max - ch.min);
            final float step = niceStep(range / 10.0f);
            final float pixelsPerUnit = area.h / (range * 0.42f);
            final float cy = area.centerY();

            for (int i = -6; i <= 6; i++) {
                final float tick = Math.round((ch.value + i * step) / step) * step;
                final float ty = cy - (tick - ch.value) * pixelsPerUnit;
                if (ty < area.y - 4.0f || ty > area.bottom() + 4.0f) {
                    continue;
                }
                Shapes.rect(s, area.right() - 14.0f, Math.round(ty), 9.0f, 1.0f, Tokens.LINE_HI);
                if (ctx.lod.showsLabels()) {
                    s.textRight(Text.value(tick), area.right() - 17.0f, ty - 3.0f,
                            Tokens.fade(Tokens.INK_3, ctx.lod.labelAlpha()), Text.MICRO);
                }
            }

            s.advanceLayer();
            final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
            final Rect window = new Rect(area.x, cy - 9.0f, area.w, 18.0f);
            Shapes.rect(s, window, Tokens.alpha(color, 0.16f));
            Shapes.rect(s, area.x, window.y, area.w, 1.0f, color);
            Shapes.rect(s, area.x, window.bottom() - 1.0f, area.w, 1.0f, color);
            s.text(Text.truncate(s, Text.value(ch.value), area.w - 12.0f, Text.BODY),
                    area.x + 6.0f, cy - 4.0f, Tokens.INK, Text.BODY);
        }

        private static float niceStep(float raw) {
            if (raw <= 0.0f) {
                return 1.0f;
            }
            final float mag = (float) Math.pow(10, Math.floor(Math.log10(raw)));
            final float norm = raw / mag;
            final float snapped = norm < 1.5f ? 1.0f : norm < 3.0f ? 2.0f : norm < 7.0f ? 5.0f : 10.0f;
            return snapped * mag;
        }
    }

    public static final class Numeric implements Instrument {
        @Override
        public String id() {
            return "numeric";
        }

        @Override
        public String displayName() {
            return "Numeric";
        }

        @Override
        public String description() {
            return "one headline";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 14.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            drawValue(s, area, ctx.primary, ctx);
        }

        static void drawValue(PanelSurface s, Rect area, ChannelView ch, InstrumentCtx ctx) {
            final boolean wantsUnit = ctx.lod.showsLabels() && !ch.unit.isEmpty() && area.h > 20.0f;
            float unitW = wantsUnit ? s.textWidth(ch.unit, Text.MICRO) + 3.0f : 0.0f;
            if (unitW > area.w * 0.4f) {
                unitW = 0.0f;
            }

            final Text.Fitted fit = Text.fitValue(s, ch.value, area.w - unitW, area.h * 0.78f,
                    Text.HERO, Text.MICRO);
            final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
            final float ty = area.centerY() - Text.lineHeight(fit.scale()) * 0.5f;
            s.text(fit.text(), area.x, ty, color, fit.scale());

            if (unitW > 0.0f) {
                s.text(ch.unit, area.x + s.textWidth(fit.text(), fit.scale()) + 3.0f,
                        ty + Text.lineHeight(fit.scale()) - Text.lineHeight(Text.MICRO) - 1.0f,
                        Tokens.INK_3, Text.MICRO);
            }
        }
    }

    public static final class Ring implements Instrument {
        @Override
        public String id() {
            return "ring";
        }

        @Override
        public String displayName() {
            return "Ring";
        }

        @Override
        public String description() {
            return "fraction of budget";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView ch = ctx.primary;
            final float r = Math.min(area.w, area.h) * 0.32f;
            final float thickness = Math.max(3.0f, r * 0.26f);
            final float cx = area.centerX();
            final float cy = area.centerY();
            final float start = (float) (-Math.PI * 0.5);

            Shapes.arc(s, cx, cy, r, thickness, start, (float) (Math.PI * 2.0), Tokens.LINE);
            s.advanceLayer();
            final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
            final float sweep = (float) (Math.PI * 2.0) * ch.fraction();
            Shapes.arc(s, cx, cy, r, thickness, start, sweep, color);
            if (sweep > 0.02f) {
                Shapes.arcCap(s, cx, cy, r, start, thickness, color);
                Shapes.arcCap(s, cx, cy, r, start + sweep, thickness, color);
            }
            if (ctx.lod.showsLabels() && r > 16.0f) {
                final String pct = Math.round(ch.fraction() * 100.0f) + "%";
                s.textCenter(pct, cx, cy - Text.lineHeight(Text.BODY) * 0.5f,
                        Tokens.fade(Tokens.INK, ctx.lod.labelAlpha()), Text.BODY);
            }
        }
    }

    // this used to be sin(row*1.7 + col*0.9 + fraction*6). reported CRITICAL on cells that meant nothing. yeah
    public static final class Matrix implements Instrument {
        private static final float LABEL_CELL_W = 58.0f;
        private static final float LABEL_CELL_H = 17.0f;

        private static final float MIN_CELL = 4.0f;

        private static final float TARGET_ASPECT = 2.6f;

        private static final float ROUND_ABOVE = 9.0f;

        private static final float FOOTER_H = 9.0f;

        @Override
        public String id() {
            return "matrix";
        }

        @Override
        public String displayName() {
            return "Status board";
        }

        @Override
        public String description() {
            return "every signal at once";
        }

        @Override
        public boolean needsHistory() {
            return true;
        }

        @Override
        public float minSize() {
            return 16.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final List<ChannelView> series = ctx.series();
            if (series.isEmpty() || area.isEmpty()) {
                return;
            }
            final float gap = Math.min(area.w, area.h) >= 30.0f ? Tokens.FILL_GAP : 1.0f;

            Rect board = area;
            int shown = Math.min(series.size(), capacity(board, gap));
            final boolean footer = shown < series.size()
                    && ctx.lod.showsLabels() && area.h >= 34.0f;
            if (footer) {
                board = area.withHeight(area.h - FOOTER_H);
                shown = Math.min(series.size(), capacity(board, gap));
            }
            if (shown <= 0) {
                return;
            }

            final int cols = colsFor(board, shown, gap);
            final int rows = ceilDiv(shown, cols);
            final float cw = (board.w - gap * (cols - 1)) / cols;
            final float chh = (board.h - gap * (rows - 1)) / rows;
            if (cw < 1.0f || chh < 1.0f) {
                return;
            }
            final boolean labelled = ctx.lod.showsLabels()
                    && cw >= LABEL_CELL_W && chh >= LABEL_CELL_H;

            for (int i = 0; i < shown; i++) {
                final Rect cell = new Rect(board.x + (i % cols) * (cw + gap),
                        board.y + (i / cols) * (chh + gap), cw, chh);

                drawCell(s, cell, series.get(i), i == 0 && series.size() > 1, labelled, ctx);
            }

            if (footer) {
                s.textV("+" + (series.size() - shown) + " more", area.x,
                        area.bottom() - FOOTER_H * 0.5f,
                        Tokens.fade(Tokens.INK_3, ctx.lod.labelAlpha()), Text.MICRO);
            }
        }

        private static void drawCell(PanelSurface s, Rect cell, ChannelView ch, boolean bound,
                                     boolean labelled, InstrumentCtx ctx) {
            final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
            final float radius = Math.min(2.0f, Math.min(cell.w, cell.h) * 0.25f);

            if (labelled) {
                fill(s, cell, radius, Tokens.CANVAS);
                drawLabelledCell(s, cell, ch, color, ctx);
            } else {
                fill(s, cell, radius, Tokens.LINE);
                final int body = Tokens.alpha(color, ch.stale ? 0.16f : 0.32f);
                final int cap = Tokens.alpha(color, ch.stale ? 0.45f : 1.0f);
                if (cell.w >= cell.h) {
                    final float w = Math.max(2.0f, ch.fraction() * cell.w);
                    final float capW = Math.min(2.0f, w);
                    fill(s, new Rect(cell.x, cell.y, w, cell.h), radius, body);
                    Shapes.rect(s, cell.x + w - capW, cell.y, capW, cell.h, cap);
                } else {
                    final float h = Math.max(2.0f, ch.fraction() * cell.h);
                    fill(s, new Rect(cell.x, cell.bottom() - h, cell.w, h), radius, body);
                    Shapes.rect(s, cell.x, cell.bottom() - h, cell.w, Math.min(2.0f, h), cap);
                }
            }

            if (ch.alerting || bound) {
                Shapes.roundedRectOutline(s, cell, radius, 1.0f,
                        ch.alerting ? Tokens.CRITICAL : Tokens.ACCENT);
            }
            if (ch.alerting && Math.min(cell.w, cell.h) >= 9.0f) {
                Shapes.circle(s, cell.right() - 3.2f, cell.y + 3.2f, 1.6f, Tokens.CRITICAL);
            }
        }

        private static void drawLabelledCell(PanelSurface s, Rect cell, ChannelView ch, int color,
                                             InstrumentCtx ctx) {
            final float pad = 3.0f;
            final Rect inner = cell.inset(pad);
            if (inner.isEmpty()) {
                return;
            }
            final String value = Text.compact(ch.value);
            final float valueW = s.textWidth(value, Text.MICRO);
            s.textRight(value, inner.right(), inner.y,
                    ch.alerting ? Tokens.CRITICAL : Tokens.INK, Text.MICRO);
            final float labelW = inner.w - valueW - 4.0f;
            if (labelW > 10.0f) {
                s.text(Text.truncate(s, ch.label, labelW, Text.MICRO), inner.x, inner.y,
                        Tokens.INK_3, Text.MICRO);
            }

            final float caretW = inner.w >= 52.0f ? 7.0f : 0.0f;
            final float barH = Math.min(4.0f, inner.h * 0.28f);
            final Rect track = new Rect(inner.x, inner.bottom() - barH,
                    inner.w - caretW, barH);
            if (track.isEmpty()) {
                return;
            }
            Shapes.roundedRect(s, track, barH * 0.5f, Tokens.LINE);
            final float filled = Math.max(barH, track.w * ch.fraction());
            Shapes.roundedRect(s, track.withWidth(filled), barH * 0.5f,
                    Tokens.alpha(color, ch.stale ? 0.35f : 1.0f));

            if (caretW > 0.0f) {
                drawCaret(s, inner.right() - caretW * 0.5f, track.centerY(),
                        trend(ch, ctx.lod.historyBudget()));
            }
        }

        private static int trend(ChannelView ch, int budget) {
            final int window = Math.min(Math.max(budget, 2), ch.historyCount);
            if (window < 2) {
                return 0;
            }
            final float[] extent = ch.historyExtent(window, 0.0f);
            final float span = extent[1] - extent[0];
            final float delta = ch.value - ch.history[ch.historyCount - window];

            return Math.abs(delta) < span * 0.05f ? 0 : delta > 0.0f ? 1 : -1;
        }

        private static void drawCaret(PanelSurface s, float cx, float cy, int direction) {
            if (direction == 0) {
                Shapes.rect(s, cx - 2.5f, cy - 0.5f, 5.0f, 1.0f, Tokens.INK_3);
                return;
            }
            final float dy = direction > 0 ? -2.0f : 2.0f;
            s.tri(cx, cy + dy, Tokens.INK_2, cx - 2.6f, cy - dy * 0.6f, Tokens.INK_2,
                    cx + 2.6f, cy - dy * 0.6f, Tokens.INK_2);
        }

        private static void fill(PanelSurface s, Rect r, float radius, int color) {
            if (radius >= 1.0f && Math.min(r.w, r.h) >= ROUND_ABOVE) {
                Shapes.roundedRect(s, r, radius, color);
            } else {
                Shapes.rect(s, r, color);
            }
        }

        private static int capacity(Rect area, float gap) {
            final int cols = Math.max(1, (int) ((area.w + gap) / (MIN_CELL + gap)));
            final int rows = Math.max(1, (int) ((area.h + gap) / (MIN_CELL + gap)));
            return cols * rows;
        }

        private static int colsFor(Rect area, int n, float gap) {
            int best = 1;
            float bestErr = Float.MAX_VALUE;
            for (int cols = 1; cols <= n; cols++) {
                final float cw = (area.w - gap * (cols - 1)) / cols;
                final float chh = (area.h - gap * (ceilDiv(n, cols) - 1)) / ceilDiv(n, cols);
                if (cw < MIN_CELL || chh < MIN_CELL) {
                    continue;
                }
                final float aspect = cw / chh;
                final float err = aspect > TARGET_ASPECT
                        ? aspect / TARGET_ASPECT : TARGET_ASPECT / aspect;
                if (err < bestErr) {
                    bestErr = err;
                    best = cols;
                }
            }
            return best;
        }

        private static int ceilDiv(int a, int b) {
            return (a + b - 1) / b;
        }
    }

    public static final class SparkRow implements Instrument {
        private static final float MIN_ROW = 7.0f;

        private static final float LABEL_ROW = 10.0f;
        private static final float ROW_GAP = 3.0f;
        private static final float SWATCH = 5.0f;
        private static final float FOOTER_H = 9.0f;

        private static final float FOOTER_ABOVE = 44.0f;

        private static final float LABEL_WIDTH_FLOOR = 74.0f;

        private static final int ROW_BUDGET = 64;

        @Override
        public String id() {
            return "spark";
        }

        @Override
        public String displayName() {
            return "Spark rows";
        }

        @Override
        public String description() {
            return "every trend, stacked";
        }

        @Override
        public float minSize() {
            return 20.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final List<ChannelView> series = ctx.series();
            if (series.isEmpty() || area.isEmpty()) {
                return;
            }

            final boolean named = ctx.lod.showsLabels() && area.w >= LABEL_WIDTH_FLOOR;
            final float minRow = named ? LABEL_ROW : MIN_ROW;

            Rect body = area;
            int rows = rowsIn(body, series.size(), minRow);
            final boolean footer = rows < series.size()
                    && ctx.lod.showsLabels() && area.h >= FOOTER_ABOVE;
            if (footer) {
                body = area.withHeight(area.h - FOOTER_H);
                rows = rowsIn(body, series.size(), minRow);
            }
            if (rows <= 0) {
                return;
            }
            final float rowH = (body.h - ROW_GAP * (rows - 1)) / rows;

            final boolean labels = named && rowH >= 8.0f;
            float valueW = 0.0f;
            float labelW = 0.0f;
            if (labels) {
                for (int i = 0; i < rows; i++) {
                    valueW = Math.max(valueW,
                            s.textWidth(Text.compact(series.get(i).value), Text.MICRO));
                }
                valueW = Math.min(valueW + 5.0f, area.w * 0.28f);
                labelW = Math.min(Math.max(area.w * 0.26f, 30.0f), 78.0f);

                final float plotW = area.w - SWATCH - 3.0f - labelW - 4.0f - valueW;
                if (plotW < area.w * 0.34f) {
                    labelW -= area.w * 0.34f - plotW;
                }
                if (labelW < 18.0f) {
                    labelW = 0.0f;
                }
            }
            final boolean marked = series.size() > 1;

            for (int i = 0; i < rows; i++) {
                drawRow(s, new Rect(body.x, body.y + i * (rowH + ROW_GAP), body.w, rowH),
                        series.get(i), i == 0 && marked, labelW, valueW, ctx);
            }

            if (footer) {
                s.textV("+" + (series.size() - rows) + " more", area.x,
                        area.bottom() - FOOTER_H * 0.5f,
                        Tokens.fade(Tokens.INK_3, ctx.lod.labelAlpha()), Text.MICRO);
            }
        }

        private static int rowsIn(Rect body, int channels, float minRow) {
            final int fits = (int) ((body.h + ROW_GAP) / (minRow + ROW_GAP));
            return Math.max(1, Math.min(channels, fits));
        }

        private static void drawRow(PanelSurface s, Rect row, ChannelView ch, boolean bound,
                                    float labelW, float valueW, InstrumentCtx ctx) {
            final int color = ch.alerting ? Tokens.CRITICAL : ch.color();
            float x = row.x;

            if (bound) {
                Shapes.rect(s, row.x, row.y, 1.5f, row.h, Tokens.alpha(Tokens.ACCENT, 0.85f));
                x += 4.0f;
            }
            if (row.h >= 7.0f) {
                Shapes.roundedRect(s, new Rect(x, row.centerY() - SWATCH * 0.5f, SWATCH, SWATCH),
                        1.5f, color);
                x += SWATCH + 3.0f;
            }
            if (labelW > 0.0f) {
                s.textV(Text.truncate(s, ch.label, labelW, Text.MICRO), x, row.centerY(),
                        bound ? Tokens.INK_2 : Tokens.INK_3, Text.MICRO);
                x += labelW + 4.0f;
            }

            final Rect plot = new Rect(x, row.y + 1.0f,
                    row.right() - valueW - x, Math.max(1.0f, row.h - 2.0f));
            if (!plot.isEmpty()) {
                drawTrend(s, plot, ch, color, bound, ctx);
            }
            if (valueW > 0.0f) {
                s.textRightV(Text.compact(ch.value), row.right(), row.centerY(),
                        ch.alerting ? Tokens.CRITICAL : Tokens.INK, Text.MICRO);
            }
        }

        private static void drawTrend(PanelSurface s, Rect plot, ChannelView ch, int color,
                                      boolean bound, InstrumentCtx ctx) {
            if (!ch.hasTrend()) {
                Shapes.rect(s, plot.x, plot.centerY(), plot.w, 1.0f, Tokens.alpha(color, 0.4f));
                return;
            }
            final Plot.Points p = Plot.map(ch, plot,
                    Math.min(ROW_BUDGET, ctx.lod.historyBudget()), 0.18f,
                    ctx.yAxis, ctx.plotPoints);
            if (p.count < 2) {
                return;
            }
            Shapes.areaFill(s, p.xy, p.count, plot.bottom(),
                    Tokens.alpha(color, bound ? 0.22f : 0.14f), Tokens.alpha(color, 0.0f));

            Shapes.polyline(s, p.xy, p.count, bound ? 1.6f : 1.3f,
                    bound ? color : Tokens.alpha(color, 0.82f));
            Shapes.circle(s, p.xy[(p.count - 1) * 2], p.xy[(p.count - 1) * 2 + 1], 1.5f, color);
            Plot.overflowMarkers(s, plot, p, color);
        }
    }
}
