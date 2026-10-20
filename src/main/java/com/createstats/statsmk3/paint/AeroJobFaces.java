package com.createstats.statsmk3.paint;

import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AirframeReadout;
import com.createstats.aerial.FlightCharts;
import com.createstats.aerial.HelmWords;
import com.createstats.statsmk3.page.AeroBridge;
import com.createstats.statsmk3.page.AeroInstruments;
import com.createstats.statsmk3.page.AeroSkin;
import com.createstats.statsmk3.page.AeroTabs;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class AeroJobFaces {
    private AeroJobFaces() {
    }

    static void telegraph(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AerialSnapshot.Helm h = f.readout().snapshot.helm();
        if (!h.known()) {
            final String t = "NO TELEGRAPH";
            s.textCenterV(t, b.centerX(), b.centerY(), k.ink3(), AeroFacePainter.fit(s, t, b.w, b.h, Text.SUB));
            return;
        }
        final String order = HelmWords.order(h.order());
        if (b.w < 70.0f || b.h < 44.0f) {
            AeroBridge.telegraph(s, b, f, k, false);
            return;
        }
        if (b.w >= b.h * 1.7f) {
            final float dialW = Math.min(b.w * 0.48f, b.h * 2.0f);
            AeroBridgeFace.dialCard(s, new Rect(b.x, b.y, dialW, b.h), f, k);
            final Rect words = new Rect(b.x + dialW + 10.0f, b.y, b.w - dialW - 10.0f, b.h);
            orderWords(s, words, h, k, false);
            return;
        }
        final float wordsH = b.h >= 110.0f ? Math.min(52.0f, Math.max(34.0f, b.h * 0.26f)) : 20.0f;

        final float natural = (b.w - 10.0f) * 0.5f + 34.0f;
        final float cardH = Math.min(b.h - wordsH - 4.0f, natural);
        final float top = b.y + Math.max(0.0f, (b.h - cardH - 4.0f - wordsH) * 0.5f);
        AeroBridgeFace.dialCard(s, new Rect(b.x, top, b.w, cardH), f, k);
        orderWords(s, new Rect(b.x, top + cardH + 4.0f, b.w, wordsH), h, k, true);
    }

    static void orderWords(PanelSurface s, Rect r, AerialSnapshot.Helm h, AeroSkin k, boolean centred) {
        final String order = HelmWords.order(h.order());
        final float lineRoom = r.h >= 30.0f ? 12.0f : 0.0f;
        final float os = AeroFacePainter.fit(s, order, r.w, Math.max(9.0f, (r.h - lineRoom * 2.0f) * 0.9f), Text.TITLE * 1.5f);
        final float oy = centred ? r.y : r.centerY() - (Text.lineHeight(os) + lineRoom * 2.0f) * 0.5f;
        if (centred) {
            s.textCenter(order, r.centerX(), oy, k.ink(), os);
        } else {
            s.text(order, r.x, oy, k.ink(), os);
        }
        if (lineRoom <= 0.0f) {
            return;
        }
        final String answer = h.braking() && Math.abs(h.order()) < 1.0e-6 ? "ENGINES BRAKING"
                : "ENGINES " + Math.round(Math.abs(h.engine()) * 100.0) + "%" + (h.engine() < -1.0e-3 ? " ASTERN" : "");
        final String full = Double.isFinite(h.fullAhead())
                ? String.format(Locale.ROOT, "FULL AHEAD %.1f b/s", h.fullAhead()) : "";
        float y = oy + Text.lineHeight(os) + 3.0f;
        for (final String line : new String[]{answer, full}) {
            if (line.isEmpty() || y + 9.0f > r.bottom() + 1.0f || s.textWidth(line, Text.META) > r.w) {
                continue;
            }
            if (centred) {
                s.textCenter(line, r.centerX(), y, line == answer ? k.engine() : k.ink3(), Text.META);
            } else {
                s.text(line, r.x, y, line == answer ? k.engine() : k.ink3(), Text.META);
            }
            y += 12.0f;
        }
    }

    static void voyage(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AerialSnapshot.Voyage v = f.snapshot().voyage();
        if (b.w < 150.0f || b.h < 110.0f) {
            final String title = v.aloft() ? "ALOFT" : v.last() != null ? "LAST FLIGHT" : "NO FLIGHT YET";
            s.text(Text.truncate(s, title, b.w, Text.META), b.x, b.y, k.title(), Text.META);
            final double seconds = v.aloft() ? v.seconds() : v.last() != null ? v.last().seconds() : Double.NaN;
            if (!Double.isFinite(seconds)) {
                return;
            }
            final String time = AerialAlerts.duration(seconds);
            final float ts = AeroFacePainter.fit(s, time, b.w, b.h * 0.5f, Text.HERO);
            s.text(time, b.x, b.y + 13.0f, k.ink(), ts);
            final double dist = v.aloft() ? v.distance() : v.last().distance();
            final String line = AeroBridgeFace.AeroBridgeWords.distance(dist);
            if (b.y + 16.0f + Text.lineHeight(ts) + 9.0f <= b.bottom()) {
                s.text(Text.truncate(s, line, b.w, Text.META), b.x, b.y + 16.0f + Text.lineHeight(ts), k.ink2(), Text.META);
            }

            final float used = Math.max(s.textWidth(time, ts), s.textWidth(line, Text.META)) + 12.0f;
            final float[] trace = v.trace();
            if (b.w - used >= 90.0f && trace.length >= 2) {
                final double[] range = AeroInstruments.range(trace, 0.1);
                AeroInstruments.spark(s, new Rect(b.x + used, b.y + 3.0f, b.w - used, b.h - 6.0f), trace, k.held(),
                        range[0], range[1], Double.NaN, true);
            }
            return;
        }
        AeroBridgeFace.topLine(s, b.top(12.0f), f, k);
        final Rect rest = new Rect(b.x, b.y + 17.0f, b.w, b.h - 17.0f);
        if (rest.w >= 380.0f && rest.h >= 130.0f) {
            final float listW = Math.min(220.0f, rest.w * 0.38f);
            AeroBridge.voyageChart(s, new Rect(rest.x, rest.y, rest.w - listW - 8.0f, rest.h), f, k);
            AeroBridge.eventList(s, new Rect(rest.right() - listW, rest.y, listW, rest.h), f, k);
        } else {
            AeroBridge.voyageChart(s, rest, f, k);
        }
    }

    static void scope(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        if (b.w < 110.0f || b.h < 90.0f) {
            final int n = rd.traffic.size();
            AirframeReadout.TrafficState near = null;
            boolean conflict = false;
            for (final AirframeReadout.TrafficState t : rd.traffic) {
                if (near == null || t.distance() < near.distance()) {
                    near = t;
                }
                conflict |= t.conflict();
            }
            final String count = Integer.toString(n);
            final float cs = AeroFacePainter.fit(s, count, b.w * 0.5f, b.h * 0.6f, Text.HERO);
            s.text(count, b.x, b.y, conflict ? Tokens.CAUTION : k.ink(), cs);
            final String what = n == 1 ? "SHIP NEAR" : "SHIPS NEAR";
            final float wx = b.x + s.textWidth(count, cs) + 6.0f;
            if (wx + s.textWidth(what, Text.META) <= b.right()) {
                s.text(what, wx, b.y + 2.0f, k.ink3(), Text.META);
            }
            if (near != null && b.y + Text.lineHeight(cs) + 13.0f <= b.bottom()) {
                s.text(Text.truncate(s, "nearest " + Text.group(Math.round(near.distance())) + " b", b.w, Text.META), b.x,
                        b.y + Text.lineHeight(cs) + 3.0f, near.conflict() ? AeroTabs.trafficInk(near) : k.ink2(), Text.META);
            }
            return;
        }
        if (b.w >= b.h * 1.45f && b.w >= 240.0f) {
            final float side = b.h;
            AeroTabs.scope(s, new Rect(b.x, b.y, side, b.h), rd, k);
            AeroTabs.list(s, new Rect(b.x + side + 8.0f, b.y, b.w - side - 8.0f, b.h), rd, k);
        } else {
            AeroTabs.scope(s, b, rd, k);
        }
    }

    static void map(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        if (b.w >= b.h * 1.45f && b.w >= 260.0f && b.h >= 120.0f) {
            final float side = b.h;
            AeroTabs.terrainMap(s, new Rect(b.x, b.y, side, b.h), rd, k);
            final Rect col = new Rect(b.x + side + 8.0f, b.y, b.w - side - 8.0f, b.h);
            final float targetH = Math.min(70.0f, col.h * 0.40f);
            AeroTabs.groundAhead(s, new Rect(col.x, col.y, col.w, col.h - targetH - 6.0f), rd, k);
            AeroTabs.target(s, new Rect(col.x, col.bottom() - targetH, col.w, targetH), rd, k);
            return;
        }
        final float wordsH = b.h >= 110.0f && b.w >= 110.0f ? 13.0f : 0.0f;
        AeroTabs.terrainMap(s, new Rect(b.x, b.y, b.w, b.h - wordsH), rd, k);
        if (wordsH > 0.0f) {
            final AerialSnapshot.Guard g = rd.snapshot.helm().known() ? rd.snapshot.helm().guard() : AerialSnapshot.Guard.NONE;
            final String w = AeroTabs.first(s, b.w, AeroTabs.words(rd, g), AeroTabs.wordsShort(rd, g));
            if (!w.isEmpty()) {
                s.text(w, b.x, b.bottom() - 10.0f, k.ink2(), Text.META);
            }
        }
    }

    static void lift(PanelSurface s, Rect b, AerialFrame f, AeroSkin k) {
        final AirframeReadout rd = f.readout();
        if (b.w >= 300.0f && b.h >= 190.0f) {
            final float colW = Math.min(b.w * 0.40f, 220.0f);
            AeroTabs.ceilingChart(s, new Rect(b.x, b.y, b.w - colW - 8.0f, b.h), rd, k);
            final Rect col = new Rect(b.right() - colW, b.y, colW, b.h);
            AeroTabs.room(s, new Rect(col.x, col.y, col.w, 50.0f), rd, k);
            AeroBridge.liftGauge(s, new Rect(col.x, col.y + 56.0f, col.w, 42.0f), rd, k);
            if (col.h - 104.0f >= 58.0f) {
                AeroTabs.heldUpBy(s, new Rect(col.x, col.y + 104.0f, col.w, Math.min(62.0f, col.h - 104.0f)), rd, k);
            }
            return;
        }

        final AerialSnapshot.Helm h = rd.snapshot.helm();
        final double spare = h.known() ? FlightCharts.spareAt(rd.altitude, h.balloonCeiling(), rd.lapse,
                FlightCharts.mustHold(rd)) : Double.NaN;
        s.text(Text.truncate(s, b.w >= 130.0f ? "ROOM AT THIS HEIGHT" : "ROOM", b.w, Text.META), b.x, b.y, k.title(),
                Text.META);
        final String figure = !Double.isFinite(spare) ? "--" : spare >= 0.0 ? "+" + Text.group(Math.round(spare))
                : "-" + Text.group(Math.round(-spare));
        final float fs = AeroFacePainter.fit(s, figure, b.w * 0.78f, b.h * 0.42f, Text.HERO);
        final int ink = !Double.isFinite(spare) ? k.ink3() : spare >= 0.0 ? Tokens.GOOD : Tokens.CRITICAL;
        s.text(figure, b.x, b.y + 13.0f, ink, fs);
        final float ux = b.x + s.textWidth(figure, fs) + 5.0f;
        if (ux + s.textWidth("kpg", Text.META) <= b.right()) {
            s.text("kpg", ux, b.y + 13.0f + Text.lineHeight(fs) - 10.0f, k.ink3(), Text.META);
        }
        float y = b.y + 13.0f + Text.lineHeight(fs) + 4.0f;
        final String ceiling = h.known() && Double.isFinite(h.ceiling())
                ? "CEILING " + Math.round(h.ceiling()) + ": " + HelmWords.ceilingReason(h).toUpperCase(Locale.ROOT)
                : "";
        if (!ceiling.isEmpty() && y + 9.0f <= b.bottom()) {
            final String c = s.textWidth(ceiling, Text.META) <= b.w ? ceiling : "CEILING " + Math.round(h.ceiling());
            if (s.textWidth(c, Text.META) <= b.w) {
                s.text(c, b.x, y, k.limit(), Text.META);
            }
            y += 13.0f;
        }
        if (b.bottom() - y >= 110.0f && b.w >= 140.0f) {
            AeroTabs.ceilingChart(s, new Rect(b.x, y, b.w, b.bottom() - y), rd, k);
        } else if (b.bottom() - y >= 42.0f) {
            AeroBridge.liftGauge(s, new Rect(b.x, b.bottom() - 42.0f, b.w, 42.0f), rd, k);
        }
    }
}
