package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.data.TimeSeriesStore;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// pure, the block face had an offline rasteriser and the studio didn't. their bug histories show it
@OnlyIn(Dist.CLIENT)
public final class StressArt {
    private StressArt() {
    }

    public static final float DIAL_START = (float) Math.toRadians(150.0);
    public static final float DIAL_SWEEP = (float) Math.toRadians(240.0);

    public static final float CAUTION_AT = 70.0f;
    public static final float CRITICAL_AT = 90.0f;

    private static final float MAX_RPM = 256.0f;

    public static final float HEADER_H = 66.0f;

    public record Reading(float used, float capacity, float pct, float remaining, float rpm,
                          boolean overstressed, boolean live, float[] history,
                          float secondsPerSample) {
        public static Reading empty() {
            return new Reading(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, false, false, new float[0], 1.0f);
        }

        public float headroom() {
            return Math.max(0.0f, this.remaining);
        }

        public float speedCeiling() {
            if (this.used <= 0.01f || this.rpm <= 0.01f) {
                return 0.0f;
            }
            return this.rpm * (this.capacity / this.used);
        }

        public Float slopePerSecond() {
            if (this.history == null || this.history.length < 8) {
                return null;
            }
            final int n = Math.min(this.history.length, 60);
            final int start = this.history.length - n;
            float sx = 0.0f;
            float sy = 0.0f;
            float sxy = 0.0f;
            float sxx = 0.0f;
            for (int i = 0; i < n; i++) {
                final float x = i;
                final float y = this.history[start + i];
                sx += x;
                sy += y;
                sxy += x * y;
                sxx += x * x;
            }
            final float denom = n * sxx - sx * sx;
            if (Math.abs(denom) < 1.0e-6f) {
                return null;
            }
            return ((n * sxy - sx * sy) / denom) / Math.max(0.05f, this.secondsPerSample);
        }
    }

    public record PeerArt(String numeral, String label, String bearing, int series, boolean compared,
                          float needlePct, float printedPct, float peakPct, float peakUsed,
                          Reading data) {
    }

    public static Reading readingOf(SourceProbe.Unit u) {
        if (u == null) {
            return Reading.empty();
        }
        final boolean live = u.has("stress_pct") || (u.has("stress_su") && u.has("capacity_su"));
        final float used = u.value("stress_su", 0.0f);
        final float capacity = Math.max(1.0f, u.value("capacity_su", Math.max(used, 1.0f)));
        final float pct = u.has("stress_pct") ? u.value("stress_pct", 0.0f) : used / capacity * 100.0f;
        final float remaining = u.has("remaining_su")
                ? u.value("remaining_su", Math.max(0.0f, capacity - used))
                : Math.max(0.0f, capacity - used);
        final float rpm = Math.abs(u.value("rpm", 0.0f));
        final boolean over = u.flag("overstressed") || pct >= 100.0f;

        final TimeSeriesStore store = u.store("stress_su");

        final float[] history = store == null ? new float[0] : store.getDataWindow(180);
        final float seconds = store == null
                ? Math.max(1, TimeSeriesStore.DEFAULT_SAMPLE_INTERVAL_TICKS) / 20.0f
                : Math.max(0.05f, store.getSampleIntervalTicks() / 20.0f);
        return new Reading(used, capacity, pct, remaining, rpm, over, live, history, seconds);
    }

