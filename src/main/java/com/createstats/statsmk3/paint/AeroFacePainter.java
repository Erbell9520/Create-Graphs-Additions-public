package com.createstats.statsmk3.paint;

import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.page.AeroSkin;
import com.createstats.statsmk3.page.AeroInstruments;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class AeroFacePainter {
    static final float REFERENCE_SIDE = 180.0f;
    static final float MAX_SCALE = 2.2f;

    private AeroFacePainter() {
    }

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        Shapes.rect(s, area, model.style.ground);
        s.advanceLayer();
        final AerialFrame f = model.aero;
        if (f != null && lod.isGlyph()) {
            glyph(s, area, f);
            return;
        }
        if (f != null) {
            AeroBridgeFace.paint(s, area, f, lod, AeroSkin.DECK, model.faceMode);
            return;
        }
        final float k = scaleFor(area);
        noFeed(new ScaledSurface(s, area.x, area.y, k), ScaledSurface.virtual(area, k), model.aeroLinked);
    }

    static float scaleFor(Rect area) {
        final float side = Math.max(1.0f, Math.min(area.w, area.h));
        return (float) AirframeMath.clamp(Math.sqrt(side / REFERENCE_SIDE), 1.0, MAX_SCALE);
    }

    static void noFeed(PanelSurface s, Rect area, boolean linked) {
        final String head = linked ? "WAITING" : "NO AERIAL";
        final float room = area.w - 8.0f;
        final float scale = Math.max(0.5f, Math.min(Math.min(Text.TITLE, area.h / 60.0f),
                room / Math.max(1.0f, s.textWidth(head, 1.0f))));
        final String hint = fitting(s, room, Text.MICRO, linked
                ? new String[]{"for the Aerial's first report", "for its first report", "no report yet"}
                : new String[]{"Display Link a VCC Mk IV Aerial", "link an Aerial", "no link"});
        final float lh = Text.lineHeight(scale);
        final float hh = hint == null ? 0.0f : Text.lineHeight(Text.MICRO) + 3.0f;
        final float top = area.centerY() - (lh + hh) * 0.5f;
        s.textCenter(head, area.centerX(), top, Tokens.INK_3, scale);
        if (hint != null && lh + hh <= area.h - 4.0f) {
            s.textCenter(hint, area.centerX(), top + lh + 3.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    static String fitting(PanelSurface s, float width, float scale, String[] wordings) {
        for (final String w : wordings) {
            if (s.textWidth(w, scale) <= width) {
                return w;
            }
        }
        return null;
    }

    static void stale(PanelSurface s, Rect area, AerialFrame f) {
        Shapes.rect(s, area, Tokens.alpha(Tokens.CANVAS, 0.55f));
        final String text = fitting(s, area.w - 20.0f, Text.META, new String[]{
                String.format(Locale.ROOT, "NO FEED  %.0f s", Math.min(999.0, f.age())), "NO FEED"});
        if (text == null) {
            return;
        }
        final float w = s.textWidth(text, Text.META) + 16.0f;
        final Rect box = new Rect(area.centerX() - w * 0.5f, area.centerY() - 9.0f, w, 18.0f);
        Shapes.roundedRect(s, box, 4.0f, Tokens.CRITICAL);
        s.textCenterV(text, box.centerX(), box.centerY(), Tokens.CANVAS, Text.META);
    }

    static void glyph(PanelSurface s, Rect area, AerialFrame f) {
        final AirframeReadout r = f.readout();
        final AerialAlerts.Level worst = f.worst();
        if (worst == AerialAlerts.Level.CRITICAL || worst == AerialAlerts.Level.CAUTION) {
            Shapes.rect(s, area, Tokens.alpha(worst == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL : Tokens.CAUTION, 0.25f));
        }
        final String alt = Long.toString(Math.round(r.altitude));
        s.textCenterV(alt, area.centerX(), area.centerY(), Tokens.INK, fit(s, alt, area.w * 0.8f, area.h * 0.5f,
                Text.HERO * 2.0f));
    }

    static float fit(PanelSurface s, String text, float w, float h, float max) {
        final float byWidth = w / Math.max(1.0f, s.textWidth(text, 1.0f));
        final float byHeight = h / Text.lineHeight(1.0f);
        return Math.max(0.1f, Math.min(max, Math.min(byWidth, byHeight)));
    }

    static int alertColor(AerialFrame f, int calm) {
        final AerialAlerts.Level worst = f.worst();
        return worst == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL
                : (worst == AerialAlerts.Level.CAUTION ? Tokens.CAUTION : calm);
    }

    static int heightColor(AerialFrame f) {
        AerialAlerts.Level level = null;
        for (final AerialAlerts.Alert a : f.alerts()) {
            if ((a.code() == AerialAlerts.Code.TERRAIN || a.code() == AerialAlerts.Code.SINK_RATE
                    || a.code() == AerialAlerts.Code.LIFT_SHORT)
                    && (level == null || a.level().compareTo(level) > 0)) {
                level = a.level();
            }
        }
        return level == AerialAlerts.Level.CRITICAL ? Tokens.CRITICAL
                : (level == AerialAlerts.Level.CAUTION ? Tokens.CAUTION : Tokens.INK);
    }

    static String alertName(AerialFrame f) {
        final AerialAlerts.Level worst = f.worst();
        if (worst == null || worst == AerialAlerts.Level.ADVISORY) {
            return null;
        }
        final String text = f.alerts().get(0).text();
        final int cut = text.indexOf("  ");
        return cut > 0 ? text.substring(0, cut) : text;
    }

    static void altitudeOnly(PanelSurface s, Rect b, AerialFrame f) {
        final String alt = Long.toString(Math.round(f.readout().altitude));
        s.textCenterV(alt, b.centerX(), b.centerY(), heightColor(f), fit(s, alt, b.w, b.h, Text.HERO));
    }

    static void altitudeHero(PanelSurface s, Rect b, AerialFrame f) {
        final AirframeReadout r = f.readout();
        final int col = heightColor(f);
        final float label = Text.MICRO * 0.75f;
        final String alert = alertName(f);
        final String head = alert == null ? "ALTITUDE" : Text.truncate(s, alert, b.w * 0.62f, label);
        s.text(head, b.x, b.y, alert == null ? Tokens.INK_3 : alertColor(f, Tokens.INK_3), label);
        final String agl = "AGL " + AeroInstruments.num(r.agl, 0);
        if (s.textWidth(head, label) + s.textWidth(agl, label) + 6.0f <= b.w) {
            s.textRight(agl, b.right(), b.y, Tokens.INK_2, label);
        }
        final String alt = Long.toString(Math.round(r.altitude));
        final float scale = fit(s, alt, b.w * 0.9f, b.h * 0.34f, Text.HERO);
        final float ty = b.y + Text.lineHeight(label) + 2.0f;
        s.textCenter(alt, b.centerX(), ty, col, scale);
        final float ay = ty + Text.lineHeight(scale) + 3.0f;
        final int vcol = r.verticalSpeed >= 0.0 ? Tokens.GOOD : (r.verticalSpeed > -2.5 ? Tokens.CAUTION : Tokens.CRITICAL);
        final String vs = AeroInstruments.signed(r.verticalSpeed, 1) + " b/s";
        final float vsScale = s.textWidth(vs, Text.META) + 14.0f <= b.w ? Text.META : Text.MICRO;
        final float vw = s.textWidth(vs, vsScale);
        final float ax = b.centerX() - vw * 0.5f - 7.0f;
        final float mid = ay + Text.lineHeight(vsScale) * 0.5f;
        if (Math.abs(r.verticalSpeed) >= 0.1) {
            if (r.verticalSpeed > 0.0) {
                s.tri(ax, mid - 4.0f, vcol, ax - 4.0f, mid + 3.0f, vcol, ax + 4.0f, mid + 3.0f, vcol);
            } else {
                s.tri(ax, mid + 4.0f, vcol, ax - 4.0f, mid - 3.0f, vcol, ax + 4.0f, mid - 3.0f, vcol);
            }
        }
        s.text(vs, b.centerX() - vw * 0.5f + 2.0f, ay, vcol, vsScale);
        final float sparkTop = ay + Text.lineHeight(vsScale) + 4.0f;
        final Rect spark = new Rect(b.x, Math.max(sparkTop, b.bottom() - b.h * 0.24f), b.w,
                b.bottom() - Math.max(sparkTop, b.bottom() - b.h * 0.24f));
        if (spark.h >= 8.0f) {
            final float[] h = r.snapshot.history().altitude();
            final double[] range = AeroInstruments.range(h, 0.15);
            AeroInstruments.spark(s, spark, h, vcol, range[0], range[1], Double.NaN, true);
        }
    }
}
