package com.createstats.statsmk3.instrument;

import java.util.Locale;

import com.createstats.ground.DriveMath;
import com.createstats.statsmk3.page.DriveKit;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DriveInstruments {
    private DriveInstruments() {
    }

    static ChannelView partner(InstrumentCtx ctx, String metric) {
        final String want = "_auto_drive_" + metric;
        final String own = ctx.primary == null ? "" : ctx.primary.id;
        if (own.endsWith(want)) {
            return ctx.primary;
        }
        final int cut = own.indexOf("_auto_");
        final String prefix = cut >= 0 ? own.substring(0, cut + "_auto_".length()) : null;
        ChannelView any = null;
        for (final ChannelView ch : ctx.series()) {
            if (ch.id.endsWith(want)) {
                if (prefix != null && ch.id.startsWith(prefix)) {
                    return ch;
                }
                if (any == null) {
                    any = ch;
                }
            }
        }
        return any;
    }

    static double valueOf(ChannelView ch) {
        return ch == null || ch.stale ? Double.NaN : ch.value;
    }

    static boolean tooSmall(Rect area, float min) {
        return area.w < min || area.h < min;
    }

    static char gearLetter(double code) {
        if (!Double.isFinite(code)) {
            return '-';
        }
        return switch ((int) Math.round(code)) {
            case 0 -> 'P';
            case 1 -> 'H';
            case 2 -> 'N';
            case 3 -> 'D';
            case 4 -> 'R';
            case 5 -> 'B';
            default -> '-';
        };
    }

    static double speedScale(InstrumentCtx ctx, double kmh) {
        double most = Double.isFinite(kmh) ? kmh * 1.1 : 0.0;
        final double cruise = valueOf(partner(ctx, "cruise"));
        if (Double.isFinite(cruise)) {
            most = Math.max(most, cruise * DriveMath.KMH * 1.2);
        }
        final double top = valueOf(partner(ctx, "top_speed"));
        if (Double.isFinite(top)) {
            most = Math.max(most, top * DriveMath.KMH * 1.1);
        }
        return Math.max(20.0, Math.ceil(most / 20.0) * 20.0);
    }

    public static final class Speedo implements Instrument {
        private static final float A0 = (float) (Math.PI * 0.78);
        private static final float A1 = (float) (Math.PI * 2.22);

        @Override
        public String id() {
            return "drive_speedo";
        }

        @Override
        public String displayName() {
            return "Speedometer";
        }

        @Override
        public String description() {
            return "km/h, dial or tape by shape";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 32.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView kmhCh = partner(ctx, "speed_kmh");
            final ChannelView bpsCh = kmhCh == null ? partner(ctx, "speed") : null;
            final boolean drive = kmhCh != null || bpsCh != null;
            if (tooSmall(area, minSize()) || !drive) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final double kmh = kmhCh != null ? valueOf(kmhCh) : Math.abs(valueOf(bpsCh)) * DriveMath.KMH;
            final double max = speedScale(ctx, kmh);
            final double cruise = valueOf(partner(ctx, "cruise"));
            final double cruiseKmh = Double.isFinite(cruise) ? cruise * DriveMath.KMH : Double.NaN;
            final boolean alert = ctx.primary.alerting;
            if (area.w >= area.h * 2.2f) {
                tape(s, area, kmh, max, cruiseKmh, alert, ctx);
            } else {
                dial(s, area, kmh, max, cruiseKmh, alert, ctx);
            }
        }

        private static void dial(PanelSurface s, Rect area, double kmh, double max, double cruiseKmh, boolean alert,
                                 InstrumentCtx ctx) {
            final float radius = Math.max(8.0f, Math.min(area.w * 0.5f, area.h / 1.64f) * 0.9f);
            final float cx = area.centerX();
            final float cy = area.y + area.h * 0.5f + radius * 0.16f;
            final float thick = Math.max(2.0f, Math.min(8.0f, radius * 0.13f));
            Shapes.arc(s, cx, cy, radius, thick, A0, A1 - A0, Tokens.LINE);
            s.advanceLayer();
            final float frac = Double.isFinite(kmh) ? (float) DriveMath.clamp(kmh / max, 0.0, 1.0) : 0.0f;
            final int color = alert ? Tokens.CRITICAL : Tokens.SERIES[0];
            if (frac > 0.002f) {
                Shapes.arc(s, cx, cy, radius, thick, A0, (A1 - A0) * frac, color);
            }

            final int majors = (int) Math.round(max / 20.0);
            for (int i = 0; i <= majors; i++) {
                final float a = A0 + (A1 - A0) * i / Math.max(1, majors);
                final float ri = radius - thick * 0.9f;
                final float ro = ri - Math.max(2.0f, radius * 0.08f);
                Shapes.line(s, cx + (float) Math.cos(a) * ri, cy + (float) Math.sin(a) * ri, cx + (float) Math.cos(a) * ro,
                        cy + (float) Math.sin(a) * ro, 1.0f, Tokens.INK_3);
                if (radius >= 44.0f && ctx.lod.showsLabels() && (majors <= 6 || i % 2 == 0)) {
                    final float rl = ro - 6.0f;
                    s.textCenterV(Integer.toString(i * 20), cx + (float) Math.cos(a) * rl, cy + (float) Math.sin(a) * rl,
                            Tokens.INK_3, Text.MICRO);
                }
            }
            if (Double.isFinite(cruiseKmh) && cruiseKmh <= max) {
                final float a = A0 + (A1 - A0) * (float) (cruiseKmh / max);
                Shapes.line(s, cx + (float) Math.cos(a) * (radius + thick * 0.2f), cy + (float) Math.sin(a) * (radius + thick * 0.2f),
                        cx + (float) Math.cos(a) * (radius - thick * 1.2f), cy + (float) Math.sin(a) * (radius - thick * 1.2f),
                        1.5f, Tokens.INK_2);
            }
            s.advanceLayer();
            final float na = A0 + (A1 - A0) * frac;
            Shapes.line(s, cx, cy, cx + (float) Math.cos(na) * (radius - thick * 1.4f),
                    cy + (float) Math.sin(na) * (radius - thick * 1.4f), 1.8f, Tokens.INK);
            Shapes.circle(s, cx, cy, Math.max(2.0f, radius * 0.06f), Tokens.INK);
            if (radius >= 22.0f) {
                final String text = Double.isFinite(kmh) ? Long.toString(Math.round(kmh)) : "--";
                final float scale = Math.max(Text.META, Math.min(Text.TITLE * 1.5f, radius / 26.0f));
                s.textCenterV(text, cx, cy + radius * 0.46f, alert ? Tokens.CRITICAL : Tokens.INK, scale);
                if (ctx.lod.showsLabels() && radius >= 34.0f) {
                    s.textCenterV("km/h", cx, cy + radius * 0.46f + Text.lineHeight(scale) * 0.5f + 6.0f, Tokens.INK_3,
                            Text.MICRO);
                }
            }
        }

        private static void tape(PanelSurface s, Rect area, double kmh, double max, double cruiseKmh, boolean alert,
                                 InstrumentCtx ctx) {
            final String text = Double.isFinite(kmh) ? Long.toString(Math.round(kmh)) : "--";
            final float scale = Math.max(Text.META, Math.min(Text.TITLE * 1.5f, area.h / Text.LINE_HEIGHT * 0.6f));
            final float numW = s.textWidth("188", scale) + 4.0f;
            s.textRight(text, area.x + numW, area.centerY() - Text.lineHeight(scale) * 0.5f, alert ? Tokens.CRITICAL
                    : Tokens.INK, scale);
            final boolean unit = ctx.lod.showsLabels() && area.h >= 18.0f;
            final float unitW = unit ? s.textWidth("km/h", Text.MICRO) + 6.0f : 0.0f;
            if (unit) {
                s.text("km/h", area.x + numW + 3.0f, area.centerY() + Text.lineHeight(scale) * 0.5f
                        - Text.lineHeight(Text.MICRO), Tokens.INK_3, Text.MICRO);
            }
            final float bx = area.x + numW + unitW + 4.0f;
            final float bh = Math.max(3.0f, Math.min(10.0f, area.h * 0.28f));
            final Rect bar = new Rect(bx, area.centerY() - bh * 0.5f, Math.max(4.0f, area.right() - bx - 2.0f), bh);
            DriveKit.pedal(s, bar, Double.isFinite(kmh) ? kmh / max : Double.NaN, false,
                    alert ? Tokens.CRITICAL : Tokens.SERIES[0]);
            if (Double.isFinite(cruiseKmh) && cruiseKmh <= max) {
                final float x = bar.x + (float) (cruiseKmh / max) * bar.w;
                Shapes.rect(s, x - 0.75f, bar.y - 2.0f, 1.5f, bar.h + 4.0f, Tokens.INK_2);
            }
        }
    }

    public static final class Gear implements Instrument {
        @Override
        public String id() {
            return "drive_gear";
        }

        @Override
        public String displayName() {
            return "Gear";
        }

        @Override
        public String description() {
            return "the dash's gear letter";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 16.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView gear = partner(ctx, "gear");
            if (gear == null || tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final float h = Math.min(area.h, area.w * 1.25f) * 0.9f;
            final float w = h / 1.25f;
            DriveKit.gear(s, new Rect(area.centerX() - w * 0.5f, area.centerY() - h * 0.5f, w, h),
                    gearLetter(valueOf(gear)), DriveKit.Look.CLUSTER, false);
        }
    }

    public static final class Gg implements Instrument {
        @Override
        public String id() {
            return "drive_gg";
        }

        @Override
        public String displayName() {
            return "G-g circle";
        }

        @Override
        public String description() {
            return "cornering against braking";
        }

        @Override
        public float minSize() {
            return 30.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView lat = partner(ctx, "lateral_g");
            final ChannelView lon = partner(ctx, "longitudinal_g");
            if ((lat == null && lon == null) || tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final float rad = Math.min(area.w, area.h) * 0.5f - 2.0f;
            final float cx = area.centerX();
            final float cy = area.centerY();

            double peak = 1.0;
            peak = Math.max(peak, peakOf(lat));
            peak = Math.max(peak, peakOf(lon));
            final float k = (float) (rad / Math.min(2.5, peak * 1.1));
            Shapes.circle(s, cx, cy, rad, Tokens.SURFACE);
            Shapes.arc(s, cx, cy, k * 1.0f, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE_HI);
            Shapes.arc(s, cx, cy, k * 0.5f, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE);
            Shapes.line(s, cx - rad, cy, cx + rad, cy, 1.0f, Tokens.LINE);
            Shapes.line(s, cx, cy - rad, cx, cy + rad, 1.0f, Tokens.LINE);
            s.advanceLayer();

            if (lat != null && lon != null && lat.historyCount >= 2 && lon.historyCount >= 2) {
                final int n = Math.min(Math.min(lat.historyCount, lon.historyCount), 40);
                for (int i = 0; i < n; i++) {
                    final float x = lat.history[lat.historyCount - n + i];
                    final float y = lon.history[lon.historyCount - n + i];
                    if (!Float.isFinite(x) || !Float.isFinite(y)) {
                        continue;
                    }
                    final float px = cx + clampTo(x * k, rad);
                    final float py = cy - clampTo(y * k, rad);
                    Shapes.circle(s, px, py, Math.max(1.0f, rad * 0.03f), Tokens.alpha(Tokens.SERIES[0], 0.12f + 0.5f * i / n));
                }
            }
            final double x = valueOf(lat);
            final double y = valueOf(lon);
            final float px = cx + clampTo((float) ((Double.isFinite(x) ? x : 0.0) * k), rad);
            final float py = cy - clampTo((float) ((Double.isFinite(y) ? y : 0.0) * k), rad);
            Shapes.circle(s, px, py, Math.max(2.0f, rad * 0.08f), ctx.primary.alerting ? Tokens.CRITICAL : Tokens.SERIES[0]);
            if (ctx.lod.showsLabels() && rad >= 34.0f) {
                final String missing = lat == null ? "no lateral g" : lon == null ? "no braking g" : null;
                if (missing != null) {
                    s.textCenter(missing, cx, area.bottom() - 11.0f, Tokens.INK_3, Text.MICRO);
                } else {
                    s.textCenter(String.format(Locale.ROOT, "%.2f g", Math.hypot(Double.isFinite(x) ? x : 0.0,
                            Double.isFinite(y) ? y : 0.0)), cx, area.bottom() - 11.0f, Tokens.INK_2, Text.MICRO);
                }
            }
        }

        private static double peakOf(ChannelView ch) {
            if (ch == null) {
                return 0.0;
            }
            double m = 0.0;
            for (int i = Math.max(0, ch.historyCount - 40); i < ch.historyCount; i++) {
                if (Float.isFinite(ch.history[i])) {
                    m = Math.max(m, Math.abs(ch.history[i]));
                }
            }
            return m;
        }

        private static float clampTo(float v, float rad) {
            return Math.max(-rad, Math.min(rad, v));
        }
    }

    public static final class Steer implements Instrument {
        @Override
        public String id() {
            return "drive_steer";
        }

        @Override
        public String displayName() {
            return "Steering";
        }

        @Override
        public String description() {
            return "the stick, centre zero";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 24.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView steer = partner(ctx, "steer");
            if (steer == null || tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final double pct = valueOf(steer);

            final float thick = Math.max(2.0f, Math.min(7.0f, Math.min(area.w, area.h) * 0.08f));
            final float rad = Math.max(4.0f, Math.min(area.w * 0.5f - thick - 1.0f, area.h - thick * 2.0f - 2.0f));
            final float cx = area.centerX();
            final float cy = area.y + (area.h - rad - thick * 2.0f) * 0.5f + rad + thick;

            final float from = (float) Math.PI;
            Shapes.arc(s, cx, cy, rad, thick, from, (float) Math.PI, Tokens.LINE);
            s.advanceLayer();
            final double f = Double.isFinite(pct) ? DriveMath.clamp(pct / 100.0, -1.0, 1.0) : 0.0;
            final float mid = (float) (Math.PI * 1.5);
            if (Math.abs(f) > 0.005) {
                final float sweep = (float) (f * Math.PI * 0.5);
                Shapes.arc(s, cx, cy, rad, thick, sweep >= 0 ? mid : mid + sweep, Math.abs(sweep),
                        ctx.primary.alerting ? Tokens.CRITICAL : Tokens.SERIES[0]);
            }
            Shapes.rect(s, cx - 0.75f, cy - rad - thick, 1.5f, thick * 2.0f, Tokens.INK_2);
            final float a = mid + (float) (f * Math.PI * 0.5);
            Shapes.circle(s, cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad, thick * 0.8f, Tokens.INK);
            if (rad >= 20.0f) {
                final String text = Double.isFinite(pct) ? String.format(Locale.ROOT, "%+.0f%%", pct) : "--";
                s.textCenterV(text, cx, cy - rad * 0.35f, Tokens.INK, Math.max(Text.META, Math.min(Text.SUB, rad / 30.0f)));
            }
        }
    }

    public static final class Telltales implements Instrument {
        @Override
        public String id() {
            return "drive_telltales";
        }

        @Override
        public String displayName() {
            return "Tell-tales";
        }

        @Override
        public String description() {
            return "indicators and lamps";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 12.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            final ChannelView gear = partner(ctx, "gear");
            final ChannelView brake = partner(ctx, "brake");
            if ((gear == null && brake == null) || area.h < minSize() || area.w < 40.0f) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final char g = gearLetter(valueOf(gear));
            final double indicator = valueOf(partner(ctx, "indicator"));
            // blinks at the lamps' rate, not their phase. only the client clock here

            final boolean blink = Math.floorMod((int) (ctx.time * 20.0f / 8.0f), 2) == 0;
            final boolean left = Double.isFinite(indicator) && indicator < -0.5 && blink;
            final boolean right = Double.isFinite(indicator) && indicator > 0.5 && blink;
            final String[] legends = {"<", "BRK", "BST", "HLD", "PRK", "BEAM", ">"};
            final boolean[] lit = {left, valueOf(brake) > 0.5 && g != 'H' && g != 'P', valueOf(partner(ctx, "boost")) > 0.5,
                    g == 'H', g == 'P', valueOf(partner(ctx, "beam")) > 0.5, right};
            final int[] colors = {Tokens.LAMP_AMBER, Tokens.CRITICAL, Tokens.ACCENT, Tokens.CAUTION, Tokens.CAUTION,
                    Tokens.BEAM_BLUE, Tokens.LAMP_AMBER};

            final float capPerScale = Text.LINE_HEIGHT / 0.72f;
            float atOne = capPerScale * 1.1f * 2.0f;
            for (int i = 1; i < legends.length - 1; i++) {
                atOne += s.textWidth(legends[i], 1.0f) + capPerScale * 0.5f;
            }
            final float scale = Math.min(Math.min(Text.TITLE, area.h * 0.9f / capPerScale), area.w * 0.9f / atOne);
            if (scale < 0.6f) {
                compact(s, area, legends, lit, colors);
                return;
            }
            final float h = capPerScale * scale;
            final float y = area.centerY() - h * 0.5f;
            final float[] widths = new float[legends.length];
            float total = 0.0f;
            for (int i = 0; i < legends.length; i++) {
                widths[i] = i == 0 || i == legends.length - 1 ? h * 1.1f : s.textWidth(legends[i], scale) + h * 0.5f;
                total += widths[i];
            }
            final float spread = Math.max(0.0f, (area.w - total) / (legends.length - 1));
            float x = area.x;
            for (int i = 0; i < legends.length; i++) {
                final Rect cap = new Rect(x, y, widths[i], h);
                x += widths[i] + spread;
                if (i == 0 || i == legends.length - 1) {
                    chevron(s, cap, i == 0, lit[i] ? colors[i] : Tokens.alpha(Tokens.INK_3, 0.28f));
                } else if (lit[i]) {
                    Shapes.roundedRect(s, cap, Tokens.R_CHIP, Tokens.alpha(colors[i], 0.18f));
                    s.textCenterV(legends[i], cap.centerX(), cap.centerY(), colors[i], scale);
                }
            }
        }

        private static void compact(PanelSurface s, Rect area, String[] legends, boolean[] lit, int[] colors) {
            final float h = Math.min(area.h * 0.8f, area.w * 0.28f);
            final float y = area.centerY() - h * 0.5f;
            final float aw = h * 1.1f * 0.7f;
            chevron(s, new Rect(area.x, y, aw, h), true, lit[0] ? colors[0] : Tokens.alpha(Tokens.INK_3, 0.28f));
            chevron(s, new Rect(area.right() - aw, y, aw, h), false,
                    lit[legends.length - 1] ? colors[legends.length - 1] : Tokens.alpha(Tokens.INK_3, 0.28f));
            for (final int i : new int[]{1, 4, 3, 2, 5}) {
                if (!lit[i]) {
                    continue;
                }
                final Rect mid = new Rect(area.x + aw + 2.0f, y, Math.max(1.0f, area.w - aw * 2.0f - 4.0f), h);
                final float scale = Math.max(0.4f, Math.min(Text.TITLE, Math.min(mid.h * 0.72f / Text.LINE_HEIGHT,
                        mid.w * 0.85f / Math.max(1.0f, s.textWidth(legends[i], 1.0f)))));
                s.textCenterV(legends[i], mid.centerX(), mid.centerY(), colors[i], scale);
                return;
            }
        }

        private static void chevron(PanelSurface s, Rect r, boolean left, int col) {
            final float cx = r.centerX();
            final float cy = r.centerY();
            final float hw = r.w * 0.34f;
            final float hh = r.h * 0.34f;
            if (left) {
                s.tri(cx - hw, cy, col, cx + hw * 0.6f, cy - hh, col, cx + hw * 0.6f, cy + hh, col);
            } else {
                s.tri(cx + hw, cy, col, cx - hw * 0.6f, cy + hh, col, cx - hw * 0.6f, cy - hh, col);
            }
        }
    }
}