    public static void header(PanelSurface c, Rect area, List<PeerArt> peers, String twinNotice,
                              float rightInset) {
        c.text("Stress", area.x, area.y, Tokens.INK, Text.SUB);

        float worst = 0.0f;
        int tight = 0;
        float totalUsed = 0.0f;
        float totalCap = 0.0f;
        for (final PeerArt p : peers) {
            worst = Math.max(worst, p.data().pct());
            if (p.data().pct() >= CAUTION_AT) {
                tight++;
            }
            totalUsed += p.data().used();
            totalCap += p.data().capacity();
        }

        final String sub;
        if (peers.isEmpty()) {
            sub = "No stressometer linked";
        } else if (peers.size() == 1) {
            final PeerArt only = peers.get(0);
            sub = only.bearing().isEmpty() ? only.label() : only.label() + " · " + only.bearing();
        } else {
            sub = peers.size() + " kinetic networks · " + tight + " at or past "
                    + Math.round(CAUTION_AT) + "% · worst " + Math.round(worst) + "%";
        }
        c.text(sub, area.x, area.y + 20.0f, Tokens.INK_3, Text.META);

        if (peers.size() > 1) {
            fleetBar(c, area, peers, totalUsed, totalCap);
        }
        if (twinNotice != null && !twinNotice.isEmpty()) {
            final float w = c.textWidth(twinNotice, Text.MICRO) + 32.0f;
            final Rect chip = new Rect(area.right() - rightInset - w - 8.0f, area.y + 3.0f, w, 20.0f);
            c.card(chip, 9.0f, Tokens.alpha(Tokens.CAUTION, 0.08f), Tokens.alpha(Tokens.CAUTION, 0.35f));
            Shapes.circle(c, chip.x + 11.0f, chip.centerY(), 3.0f, Tokens.CAUTION);
            c.textV(twinNotice, chip.x + 19.0f, chip.centerY(), Tokens.CAUTION, Text.MICRO);
        }
    }

    private static void fleetBar(PanelSurface c, Rect area, List<PeerArt> peers,
                                 float used, float cap) {
        final float labelW = 220.0f;
        final Rect bar = new Rect(area.x, area.y + 42.0f,
                Math.max(60.0f, area.w - labelW), 14.0f);
        c.card(bar, 7.0f, Tokens.RAISED, Tokens.LINE);
        float x = bar.x + 2.0f;
        for (final PeerArt p : peers) {
            final float w = (bar.w - 4.0f) * (p.data().capacity() / Math.max(1.0f, cap));
            final float fill = Math.min(w - 2.0f,
                    w * Math.min(1.0f, p.data().used() / p.data().capacity()));
            Shapes.roundedRect(c, new Rect(x, bar.y + 2.0f, Math.max(2.0f, w - 2.0f), bar.h - 4.0f),
                    3.0f, Tokens.LINE);
            Shapes.roundedRect(c, new Rect(x, bar.y + 2.0f, Math.max(2.0f, fill), bar.h - 4.0f),
                    3.0f, statusColor(p.data()));
            x += w;
        }
        c.textV(Text.value(used) + " / " + Text.value(cap) + " SU across all networks",
                bar.right() + 10.0f, bar.centerY(), Tokens.INK_3, Text.MICRO);
    }

