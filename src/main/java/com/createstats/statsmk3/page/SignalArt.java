package com.createstats.statsmk3.page;

import com.createstats.statsmk3.YAxisRange;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SignalArt {
    private static final ThreadLocal<PointBuffer> POINTS =
            ThreadLocal.withInitial(PointBuffer::new);

    private static final class PointBuffer {
        float[] xy = new float[0];
        int count;

        void ensure(int floats) {
            if (this.xy.length < floats) {
                this.xy = new float[Math.max(floats, this.xy.length * 2 + 32)];
            }
        }
    }

    private SignalArt() {
    }

    public static final float STROKE = 2.0f;

    public static final float LANE_MIN_H = 46.0f;

    public static final float AXIS_W = 46.0f;

    public record Lane(Signals.Signal signal, float[] window, Signals.Stats stats, int color,
                       boolean limit, float limitValue, boolean limitAbove, int limitColor,
                       YAxisRange yAxis) {
        public Lane {
            yAxis = yAxis == null ? YAxisRange.auto() : yAxis;
        }

        public Lane(Signals.Signal signal, float[] window, Signals.Stats stats, int color,
                    boolean limit, float limitValue, boolean limitAbove, int limitColor) {
            this(signal, window, stats, color, limit, limitValue, limitAbove, limitColor,
                    YAxisRange.auto());
        }
    }

    public record Mapped(Rect plot, float lo, float hi, int count) {
        public float yFor(float value) {
            final float span = this.hi - this.lo;
            if (span <= 1.0e-6f) {
                return this.plot.centerY();
            }
            final float t = Math.max(0.0f, Math.min(1.0f, (value - this.lo) / span));
            return this.plot.bottom() - t * this.plot.h;
        }

        public float xFor(int index) {
            if (this.count < 2) {
                return this.plot.right();
            }
            return this.plot.x + this.plot.w * (index / (float) (this.count - 1));
        }

        public int indexAt(double mx) {
            if (this.count < 2) {
                return 0;
            }
            final float t = (float) ((mx - this.plot.x) / Math.max(1.0f, this.plot.w));
            return Math.max(0, Math.min(this.count - 1, Math.round(t * (this.count - 1))));
        }
    }

    public static Mapped lane(PanelSurface c, Rect r, Lane lane, float detail, float reveal) {
        if (r.isEmpty() || lane == null) {
            return new Mapped(r, 0.0f, 1.0f, 0);
        }
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);

        final float headH = r.h >= 54.0f ? 15.0f : 0.0f;
        final boolean axis = r.w > 210.0f && r.h >= 44.0f;
        final Rect plot = r.inset(Tokens.S3, Tokens.S2 + headH, axis ? AXIS_W : Tokens.S3, Tokens.S2);
        if (plot.isEmpty()) {
            return new Mapped(plot, 0.0f, 1.0f, 0);
        }

        final float[] w = lane.window();
        if (w == null || w.length < 2) {
            if (headH > 0.0f) {
                laneHead(c, r, lane, detail);
            }
            c.textCenterV(w == null || w.length == 0 ? "No samples yet" : "Collecting samples",
                    plot.centerX(), plot.centerY(), Tokens.INK_3, Text.META);
            return new Mapped(plot, 0.0f, 1.0f, 0);
        }

        final Signals.Stats st = lane.stats();
        final float[] range = range(st, lane);
        final Mapped map = new Mapped(plot, range[0], range[1], w.length);
        final boolean limitOffTop = lane.limit() && lane.limitValue() > range[1];
        final boolean limitOffBottom = lane.limit() && lane.limitValue() < range[0];

        normalBand(c, plot, map, st, detail);
        if (lane.limit() && !limitOffTop && !limitOffBottom) {
            limitMark(c, plot, map, lane, detail);
        }
        series(c, plot, map, w, lane.color(), reveal, detail);
        if (lane.yAxis().isFixed()) {
            dataOverflowMarkers(c, plot, st.hi > range[1], st.lo < range[0], lane.color());
        }
        if (limitOffTop || limitOffBottom) {
            limitOffEdge(c, plot, lane, limitOffTop);
        }
        if (axis) {
            valueAxis(c, r, plot, map, lane, detail);
        }
        if (headH > 0.0f) {
            laneHead(c, r, lane, detail);
        }
        return map;
    }

    private static float[] range(Signals.Stats st, Lane lane) {
        if (lane.yAxis().isFixed()) {
            return new float[]{lane.yAxis().min(), lane.yAxis().max()};
        }
        float lo = st.lo;
        float hi = st.hi;
        if (hi - lo < 1.0e-4f) {
            final float pad = Math.max(1.0f, Math.abs(hi) * 0.1f);
            lo -= pad;
            hi += pad;
        }
        final float span = hi - lo;
        lo -= span * 0.10f;
        hi += span * 0.10f;
        if (lane.limit()) {
            final float t = lane.limitValue();
            if (t <= hi + span && t >= lo - span) {
                lo = Math.min(lo, t - span * 0.08f);
                hi = Math.max(hi, t + span * 0.08f);
            }
        }
        return new float[]{lo, hi};
    }

    private static void dataOverflowMarkers(PanelSurface c, Rect plot, boolean high, boolean low,
                                            int color) {
        final float x = plot.right() - 7.0f;
        if (high) {
            c.tri(x, plot.y + 5.0f, color, x + 7.0f, plot.y + 5.0f, color,
                    x + 3.5f, plot.y, color);
        }
        if (low) {
            c.tri(x, plot.bottom() - 5.0f, color, x + 3.5f, plot.bottom(), color,
                    x + 7.0f, plot.bottom() - 5.0f, color);
        }
    }

    private static void laneHead(PanelSurface c, Rect r, Lane lane, float detail) {
        final Signals.Signal s = lane.signal();
        final float y = r.y + Tokens.S2 - 1.0f;
        Shapes.roundedRect(c, new Rect(r.x + Tokens.S3, y + 2.0f, 3.0f, 9.0f), 1.5f, lane.color());
        final String name = s.group.isEmpty() ? s.name : s.name + "  " + s.group;
        c.text(Text.truncate(c, name, r.w - 130.0f, Text.MICRO),
                r.x + Tokens.S3 + 8.0f, y, Tokens.INK_2, Text.MICRO);
        if (detail > 0.55f) {
            c.textRight(Signals.withUnit(s.value(), s.unit), r.right() - Tokens.S3, y,
                    Tokens.INK, Text.MICRO);
        }
    }

    private static void normalBand(PanelSurface c, Rect plot, Mapped map, Signals.Stats st,
                                   float detail) {
        if (st.count < 4) {
            return;
        }
        final float mid = Math.round(map.yFor(st.median));

        final boolean stationary = st.regime != Signals.Regime.RISING
                && st.regime != Signals.Regime.FALLING;
        final float top = map.yFor(st.p90);
        final float bottom = map.yFor(st.p10);
        if (stationary && bottom - top > 1.0f) {
            Shapes.rect(c, plot.x, top, plot.w, bottom - top, Tokens.alpha(Tokens.LINE_HI, 0.22f));
            Shapes.rect(c, plot.x, Math.round(top), plot.w, 1.0f, Tokens.alpha(Tokens.INK_3, 0.30f));
            Shapes.rect(c, plot.x, Math.round(bottom), plot.w, 1.0f,
                    Tokens.alpha(Tokens.INK_3, 0.30f));
        }
        Shapes.rect(c, plot.x, mid, plot.w, 1.0f, Tokens.alpha(Tokens.INK_3, 0.55f));
        if (stationary && detail > 0.7f && bottom - top > 13.0f) {
            c.text(Text.track("typical"), plot.x + 3.0f, mid - Text.lineHeight(Text.MICRO) - 1.0f,
                    Tokens.alpha(Tokens.INK_3, 0.9f), Text.MICRO);
        }
    }

    private static void limitMark(PanelSurface c, Rect plot, Mapped map, Lane lane, float detail) {
        final float y = Math.round(map.yFor(lane.limitValue()));
        final int color = lane.limitColor();
        if (lane.limitAbove()) {
            Shapes.rect(c, plot.x, plot.y, plot.w, Math.max(0.0f, y - plot.y),
                    Tokens.alpha(color, 0.09f));
        } else {
            Shapes.rect(c, plot.x, y, plot.w, Math.max(0.0f, plot.bottom() - y),
                    Tokens.alpha(color, 0.09f));
        }
        dashed(c, plot.x, y, plot.w, color);
        if (plot.w > 150.0f && plot.h > 30.0f) {
            final String label = (lane.limitAbove() ? "over " : "under ")
                    + Signals.withUnit(lane.limitValue(), lane.signal().unit);
            final float w = c.textWidth(label, Text.MICRO) + 10.0f;
            final float ly = Math.max(plot.y, Math.min(plot.bottom() - 13.0f, y - 6.5f));
            c.card(new Rect(plot.right() - w, ly, w, 13.0f), Tokens.R_CHIP,
                    Tokens.alpha(Tokens.CANVAS, 0.86f), Tokens.alpha(color, 0.7f));
            c.textCenterV(label, plot.right() - w * 0.5f, ly + 6.5f, color, Text.MICRO);
        }
    }

    private static void limitOffEdge(PanelSurface c, Rect plot, Lane lane, boolean top) {
        final int color = lane.limitColor();
        final float y = top ? plot.y + 1.0f : plot.bottom() - 2.0f;
        dashed(c, plot.x, y, plot.w, Tokens.alpha(color, 0.5f));
        final String label = (top ? "over " : "under ")
                + Signals.withUnit(lane.limitValue(), lane.signal().unit)
                + (top ? ", off the top" : ", off the bottom");
        final float w = c.textWidth(label, Text.MICRO) + 10.0f;
        final float ly = top ? plot.y + 3.0f : plot.bottom() - 16.0f;
        c.card(new Rect(plot.right() - w, ly, w, 13.0f), Tokens.R_CHIP,
                Tokens.alpha(Tokens.CANVAS, 0.86f), Tokens.alpha(color, 0.55f));
        c.textCenterV(label, plot.right() - w * 0.5f, ly + 6.5f, color, Text.MICRO);
    }

    private static void dashed(PanelSurface c, float x, float y, float w, int color) {
        final float dash = 5.0f;
        final float gap = 4.0f;
        for (float dx = 0.0f; dx < w; dx += dash + gap) {
            Shapes.rect(c, x + dx, y, Math.min(dash, w - dx), 1.0f, color);
        }
    }

    private static void series(PanelSurface c, Rect plot, Mapped map, float[] w, int color,
                               float reveal, float detail) {
        // one point every 5px, max 180. two per column blew the frame budget

        final int cols = Math.max(2, Math.min(180, Math.round(plot.w * 0.2f)));
        final PointBuffer points = points(w, map, plot, cols);
        final float[] xy = points.xy;
        final int count = points.count;
        final int drawn = reveal >= 0.999f ? count
                : Math.max(2, Math.min(count, Math.round(count * reveal)));

        Shapes.areaFill(c, xy, drawn, plot.bottom(),
                Tokens.alpha(color, 0.20f), Tokens.alpha(color, 0.0f));
        Shapes.polyline(c, xy, drawn, detail > 0.4f ? STROKE : 1.4f, color);

        if (drawn < count) {
            Shapes.rect(c, xy[(drawn - 1) * 2], plot.y, 1.0f, plot.h, Tokens.alpha(color, 0.35f));
            return;
        }
        com.createstats.statsmk3.instrument.Plot.endpoint(c, xy[(count - 1) * 2],
                xy[(count - 1) * 2 + 1], detail > 0.4f ? STROKE : 1.4f, color);
    }

    private static PointBuffer points(float[] w, Mapped map, Rect plot, int cols) {
        final PointBuffer points = POINTS.get();
        if (w.length <= cols) {
            points.ensure(w.length * 2);
            final float[] out = points.xy;
            for (int i = 0; i < w.length; i++) {
                out[i * 2] = plot.x + plot.w * (w.length < 2 ? 1.0f : i / (float) (w.length - 1));
                out[i * 2 + 1] = map.yFor(w[i]);
            }
            points.count = w.length;
            return points;
        }
        final int lanes = Math.max(1, cols / 2);
        points.ensure(lanes * 4);
        final float[] out = points.xy;
        int n = 0;
        for (int col = 0; col < lanes; col++) {
            final int from = (int) ((long) col * w.length / lanes);
            final int to = Math.max(from + 1, (int) ((long) (col + 1) * w.length / lanes));
            int loAt = from;
            int hiAt = from;
            for (int i = from; i < to; i++) {
                if (w[i] < w[loAt]) {
                    loAt = i;
                }
                if (w[i] > w[hiAt]) {
                    hiAt = i;
                }
            }
            final int first = Math.min(loAt, hiAt);
            final int second = Math.max(loAt, hiAt);
            out[n * 2] = plot.x + plot.w * (first / (float) (w.length - 1));
            out[n * 2 + 1] = map.yFor(w[first]);
            n++;
            if (second != first) {
                out[n * 2] = plot.x + plot.w * (second / (float) (w.length - 1));
                out[n * 2 + 1] = map.yFor(w[second]);
                n++;
            }
        }
        points.count = n;
        return points;
    }

    private static void valueAxis(PanelSurface c, Rect r, Rect plot, Mapped map, Lane lane,
                                  float detail) {
        final float x = plot.right() + 5.0f;
        for (int i = 0; i <= 2; i++) {
            final float t = i / 2.0f;
            final float value = map.lo() + (map.hi() - map.lo()) * (1.0f - t);
            final float y = plot.y + plot.h * t;
            Shapes.rect(c, plot.right(), Math.round(y), 3.0f, 1.0f, Tokens.LINE);
            c.textV(Text.compact(value), x, y, Tokens.INK_3, Text.MICRO);
        }
        if (detail > 0.5f) {
            final float y = map.yFor(lane.signal().value());
            Shapes.rect(c, plot.right(), Math.round(y), 4.0f, 1.0f, lane.color());
        }
    }

    public static void crosshair(PanelSurface c, Mapped map, Lane lane, int index) {
        if (map.count() < 2 || lane.window() == null || index < 0 || index >= lane.window().length) {
            return;
        }
        final float t = index / (float) (lane.window().length - 1);
        final float px = map.plot().x + map.plot().w * t;
        final float py = map.yFor(lane.window()[index]);
        Shapes.circle(c, px, py, 4.4f, Tokens.SURFACE);
        Shapes.circle(c, px, py, 2.8f, lane.color());

        final String label = Signals.withUnit(lane.window()[index], lane.signal().unit);
        final float w = c.textWidth(label, Text.MICRO) + 12.0f;
        final float bx = Math.max(map.plot().x, Math.min(px - w * 0.5f, map.plot().right() - w));
        final float by = Math.max(map.plot().y, py - 20.0f);
        c.card(new Rect(bx, by, w, 15.0f), Tokens.R_CHIP, Tokens.OVERLAY, Tokens.LINE_HI);
        c.textCenterV(label, bx + w * 0.5f, by + 7.5f, Tokens.INK, Text.MICRO);
    }

    public static void timeAxis(PanelSurface c, Rect r, float seconds, float pointerT) {
        if (r.isEmpty()) {
            return;
        }
        final float y = r.y + 1.0f;
        c.text(Signals.ago(seconds) + " ago", r.x, y, Tokens.INK_3, Text.MICRO);
        if (r.w > 220.0f) {
            c.textCenter(Signals.ago(seconds * 0.5f), r.centerX(), y, Tokens.INK_3, Text.MICRO);
        }
        c.textRight("now", r.right(), y, Tokens.INK_3, Text.MICRO);
        if (pointerT >= 0.0f) {
            final String at = Signals.ago(seconds * (1.0f - pointerT)) + " ago";
            final float w = c.textWidth(at, Text.MICRO) + 10.0f;
            final float bx = Math.max(r.x, Math.min(r.x + r.w * pointerT - w * 0.5f, r.right() - w));
            c.card(new Rect(bx, y - 2.0f, w, 13.0f), Tokens.R_CHIP, Tokens.OVERLAY, Tokens.LINE_HI);
            c.textCenterV(at, bx + w * 0.5f, y + 4.5f, Tokens.INK_2, Text.MICRO);
        }
    }

    public static void spark(PanelSurface c, Rect r, float[] w, int color,
                             boolean limit, float limitValue, boolean limitAbove, int limitColor) {
        if (r.isEmpty() || w == null || w.length == 0) {
            return;
        }
        if (w.length < 2) {
            Shapes.circle(c, r.centerX(), r.centerY(), 1.6f, color);
            return;
        }
        float lo = w[0];
        float hi = w[0];
        for (final float v : w) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (limit && limitValue <= hi + (hi - lo) && limitValue >= lo - (hi - lo)) {
            lo = Math.min(lo, limitValue);
            hi = Math.max(hi, limitValue);
        }
        final float span = hi - lo < 1.0e-5f ? 1.0f : hi - lo;
        final float pad = span * 0.12f;
        final float top = hi + pad;
        final float bottom = lo - pad;
        final float full = top - bottom;

        final int cols = Math.min(16, w.length);
        final float[] xy = new float[cols * 2];
        for (int i = 0; i < cols; i++) {
            final int from = (int) ((long) i * w.length / cols);
            final int to = Math.max(from + 1, (int) ((long) (i + 1) * w.length / cols));
            float peak = w[from];
            for (int k = from; k < to; k++) {
                if (Math.abs(w[k] - (top + bottom) * 0.5f) > Math.abs(peak - (top + bottom) * 0.5f)) {
                    peak = w[k];
                }
            }
            xy[i * 2] = r.x + r.w * (cols < 2 ? 1.0f : i / (float) (cols - 1));
            xy[i * 2 + 1] = r.bottom() - (peak - bottom) / full * r.h;
        }

        if (limit) {
            final float ly = r.bottom() - (limitValue - bottom) / full * r.h;
            if (ly > r.y - 2.0f && ly < r.bottom() + 2.0f) {
                final float clamped = Math.max(r.y, Math.min(r.bottom(), ly));
                Shapes.rect(c, r.x, limitAbove ? r.y : clamped, r.w,
                        limitAbove ? clamped - r.y : r.bottom() - clamped,
                        Tokens.alpha(limitColor, 0.14f));
                Shapes.rect(c, r.x, Math.round(clamped), r.w, 1.0f,
                        Tokens.alpha(limitColor, 0.75f));
            }
        }
        Shapes.areaFill(c, xy, cols, r.bottom(),
                Tokens.alpha(color, 0.22f), Tokens.alpha(color, 0.02f));
        Shapes.polyline(c, xy, cols, 1.0f, color);
    }

    public static void pill(PanelSurface c, Rect r, String label, int color, float fill) {
        c.card(r, r.h * 0.5f, Tokens.alpha(color, 0.10f * fill), Tokens.alpha(color, 0.45f));
        Shapes.circle(c, r.x + r.h * 0.5f, r.centerY(), 2.6f, color);
        c.textV(label, r.x + r.h * 0.5f + 6.0f, r.centerY(), color, Text.MICRO);
    }

    public static float pillWidth(PanelSurface c, String label, float height) {
        return c.textWidth(label, Text.MICRO) + height * 0.5f + 14.0f;
    }

    public static void regime(PanelSurface c, float x, float centerY, Signals.Stats st,
                              String unit) {
        final int color = st.regime == Signals.Regime.STALE ? Tokens.CAUTION : Tokens.INK_2;
        float cursor = x;
        switch (st.regime) {
            case RISING -> {
                arrow(c, cursor + 3.0f, centerY, true, color);
                cursor += 11.0f;
            }
            case FALLING -> {
                arrow(c, cursor + 3.0f, centerY, false, color);
                cursor += 11.0f;
            }
            case SWINGING -> {
                Shapes.line(c, cursor, centerY + 2.0f, cursor + 3.0f, centerY - 2.0f, 1.2f, color);
                Shapes.line(c, cursor + 3.0f, centerY - 2.0f, cursor + 6.0f, centerY + 2.0f, 1.2f,
                        color);
                cursor += 11.0f;
            }
            case STEADY -> {
                Shapes.rect(c, cursor, Math.round(centerY), 7.0f, 1.0f, color);
                cursor += 11.0f;
            }
            default -> {
            }
        }
        c.textV(st.regime.word(), cursor, centerY, color, Text.MICRO);
        if (st.regime == Signals.Regime.RISING || st.regime == Signals.Regime.FALLING) {
            final float w = c.textWidth(st.regime.word(), Text.MICRO);
            c.textV(Signals.rate(st.perMinute, unit), cursor + w + 7.0f, centerY,
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private static void arrow(PanelSurface c, float cx, float cy, boolean up, int color) {
        final float tip = up ? cy - 3.5f : cy + 3.5f;
        final float tail = up ? cy + 3.5f : cy - 3.5f;
        final float barb = up ? tip + 2.6f : tip - 2.6f;
        Shapes.line(c, cx, tail, cx, tip, 1.2f, color);
        Shapes.line(c, cx, tip, cx - 2.4f, barb, 1.2f, color);
        Shapes.line(c, cx, tip, cx + 2.4f, barb, 1.2f, color);
    }

    public static void empty(PanelSurface c, Rect r, String head, String hint) {
        final float cy = r.centerY() - 10.0f;
        c.textCenter(head, r.centerX(), cy, Tokens.INK_2, Text.SUB);
        c.textCenter(hint, r.centerX(), cy + Text.lineHeight(Text.SUB) + 6.0f,
                Tokens.INK_3, Text.META);
    }

    public static void tile(PanelSurface c, Rect r, String label, String value, String note,
                            int color) {
        if (r.isEmpty()) {
            return;
        }
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track(label), r.x + Tokens.S2, r.y + 6.0f, Tokens.INK_3, Text.MICRO);
        final float room = r.w - Tokens.S2 * 2.0f;

        final float scale = Text.fitScale(c, value, room, 14.0f, Text.BODY, Text.MICRO);
        c.text(Text.truncate(c, value, room, scale), r.x + Tokens.S2, r.y + 18.0f, color, scale);
        if (note != null && !note.isEmpty() && r.h > 40.0f) {
            c.text(Text.truncate(c, note, room, Text.MICRO), r.x + Tokens.S2, r.y + 32.0f,
                    Tokens.INK_3, Text.MICRO);
        }
    }
}
