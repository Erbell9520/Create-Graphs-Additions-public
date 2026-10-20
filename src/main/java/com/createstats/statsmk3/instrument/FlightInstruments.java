package com.createstats.statsmk3.instrument;

import java.util.Locale;

import com.createstats.aerial.AirframeMath;
import com.createstats.statsmk3.page.AeroInstruments;
import com.createstats.statsmk3.paint.ChannelView;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// tiles bind one channel, so instruments find their partner channel by id
@OnlyIn(Dist.CLIENT)
public final class FlightInstruments {
    private FlightInstruments() {
    }

    static ChannelView partner(InstrumentCtx ctx, String metric) {
        final String want = "_auto_aero_" + metric;
        final String own = ctx.primary == null ? "" : ctx.primary.id;
        final int cut = own.indexOf("_auto_");
        final String prefix = cut >= 0 ? own.substring(0, cut + "_auto_".length()) : null;
        if (own.endsWith(want)) {
            return ctx.primary;
        }
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

    static boolean tooSmall(Rect area, float min) {
        return area.w < min || area.h < min;
    }

    static double valueOf(ChannelView ch) {
        return ch == null || ch.stale ? Double.NaN : ch.value;
    }

    public static final class Attitude implements Instrument {
        @Override
        public String id() {
            return "attitude";
        }

        @Override
        public String displayName() {
            return "Attitude";
        }

        @Override
        public String description() {
            return "pitch and bank, as a ball";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 36.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            if (tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final ChannelView pitchCh = partner(ctx, "pitch");
            final ChannelView bankCh = partner(ctx, "bank");
            final double pitch = pitchCh != null ? valueOf(pitchCh) : valueOf(ctx.primary);
            final double bank = bankCh != null ? valueOf(bankCh) : 0.0;
            final float side = Math.min(area.w, area.h);
            final Rect ball = new Rect(area.centerX() - side * 0.5f, area.centerY() - side * 0.5f, side, side);
            AeroInstruments.attitude(s, ball, Double.isFinite(pitch) ? AirframeMath.clamp(pitch, -60.0, 60.0) : 0.0,
                    Double.isFinite(bank) ? bank : 0.0, 0.0, Double.NaN, Double.NaN, ctx.primary.alerting);
            if (bankCh == null && side >= 60.0f && ctx.lod.showsLabels()) {
                s.textCenter("no bank", ball.centerX(), ball.bottom() - 11.0f, Tokens.INK_3, Text.META);
            }
        }
    }

    public static final class Heading implements Instrument {
        @Override
        public String id() {
            return "heading";
        }

        @Override
        public String displayName() {
            return "Heading";
        }

        @Override
        public String description() {
            return "tape or rose, by the tile's shape";
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
            if (tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final ChannelView ch = partner(ctx, "heading");
            final double heading = valueOf(ch != null ? ch : ctx.primary);
            if (area.w >= area.h * 1.6f) {
                final Rect tape = new Rect(area.x, area.centerY() - Math.min(area.h, 26.0f) * 0.5f + 4.0f, area.w,
                        Math.min(area.h - 8.0f, 22.0f));
                AeroInstruments.headingTape(s, tape, AirframeMath.wrap360(heading), Double.NaN, Double.NaN);
                return;
            }
            rose(s, area, heading, ctx);
        }

        static void rose(PanelSurface s, Rect area, double heading, InstrumentCtx ctx) {
            final float cx = area.centerX();
            final float cy = area.centerY();
            final float r = Math.min(area.w, area.h) * 0.5f - 2.0f;
            Shapes.circle(s, cx, cy, r, Tokens.SURFACE);
            Shapes.arc(s, cx, cy, r, 1.0f, 0.0f, (float) (Math.PI * 2.0), Tokens.LINE_HI);
            s.advanceLayer();
            if (!Double.isFinite(heading)) {
                s.textCenterV("--", cx, cy, Tokens.INK_3, Text.META);
                return;
            }
            for (int d = 0; d < 360; d += 10) {
                final double a = Math.toRadians(d - heading - 90.0);
                final float len = d % 30 == 0 ? 5.0f : 2.5f;
                final float ca = (float) Math.cos(a);
                final float sa = (float) Math.sin(a);
                Shapes.line(s, cx + ca * (r - 1.0f), cy + sa * (r - 1.0f), cx + ca * (r - 1.0f - len), cy + sa * (r - 1.0f - len),
                        1.0f, Tokens.INK_2);
                if (d % 90 == 0 && r >= 22.0f && ctx.lod.showsLabels()) {
                    final String l = d == 0 ? "N" : d == 90 ? "E" : d == 180 ? "S" : "W";
                    s.textCenterV(l, cx + ca * (r - 12.0f), cy + sa * (r - 12.0f), d == 0 ? Tokens.INK : Tokens.INK_2, Text.META);
                }
            }

            s.tri(cx, cy - r + 1.0f, Tokens.ACCENT, cx - 4.0f, cy - r - 4.0f, Tokens.ACCENT, cx + 4.0f, cy - r - 4.0f,
                    Tokens.ACCENT);
            if (r >= 18.0f) {
                final String hdg = String.format(Locale.ROOT, "%03d", Math.round(AirframeMath.wrap360(heading)) % 360);
                s.textCenterV(hdg, cx, cy, Tokens.INK, r >= 34.0f ? Text.SUB : Text.META);
            }
        }
    }

    public static final class Vsi implements Instrument {
        @Override
        public String id() {
            return "vsi";
        }

        @Override
        public String displayName() {
            return "Vertical speed";
        }

        @Override
        public String description() {
            return "square-root scale";
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
            if (tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final ChannelView ch = partner(ctx, "vertical_speed");
            final ChannelView use = ch != null ? ch : ctx.primary;

            final double limit = ch != null ? 6.0 : Math.max(1.0, Math.max(Math.abs(use.min), Math.abs(use.max)));
            final float w = Math.min(area.w, Math.max(28.0f, area.h * 0.42f));
            AeroInstruments.vsi(s, new Rect(area.centerX() - w * 0.5f, area.y, w, area.h), valueOf(use), limit);
        }
    }

    public static final class Wind implements Instrument {
        @Override
        public String id() {
            return "wind";
        }

        @Override
        public String displayName() {
            return "Wind";
        }

        @Override
        public String description() {
            return "where it comes from, and how hard";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 28.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            if (tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final ChannelView speedCh = partner(ctx, "wind");
            final ChannelView fromCh = partner(ctx, "wind_from");
            final double speed = valueOf(speedCh != null ? speedCh : ctx.primary);
            final double from = valueOf(fromCh);
            final boolean words = area.h >= 50.0f && ctx.lod.showsLabels();
            final float r = Math.min(area.w, area.h - (words ? 12.0f : 0.0f)) * 0.5f - 3.0f;
            final float cx = area.centerX();
            final float cy = area.y + r + 3.0f;
            if (r >= 8.0f) {
                s.textCenter("N", cx, cy - r + 1.0f, Tokens.INK_3, Text.META);

                AeroInstruments.windArrow(s, cx, cy, r, Double.isFinite(from) && speed >= 0.1 ? from + 180.0 : Double.NaN,
                        AeroInstruments.BLUE);
            }
            if (words) {
                final String t = !Double.isFinite(speed) ? "no air data"
                        : speed < 0.1 ? "calm" : String.format(Locale.ROOT, "%.1f b/s", speed);
                if (s.textWidth(t, Text.META) <= area.w) {
                    s.textCenter(t, cx, area.bottom() - 10.0f, Tokens.INK_2, Text.META);
                }
            }
        }
    }

    public static final class AltTape implements Instrument {
        @Override
        public String id() {
            return "alt_tape";
        }

        @Override
        public String displayName() {
            return "Altitude tape";
        }

        @Override
        public String description() {
            return "height, held, ceiling and ground";
        }

        @Override
        public boolean needsHistory() {
            return false;
        }

        @Override
        public float minSize() {
            return 28.0f;
        }

        @Override
        public void draw(PanelSurface s, Rect area, InstrumentCtx ctx) {
            if (tooSmall(area, minSize())) {
                Instruments.Numeric.drawValue(s, area, ctx.primary, ctx);
                return;
            }
            final ChannelView altCh = partner(ctx, "altitude");
            final ChannelView use = altCh != null ? altCh : ctx.primary;
            final double alt = valueOf(use);
            final double held = valueOf(partner(ctx, "held_altitude"));
            final double agl = valueOf(partner(ctx, "agl"));
            final double ceiling = valueOf(partner(ctx, "ceiling"));
            final double vs = valueOf(partner(ctx, "vertical_speed"));
            final double ground = Double.isFinite(alt) && Double.isFinite(agl) ? alt - agl : Double.NaN;
            final float w = Math.min(area.w, Math.max(40.0f, area.h * 0.45f));
            final Rect tape = new Rect(area.centerX() - w * 0.5f, area.y + (area.h >= 60.0f ? 10.0f : 0.0f), w,
                    area.h - (area.h >= 60.0f ? 10.0f : 0.0f));
            AeroInstruments.tape(s, tape, alt, 2.0, 5, false, new AeroInstruments.TapeMarks(ceiling,
                    Double.isFinite(ceiling) ? ceiling + 1000.0 : Double.NaN, Tokens.LIMIT, ground, held,
                    Double.isFinite(vs) ? vs * 6.0 : Double.NaN, area.h >= 60.0f && ctx.lod.showsLabels() ? "Y" : null, null,
                    use.alerting ? Tokens.CRITICAL : Tokens.INK));
        }
    }
}