    public static void paintPeer(PanelSurface c, PeerArt p, Rect r, float big, float mid,
                                 boolean hovered, float time) {
        if (r.w < 4.0f || r.h < 4.0f) {
            return;
        }
        final Reading data = p.data();
        final int status = statusColor(data);

        c.card(r, Tokens.R_CARD, hovered ? Tokens.RAISED : Tokens.SURFACE,
                hovered ? Tokens.alpha(status, 0.55f) : Tokens.LINE);

        Shapes.roundedRect(c, new Rect(r.x, r.y, 3.0f, r.h), 1.5f, status);

        final float numScale = Text.MICRO + 0.25f * mid;
        c.textV(p.numeral(), r.x + 12.0f, r.y + 13.0f,
                p.compared() ? p.series() : Tokens.INK_2, numScale);
        final float numW = c.textWidth(p.numeral(), numScale);
        if (p.compared()) {
            Shapes.rect(c, r.x + 12.0f, r.y + 21.0f, Math.max(10.0f, numW), 2.0f, p.series());
        }
        if (mid > 0.05f) {
            c.textV(Text.truncate(c, p.label(), Math.max(10.0f, r.w - numW - 40.0f), Text.META),
                    r.x + 16.0f + numW + 6.0f, r.y + 13.0f, Tokens.INK, Text.META);
        }
        if (big > 0.05f && !p.bearing().isEmpty()) {
            c.textRightV(p.bearing(), r.right() - 12.0f, r.y + 13.0f, Tokens.INK_3, Text.MICRO);
        }

        if (!data.live()) {
            c.textCenterV("No readings yet", r.centerX(), r.centerY(), Tokens.INK_3,
                    r.w < 80.0f ? Text.MICRO : Text.META);
            return;
        }
        if (r.w < 80.0f) {
            chip(c, r, p, status);
            return;
        }

        final boolean sparkShown = r.h >= 200.0f;
        final float tiny = 1.0f - ramp(r.h, 150.0f, 210.0f);

        final float room = Math.max(20.0f, r.h - 34.0f);
        float trendH = big * Math.min(170.0f, room * 0.45f);

        if (trendH < 80.0f) {
            trendH = 0.0f;
        }
        final float reserveSmall = 6.0f + mid * (sparkShown ? 58.0f : 24.0f);
        final float bottomReserve = Math.max(6.0f, Math.min(room - 40.0f,
                reserveSmall * (1.0f - big) + (trendH > 0.0f ? trendH + 12.0f : 0.0f) * big));
        final float mainTop = r.y + 34.0f - 8.0f * tiny;
        final float mainBot = Math.max(mainTop + 40.0f, r.bottom() - bottomReserve);

        final float dialR = Math.max(6.0f, Math.min(Math.min(r.w * (0.34f - 0.15f * big),
                (mainBot - mainTop) * (0.46f - 0.04f * big)), r.h * 0.34f));
        final float cx = r.x + r.w * (0.5f - 0.26f * big);
        final float cy = big > 0.05f ? mainTop + (mainBot - mainTop) * 0.5f : r.y + r.h * 0.56f;
        dial(c, p, cx, cy, dialR, big, mid, tiny, status, time);

        if (sparkShown && big < 0.6f) {
            trend(c, new Rect(r.x + 14.0f, r.bottom() - 50.0f, r.w - 28.0f, 34.0f), data, status, false);
        }
        if (mid > 0.3f && big < 0.6f) {
            c.textV(Text.value(data.headroom()) + " SU spare", r.x + 14.0f, r.bottom() - 12.0f,
                    Tokens.INK_3, Text.MICRO);
            c.textRightV(Math.round(data.rpm()) + " RPM", r.right() - 14.0f, r.bottom() - 12.0f,
                    Tokens.INK_3, Text.MICRO);
        }
        if (big > 0.35f) {
            heroExtras(c, r, p, status, mainTop, mainBot, trendH);
        }
    }

    private static void chip(PanelSurface c, Rect r, PeerArt p, int status) {
        final float cx = r.centerX();
        final float cy = r.y + r.h * 0.60f;
        final float rad = r.h * 0.26f;
        Shapes.arc(c, cx, cy, rad, 3.0f, DIAL_START, DIAL_SWEEP, Tokens.LINE);
        final float shown = Math.min(p.needlePct(), 100.0f);
        if (shown > 0.2f) {
            Shapes.arc(c, cx, cy, rad, 3.0f, DIAL_START, sweepBetween(0.0f, shown), status);
        }
        c.textCenterV(Math.round(p.printedPct()) + "%", cx, cy + 1.0f, status, Text.META);
    }

    private static void dial(PanelSurface c, PeerArt p, float cx, float cy, float dialR,
                             float big, float mid, float tiny, int status, float time) {
        final Reading data = p.data();
        final float thick = Math.max(3.0f, dialR * 0.13f);

        // danger marks only, green under an amber needle is two colours arguing

        if (mid > 0.3f) {
            final float bandR = dialR + thick * 0.5f + 6.0f;
            Shapes.arc(c, cx, cy, bandR, 2.5f, angleAt(CAUTION_AT),
                    sweepBetween(CAUTION_AT, CRITICAL_AT), Tokens.alpha(Tokens.CAUTION, 0.70f));
            Shapes.arc(c, cx, cy, bandR, 2.5f, angleAt(CRITICAL_AT),
                    sweepBetween(CRITICAL_AT, 100.0f), Tokens.CRITICAL);
        }

        Shapes.arc(c, cx, cy, dialR, thick, DIAL_START, DIAL_SWEEP, Tokens.LINE);
        final float shown = Math.min(p.needlePct(), 100.0f);
        if (shown > 0.2f) {
            Shapes.arc(c, cx, cy, dialR, thick, DIAL_START, sweepBetween(0.0f, shown), status);
            Shapes.arcCap(c, cx, cy, dialR, DIAL_START, thick, status);
            Shapes.arcCap(c, cx, cy, dialR, angleAt(shown), thick, status);
        }
        if (data.overstressed()) {
            final float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6.0);
            Shapes.arc(c, cx, cy, dialR, thick, DIAL_START, DIAL_SWEEP,
                    Tokens.alpha(Tokens.CRITICAL, 0.35f * pulse));
        }
        if (big > 0.02f) {
            ticks(c, cx, cy, dialR, thick, big);
        }
        if (p.peakPct() > 1.0f) {
            final float pa = angleAt(Math.min(p.peakPct(), 100.0f));
            final float inner = dialR - thick * 0.5f - 2.0f;
            final float outer = dialR + thick * 0.5f + 2.0f;
            Shapes.line(c, cx + cos(pa) * inner, cy + sin(pa) * inner,
                    cx + cos(pa) * outer, cy + sin(pa) * outer, 2.0f, Tokens.INK);
        }

        final float na = angleAt(Math.min(p.needlePct(), 100.0f));
        final float nOut = dialR - thick * 0.5f - 2.0f;
        final float nIn = dialR * 0.62f;
        Shapes.line(c, cx + cos(na) * nIn, cy + sin(na) * nIn,
                cx + cos(na) * nOut, cy + sin(na) * nOut, 2.2f, Tokens.INK);

        final float numScale = Math.max(1.0f, Math.min(2.0f + 3.0f * big, dialR / 12.0f));
        final String number = String.format(Locale.ROOT, "%.0f", p.printedPct());
        c.textCenterV(number, cx, cy - dialR * 0.02f, status, numScale);
        final float numW = c.textWidth(number, numScale);
        c.textV("%", cx + numW * 0.5f + 5.0f + 2.0f * big,
                cy - dialR * 0.02f - Text.lineHeight(numScale) * 0.16f,
                Tokens.INK_3, Text.MICRO + 0.25f * big);

        if (tiny < 0.9f && dialR >= 45.0f) {
            c.textCenterV(Text.value(data.used()) + "  /  " + Text.value(data.capacity()),
                    cx, cy + dialR * 0.40f, Tokens.INK_2, Text.MICRO + 0.25f * big);
        }

        if (mid > 0.3f) {
            c.textCenterV(statusWord(data), cx, cy + dialR * 0.75f + 4.0f * big, status, Text.MICRO);
        }
    }

    private static void ticks(PanelSurface c, float cx, float cy, float radius, float thick,
                              float big) {
        final float r0 = radius - thick * 0.5f - 4.0f;
        final int labelInk = Tokens.alpha(Tokens.INK_3, big);
        for (int p = 0; p <= 100; p += 5) {
            final boolean major = p % 25 == 0;
            final float ang = angleAt(p);
            final float len = major ? 8.0f : 4.0f;
            Shapes.line(c, cx + cos(ang) * (r0 - len), cy + sin(ang) * (r0 - len),
                    cx + cos(ang) * r0, cy + sin(ang) * r0, major ? 1.6f : 1.0f,
                    Tokens.alpha(major ? Tokens.INK_2 : Tokens.LINE_HI, big));
            if (major) {
                final float lr = r0 - len - 11.0f;
                c.textCenterV(String.valueOf(p), cx + cos(ang) * lr, cy + sin(ang) * lr,
                        labelInk, Text.MICRO);
            }
        }
    }

    private static void heroExtras(PanelSurface c, Rect r, PeerArt p, int status,
                                   float mainTop, float mainBot, float trendH) {
        final Reading data = p.data();
        final float left = r.x + r.w * 0.47f;
        final float cw = (r.right() - left - 28.0f) * 0.5f;
        final float band = mainBot - mainTop;

        final float chh = Math.max(62.0f, Math.min(118.0f, (band - 12.0f) * 0.5f));
        if (band >= chh * 2.0f + 12.0f) {
            final float blockTop = mainTop + Math.max(0.0f, (band - (chh * 2.0f + 12.0f)) * 0.5f);
            headroomCard(c, new Rect(left, blockTop, cw, chh), data, status);
            speedCard(c, new Rect(left + cw + 12.0f, blockTop, cw, chh), data);
            rateCard(c, new Rect(left, blockTop + chh + 12.0f, cw, chh), data);
            peakCard(c, new Rect(left + cw + 12.0f, blockTop + chh + 12.0f, cw, chh), p);
        } else if (band >= 62.0f) {
            final float h = Math.min(band, 86.0f);
            final float top = mainTop + (band - h) * 0.5f;
            headroomCard(c, new Rect(left, top, cw, h), data, status);
            speedCard(c, new Rect(left + cw + 12.0f, top, cw, h), data);
        }
        if (trendH <= 0.0f) {
            return;
        }

        final Rect tr = new Rect(r.x + 14.0f, r.bottom() - trendH + 2.0f, r.w - 28.0f,
                Math.max(10.0f, trendH - 16.0f));
        c.card(tr, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Load against capacity"), tr.x + 12.0f, tr.y + 8.0f,
                Tokens.INK_3, Text.MICRO);
        trend(c, tr.inset(12.0f, 26.0f, 12.0f, 20.0f), data, status, true);
        final float seconds = (data.history() == null ? 0 : data.history().length)
                * data.secondsPerSample();
        c.textV(formatAgo(seconds), tr.x + 12.0f, tr.bottom() - 10.0f, Tokens.INK_3, Text.MICRO);
        c.textRightV("now", tr.right() - 12.0f, tr.bottom() - 10.0f, Tokens.INK_3, Text.MICRO);
    }

    private static void trend(PanelSurface c, Rect plot, Reading data, int color, boolean grid) {
        if (plot.isEmpty()) {
            return;
        }
        final float[] h = data.history();
        if (h == null || h.length < 2) {
            c.textCenterV("Collecting samples", plot.centerX(), plot.centerY(),
                    Tokens.INK_3, Text.MICRO);
            return;
        }
        final float cap = Math.max(1.0f, data.capacity());
        float hi = cap * 1.06f;
        for (final float v : h) {
            hi = Math.max(hi, v);
        }
        final float span = Math.max(1.0e-6f, hi);

        if (grid) {
            for (int q = 1; q <= 3; q++) {
                c.hairlineH(plot.x, plot.bottom() - (cap * q / 4.0f) / span * plot.h, plot.w,
                        Tokens.LINE);
            }
        }

        final int n = h.length;
        final float[] xy = new float[n * 2];
        for (int i = 0; i < n; i++) {
            xy[i * 2] = plot.x + plot.w * (i / (float) (n - 1));
            xy[i * 2 + 1] = plot.bottom() - h[i] / span * plot.h;
        }
        Shapes.areaFill(c, xy, n, plot.bottom(),
                Tokens.alpha(color, 0.14f), Tokens.alpha(color, 0.0f));
        Shapes.polyline(c, xy, n, Tokens.DATA_STROKE, color);

        final float capY = plot.bottom() - cap / span * plot.h;
        for (float x = plot.x; x < plot.right(); x += 7.0f) {
            Shapes.rect(c, x, capY - 0.5f, Math.min(4.0f, plot.right() - x), 1.0f,
                    Tokens.alpha(Tokens.CRITICAL, 0.85f));
        }
        if (grid) {
            c.textRightV("CAPACITY " + Text.value(cap), plot.right(), capY - 7.0f,
                    Tokens.CRITICAL, Text.MICRO);
        }
    }

    private static void headroomCard(PanelSurface c, Rect r0, Reading data, int status) {
        c.card(r0, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r0.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        c.text(Text.track("Headroom"), in.x, in.y, Tokens.INK_3, Text.MICRO);

        final boolean over = data.overstressed();
        final String big = over ? "NONE" : Text.value(data.headroom());
        final float valueY = in.centerY() - 4.0f;
        c.textV(big, in.x, valueY, over ? Tokens.CRITICAL : Tokens.INK, Text.TITLE);
        if (!over) {
            c.textV("SU spare", in.x + c.textWidth(big, Text.TITLE) + 5.0f, valueY + 5.0f,
                    Tokens.INK_3, Text.MICRO);
        }

        final Rect bar = new Rect(in.x, in.bottom() - 5.0f, in.w, 5.0f);
        Shapes.roundedRect(c, bar, 2.5f, Tokens.LINE);
        final float frac = Math.min(1.0f, data.pct() / 100.0f);
        if (frac > 0.0f) {
            Shapes.roundedRect(c, bar.withWidth(Math.max(3.0f, bar.w * frac)), 2.5f, status);
        }
        final String eta = timeToOverstress(data);
        if (eta != null) {
            c.textRightV(eta, in.right(), valueY + 4.0f, Tokens.CAUTION, Text.MICRO);
        }
    }

    private static void speedCard(PanelSurface c, Rect r0, Reading data) {
        c.card(r0, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r0.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        c.text(Text.track("Speed"), in.x, in.y, Tokens.INK_3, Text.MICRO);

        final String rpmStr = Text.value(data.rpm());
        final float valueY = in.centerY() - 4.0f;
        c.textV(rpmStr, in.x, valueY, Tokens.INK, Text.TITLE);
        c.textV("RPM", in.x + c.textWidth(rpmStr, Text.TITLE) + 5.0f, valueY + 5.0f,
                Tokens.INK_3, Text.MICRO);

        final float ceiling = data.speedCeiling();
        final Rect bar = new Rect(in.x, in.bottom() - 5.0f, in.w, 5.0f);
        Shapes.roundedRect(c, bar, 2.5f, Tokens.LINE);
        if (ceiling <= 0.0f) {
            c.textRightV("no load", in.right(), valueY + 4.0f, Tokens.INK_3, Text.MICRO);
            return;
        }
        final float shownCeiling = Math.min(ceiling, MAX_RPM);
        final float frac = Math.min(1.0f, data.rpm() / Math.max(1.0f, shownCeiling));
        final int barColor = frac >= 0.9f ? Tokens.CRITICAL
                : frac >= 0.75f ? Tokens.CAUTION : Tokens.GOOD;
        Shapes.roundedRect(c, bar.withWidth(Math.max(3.0f, bar.w * frac)), 2.5f, barColor);

        final boolean past = data.rpm() > shownCeiling + 0.5f;
        c.textRightV(ceiling >= MAX_RPM ? "safe to 256+"
                        : (past ? "slow to ~" : "safe to ~") + Text.value(shownCeiling),
                in.right(), valueY + 4.0f, barColor, Text.MICRO);
    }

    private static void rateCard(PanelSurface c, Rect r0, Reading data) {
        c.card(r0, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r0.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        c.text(Text.track("Load rate"), in.x, in.y, Tokens.INK_3, Text.MICRO);

        final Float perSecond = data.slopePerSecond();
        final float valueY = in.centerY() - 4.0f;
        final Rect bar = new Rect(in.x, in.bottom() - 5.0f, in.w, 5.0f);

        if (perSecond == null) {
            c.textV("--", in.x, valueY, Tokens.INK_3, Text.TITLE);
            Shapes.roundedRect(c, bar, 2.5f, Tokens.LINE);
            return;
        }
        final float perMinute = perSecond * 60.0f;
        final boolean flat = Math.abs(perMinute) < Math.max(1.0f, data.capacity() * 0.002f);
        final int color = flat ? Tokens.INK_2 : perMinute > 0 ? Tokens.CAUTION : Tokens.GOOD;

        final String value = flat ? "steady" : Text.delta(perMinute, 0);
        c.textV(value, in.x, valueY, color, Text.TITLE);
        if (!flat) {
            c.textV("SU / min", in.x + c.textWidth(value, Text.TITLE) + 5.0f, valueY + 5.0f,
                    Tokens.INK_3, Text.MICRO);
        }

        Shapes.roundedRect(c, bar, 2.5f, Tokens.LINE);
        final float full = Math.max(1.0f, data.capacity() * 0.10f);
        final float frac = Math.max(-1.0f, Math.min(1.0f, perMinute / full));
        final float mid = bar.centerX();
        Shapes.rect(c, mid - 0.5f, bar.y - 2.0f, 1.0f, bar.h + 4.0f, Tokens.LINE_HI);
        if (Math.abs(frac) > 0.01f) {
            final float w = Math.abs(frac) * (bar.w * 0.5f);
            Shapes.roundedRect(c, frac > 0
                    ? new Rect(mid, bar.y, w, bar.h)
                    : new Rect(mid - w, bar.y, w, bar.h), 2.5f, color);
        }
    }

    private static void peakCard(PanelSurface c, Rect r0, PeerArt p) {
        c.card(r0, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r0.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        c.text(Text.track("Peak load"), in.x, in.y, Tokens.INK_3, Text.MICRO);

        final int peakColor = p.peakPct() >= CRITICAL_AT ? Tokens.CRITICAL
                : p.peakPct() >= CAUTION_AT ? Tokens.CAUTION : Tokens.INK;
        final String big = String.format(Locale.ROOT, "%.0f", p.peakPct());
        final float valueY = in.centerY() - 4.0f;
        c.textV(big + "%", in.x, valueY, peakColor, Text.TITLE);
        c.textRightV(Text.value(p.peakUsed()) + " SU", in.right(), valueY + 4.0f,
                Tokens.INK_2, Text.MICRO);

        final Rect bar = new Rect(in.x, in.bottom() - 5.0f, in.w, 5.0f);
        Shapes.roundedRect(c, bar, 2.5f, Tokens.LINE);
        final float pf = Math.min(1.0f, p.peakPct() / 100.0f);
        if (pf > 0.0f) {
            Shapes.roundedRect(c, bar.withWidth(Math.max(3.0f, bar.w * pf)), 2.5f,
                    Tokens.alpha(peakColor, 0.55f));
        }
    }

    public static void compareChart(PanelSurface c, Rect area, List<PeerArt> shown) {
        if (area.isEmpty()) {
            return;
        }
        c.card(area, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Load as a share of each network's own capacity"),
                area.x + 12.0f, area.y + 8.0f, Tokens.INK_3, Text.MICRO);
        final Rect plot = area.inset(38.0f, 28.0f, 34.0f, 22.0f);
        if (plot.isEmpty() || shown.isEmpty()) {
            return;
        }

        final List<float[]> series = new ArrayList<>();
        float dMin = Float.MAX_VALUE;
        float dMax = 0.0f;
        for (final PeerArt p : shown) {
            final float[] s = percentSeries(p.data());
            series.add(s);
            for (final float v : s) {
                dMin = Math.min(dMin, v);
                dMax = Math.max(dMax, v);
            }
        }
        if (dMin > dMax) {
            dMin = 0.0f;
            dMax = 100.0f;
        }
        final float lo = Math.max(0.0f, Math.min(dMin - 8.0f, 92.0f));
        final float hi = Math.max(105.0f, dMax + 5.0f);

        final List<Float> marks = new ArrayList<>();
        for (final float v : new float[]{0.0f, 25.0f, 50.0f, CAUTION_AT, CRITICAL_AT, 100.0f}) {
            if (v >= lo - 0.5f && v <= hi) {
                marks.add(v);
            }
        }
        if (hi - lo < 45.0f) {
            final List<Float> extra = new ArrayList<>();
            for (float v = (float) Math.ceil(lo / 5.0f) * 5.0f; v <= Math.min(hi, 100.0f); v += 5.0f) {
                boolean clear = true;
                for (final float m : marks) {
                    clear &= Math.abs(m - v) > 4.0f;
                }
                if (clear) {
                    extra.add(v);
                }
            }
            marks.addAll(extra);
        }
        for (final float v : marks) {
            final boolean cliff = Math.abs(v - 100.0f) < 0.01f;
            final float y = plot.bottom() - (v - lo) / (hi - lo) * plot.h;
            c.hairlineH(plot.x, y, plot.w, cliff ? Tokens.alpha(Tokens.CRITICAL, 0.7f)
                    : v == CRITICAL_AT ? Tokens.alpha(Tokens.CAUTION, 0.30f) : Tokens.LINE);
            c.textRightV(Math.round(v) + "%", plot.x - 6.0f, y,
                    cliff ? Tokens.CRITICAL : Tokens.INK_3, Text.MICRO);
        }

        final List<float[]> ends = new ArrayList<>();
        for (int s = 0; s < shown.size(); s++) {
            final float[] data = series.get(s);
            if (data.length < 2) {
                continue;
            }
            final float[] xy = new float[data.length * 2];
            for (int i = 0; i < data.length; i++) {
                xy[i * 2] = plot.x + plot.w * (i / (float) (data.length - 1));
                xy[i * 2 + 1] = plot.bottom() - (clamp(data[i], lo, hi) - lo) / (hi - lo) * plot.h;
            }
            Shapes.polyline(c, xy, data.length, Tokens.DATA_STROKE, shown.get(s).series());
            final float ly = xy[xy.length - 1];
            Shapes.circle(c, plot.right(), ly, 3.0f, shown.get(s).series());
            ends.add(new float[]{ly, s});
        }

        ends.sort((a, b) -> Float.compare(a[0], b[0]));
        for (int i = 1; i < ends.size(); i++) {
            if (ends.get(i)[0] - ends.get(i - 1)[0] < 11.0f) {
                ends.get(i)[0] = ends.get(i - 1)[0] + 11.0f;
            }
        }
        for (final float[] e : ends) {
            final PeerArt p = shown.get((int) e[1]);
            c.textV(p.numeral(), plot.right() + 8.0f, e[0], p.series(), Text.MICRO);
        }
    }

    public static float[] percentSeries(Reading data) {
        if (data.history() == null) {
            return new float[0];
        }
        final float[] out = new float[data.history().length];
        for (int i = 0; i < out.length; i++) {
            out[i] = data.history()[i] / Math.max(1.0f, data.capacity()) * 100.0f;
        }
        return out;
    }

    public static void identify(PanelSurface c, Rect area, Rect card, PeerArt p, float t) {
        if (t < 0.02f || card.isEmpty() || card.w < 90.0f) {
            return;
        }
        final String s = "Highlighted in world · " + (p.bearing().isEmpty() ? "here" : p.bearing());
        final float w = c.textWidth(s, Text.MICRO) + 28.0f;
        final boolean above = card.y - 28.0f >= area.y + HEADER_H;
        final Rect tip = new Rect(
                Math.max(area.x, Math.min(area.right() - w, card.centerX() - w * 0.5f)),
                above ? card.y - 28.0f : card.bottom() + 6.0f, w, 22.0f);
        c.card(tip, Tokens.R_CHIP, Tokens.alpha(Tokens.OVERLAY, t), Tokens.alpha(Tokens.LINE_HI, t));
        Shapes.circle(c, tip.x + 12.0f, tip.centerY(), 3.5f, Tokens.alpha(p.series(), t));
        c.textV(s, tip.x + 21.0f, tip.centerY(), Tokens.alpha(Tokens.INK, t), Text.MICRO);
    }

    public static void empty(PanelSurface c, Rect area) {
        final Rect box = area.inset(0.0f, HEADER_H, 0.0f, 0.0f).top(104.0f);
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenterV("No stressometer linked", box.centerX(), box.centerY() - 10.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenterV("Point a Create Display Link from a Stressometer at this panel",
                box.centerX(), box.centerY() + 10.0f, Tokens.INK_3, Text.META);
    }

    public static String timeToOverstress(Reading data) {
        if (data.overstressed()) {
            return null;
        }
        final Float perSecond = data.slopePerSecond();
        if (perSecond == null || perSecond <= 0.0f) {
            return null;
        }
        final float seconds = data.headroom() / Math.max(1.0e-6f, perSecond);
        if (seconds <= 0.0f || seconds > 600.0f) {
            return null;
        }
        return seconds < 60.0f
                ? "full in " + Math.round(seconds) + "s"
                : "full in " + Math.round(seconds / 60.0f) + "m";
    }

    private static String formatAgo(float seconds) {
        if (seconds < 90.0f) {
            return "-" + Math.round(seconds) + "s";
        }
        return "-" + Math.round(seconds / 60.0f) + "m";
    }

    public static int statusColor(Reading r) {
        if (r.overstressed()) {
            return Tokens.CRITICAL;
        }
        return r.pct() >= CRITICAL_AT ? Tokens.CRITICAL
                : r.pct() >= CAUTION_AT ? Tokens.CAUTION : Tokens.GOOD;
    }

    public static String statusWord(Reading r) {
        if (r.overstressed()) {
            return "OVER";
        }
        return r.pct() >= CRITICAL_AT ? "CRITICAL" : r.pct() >= CAUTION_AT ? "TIGHT" : "NOMINAL";
    }

    static float ramp(float v, float lo, float hi) {
        return PeerRoster.ramp(v, lo, hi);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    static float angleAt(float percent) {
        return DIAL_START + DIAL_SWEEP * (Math.min(Math.max(percent, 0.0f), 100.0f) / 100.0f);
    }

    private static float sweepBetween(float fromPct, float toPct) {
        return angleAt(toPct) - angleAt(fromPct);
    }

    private static float cos(float a) {
        return (float) Math.cos(a);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }
}
