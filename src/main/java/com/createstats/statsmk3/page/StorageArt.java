package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.statsmk3.page.StorageReadings.Item;
import com.createstats.statsmk3.page.StorageReadings.Kind;
import com.createstats.statsmk3.page.StorageReadings.Vessel;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StorageArt {
    private StorageArt() {
    }

    public static final float HEADER_H = 66.0f;

    private static final float CHIP_H = 22.0f;
    private static final float CHIP_GAP = 8.0f;

    private static final float CHIP_W = 152.0f;

    private static final float FLOW_SCALE = 40.0f;

    private static final float ASPECT = 2.4f;

    public record PeerArt(String numeral, String label, String bearing, int series, boolean compared,
                          float shownPct, float printedPct, float shownLevel, Vessel data) {
        public float shownFill() {
            return Math.max(0.0f, Math.min(1.0f, this.shownPct / 100.0f));
        }
    }

    public record Category(Kind kind, int count, float pct, boolean hasCapacity, int worstColor) {
    }

    // capacity weighted mean, not sum and not plain mean
    public static List<Category> categorise(List<Vessel> vessels) {
        final List<Category> out = new ArrayList<>();
        for (final Kind kind : Kind.values()) {
            int count = 0;
            float weighted = 0.0f;
            float capacity = 0.0f;
            int worst = Tokens.GOOD;
            int worstRank = 0;
            for (final Vessel v : vessels) {
                if (v.kind() != kind) {
                    continue;
                }
                count++;
                if (v.hasCapacity()) {
                    weighted += v.pct() * v.capacity();
                    capacity += v.capacity();
                }
                final int rank = severity(v.statusColor());
                if (rank > worstRank) {
                    worstRank = rank;
                    worst = v.statusColor();
                }
            }
            if (count > 0) {
                out.add(new Category(kind, count,
                        capacity > 0.0f
                                ? Math.max(0.0f, Math.min(100.0f, weighted / capacity)) : 0.0f,
                        capacity > 0.0f, worst));
            }
        }
        return out;
    }

    private static int severity(int color) {
        if (color == Tokens.CRITICAL) {
            return 3;
        }
        if (color == Tokens.CAUTION) {
            return 2;
        }
        return color == Tokens.GOOD ? 1 : 0;
    }

    public static void header(PanelSurface c, Rect area, List<Category> categories,
                              List<PeerArt> shown, Kind selected, float rightInset,
                              int hiddenCount) {
        c.text("Storage", area.x, area.y, Tokens.INK, Text.SUB);
        c.text(summary(categories, shown, hiddenCount), area.x, area.y + 20.0f,
                Tokens.INK_3, Text.META);

        if (!categories.isEmpty()) {
            drawCategories(c, area, categories, selected);
        }
        if (hiddenCount > 0 && area.w - rightInset > 420.0f) {
            final String note = hiddenCount + " more, filter by kind";
            PageArt.chip(c, area.right() - rightInset - c.textWidth(note, Text.MICRO) - 32.0f,
                    area.y + 2.0f, note, Tokens.CAUTION);
        }
    }

    private static String summary(List<Category> categories, List<PeerArt> shown, int hidden) {
        if (categories.isEmpty()) {
            return "Nothing on this panel is holding anything";
        }
        int total = 0;
        for (final Category cat : categories) {
            total += cat.count();
        }
        if (total == 1 && !shown.isEmpty()) {
            final PeerArt only = shown.get(0);
            final String where = only.bearing().isEmpty() ? "" : " · " + only.bearing();
            return only.data().kind().label + " · " + only.label() + where;
        }
        int atLimit = 0;
        int moving = 0;
        for (final PeerArt p : shown) {
            if (p.data().alarming()) {
                atLimit++;
            }
            if (!p.data().steady()) {
                moving++;
            }
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(Peers.count(total, "store"));
        sb.append(" · ").append(categories.size() == 1
                ? categories.get(0).kind().label.toLowerCase(Locale.ROOT) + " only"
                : categories.size() + " kinds");
        sb.append(" · ").append(moving).append(" moving");
        if (atLimit > 0) {
            sb.append(" · ").append(atLimit).append(" at a limit");
        }
        if (hidden > 0) {
            sb.append(" · ").append(hidden).append(" not shown");
        }
        return sb.toString();
    }

    public static Rect categoryChip(Rect area, int index) {
        return new Rect(area.x + index * (CHIP_W + CHIP_GAP), area.y + 40.0f, CHIP_W, CHIP_H);
    }

    private static void drawCategories(PanelSurface c, Rect area, List<Category> categories,
                                       Kind selected) {
        for (int i = 0; i < categories.size(); i++) {
            final Category cat = categories.get(i);
            final Rect r = categoryChip(area, i);
            if (r.right() > area.right()) {
                return;
            }
            final boolean on = selected == null || selected == cat.kind();
            final int ink = on ? Tokens.INK_2 : Tokens.INK_3;
            c.card(r, Tokens.R_CHIP + 3.0f,
                    selected == cat.kind() ? Tokens.RAISED : Tokens.SURFACE,
                    selected == cat.kind() ? Tokens.alpha(cat.worstColor(), 0.5f) : Tokens.LINE);

            kindIcon(c, r.x + 13.0f, r.centerY(), 6.0f, cat.kind(),
                    on ? cat.worstColor() : Tokens.INK_3);
            final String name = cat.kind().label.toUpperCase(Locale.ROOT) + " " + cat.count();
            c.textV(name, r.x + 24.0f, r.centerY() - 0.5f, ink, Text.MICRO);

            // no capacity = no %, empty and unknown are different answers

            final String pct = cat.hasCapacity() ? Math.round(cat.pct()) + "%" : "";

            final float pctW = pct.isEmpty() ? 0.0f : c.textWidth(pct, Text.MICRO) + 6.0f;
            final float barX = r.x + 24.0f + c.textWidth(name, Text.MICRO) + 7.0f;
            final float barW = r.right() - 10.0f - pctW - barX;
            if (barW < 16.0f) {
                continue;
            }
            final Rect bar = new Rect(barX, r.centerY() - 3.0f, barW, 6.0f);
            Shapes.roundedRect(c, bar, 3.0f, Tokens.LINE);
            if (cat.hasCapacity()) {
                final float w = Math.max(2.0f, bar.w * cat.pct() / 100.0f);
                Shapes.roundedRect(c, bar.withWidth(w), 3.0f,
                        on ? cat.worstColor() : Tokens.alpha(cat.worstColor(), 0.4f));
                c.textRightV(pct, r.right() - 8.0f, r.centerY(), ink, Text.MICRO);
            } else {
                c.textCenterV("no capacity", bar.centerX(), bar.centerY(), Tokens.INK_3, Text.MICRO);
            }
        }
    }

    public static void paintPeer(PanelSurface c, PeerArt p, Rect r, float big, float mid,
                                 boolean hovered, float time) {
        if (r.w < 4.0f || r.h < 4.0f) {
            return;
        }
        final Vessel v = p.data();
        final int status = v.statusColor();

        c.card(r, Tokens.R_CARD, hovered ? Tokens.RAISED : Tokens.SURFACE,
                hovered ? Tokens.alpha(status, 0.55f) : Tokens.LINE);

        Shapes.roundedRect(c, new Rect(r.x, r.y, 3.0f, r.h), 1.5f, status);

        if (!v.live()) {
            drawTitleRow(c, p, r, big, mid, status);
            c.textCenterV("No readings yet", r.centerX(), r.centerY(), Tokens.INK_3,
                    r.w < 80.0f ? Text.MICRO : Text.META);
            return;
        }
        if (r.w < 80.0f) {
            drawTitleRow(c, p, r, big, mid, status);
            chip(c, p, r, status);
            return;
        }

        if (r.h < 62.0f) {
            drawCompactRow(c, p, r, status);
            return;
        }
        drawTitleRow(c, p, r, big, mid, status);

        final Rect inner = r.inset(12.0f, 30.0f - 6.0f * (1.0f - mid), 12.0f, 10.0f);
        if (inner.h < 24.0f) {
            return;
        }

        final float footer = mid > 0.25f ? 30.0f : 14.0f;
        final float availH = Math.max(24.0f, inner.h - footer);
        final float vesselW = vesselWidth(inner.w, availH);
        final float lane = laneWidth(c, v, big);
        final float readoutW = 118.0f + 16.0f * ramp(big, 0.30f, 0.85f);
        final float gaugeNeed = vesselW + lane + Tokens.S2 + readoutW;

        final float detailW = ramp(big, 0.30f, 0.85f)
                * Math.max(0.0f, inner.w - gaugeNeed - Tokens.S4);
        final Rect gaugeArea = new Rect(inner.x, inner.y,
                Math.max(60.0f, inner.w - detailW - (detailW > 4.0f ? Tokens.S4 : 0.0f)), inner.h);

        drawGauge(c, p, gaugeArea, vesselW, lane, readoutW, availH, mid, big, status, time);

        if (detailW > 60.0f) {
            drawDetail(c, p, new Rect(inner.right() - detailW, inner.y, detailW, inner.h),
                    status, time);
        }
    }

    private static float vesselWidth(float columnW, float availH) {
        return Math.max(18.0f, Math.min(Math.min(columnW * 0.22f, availH / ASPECT), 160.0f));
    }

    private static void drawTitleRow(PanelSurface c, PeerArt p, Rect r, float big, float mid,
                                     int status) {
        final float numScale = Text.MICRO + 0.25f * mid;
        c.textV(p.numeral(), r.x + 12.0f, r.y + 13.0f,
                p.compared() ? p.series() : Tokens.INK_2, numScale);
        final float numW = c.textWidth(p.numeral(), numScale);
        if (p.compared()) {
            Shapes.rect(c, r.x + 12.0f, r.y + 21.0f, Math.max(10.0f, numW), 2.0f, p.series());
        }
        if (mid <= 0.05f) {
            return;
        }
        final float iconX = r.x + 16.0f + numW + 8.0f;
        kindIcon(c, iconX, r.y + 13.0f, 5.5f, p.data().kind(), Tokens.alpha(status, 0.85f));

        final float room = Math.max(10.0f, r.w - (iconX - r.x) - 24.0f
                - (big > 0.05f && !p.bearing().isEmpty()
                        ? c.textWidth(p.bearing(), Text.MICRO) + 10.0f : 0.0f));
        c.textV(Text.truncate(c, p.label(), room, Text.META), iconX + 10.0f, r.y + 13.0f,
                Tokens.INK, Text.META);
        if (big > 0.05f && !p.bearing().isEmpty()) {
            c.textRightV(p.bearing(), r.right() - 12.0f, r.y + 13.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    private static void drawCompactRow(PanelSurface c, PeerArt p, Rect r, int status) {
        final Vessel v = p.data();
        final float y = r.centerY();
        float x = r.x + 12.0f;
        c.textV(p.numeral(), x, y, p.compared() ? p.series() : Tokens.INK_3, Text.MICRO);
        x += c.textWidth(p.numeral(), Text.MICRO) + 8.0f;
        kindIcon(c, x + 5.0f, y, 5.0f, v.kind(), Tokens.alpha(status, 0.85f));
        x += 16.0f;

        final String reading = v.hasCapacity()
                ? Math.round(p.printedPct()) + "%" : v.compact(p.shownLevel());
        final float readingW = c.textWidth(reading, Text.META) + 8.0f;
        final float barW = Math.max(30.0f, (r.right() - 12.0f - readingW - x) * 0.5f);
        final float nameW = r.right() - 12.0f - readingW - barW - x - 8.0f;
        if (nameW > 24.0f) {
            c.textV(Text.truncate(c, p.label(), nameW, Text.META), x, y, Tokens.INK, Text.META);
        }
        final Rect bar = new Rect(r.right() - 12.0f - readingW - barW, y - 3.5f, barW, 7.0f);
        Shapes.roundedRect(c, bar, 3.5f, Tokens.LINE);
        if (v.hasCapacity()) {
            Shapes.roundedRect(c, bar.withWidth(Math.max(3.5f, bar.w * p.shownFill())), 3.5f,
                    status);
            if (v.hasThresholds()) {
                Shapes.rect(c, bar.x + bar.w * v.lowerPct() / 100.0f, bar.y - 1.5f, 1.0f,
                        bar.h + 3.0f, Tokens.GOOD);
                Shapes.rect(c, bar.x + bar.w * v.upperPct() / 100.0f, bar.y - 1.5f, 1.0f,
                        bar.h + 3.0f, Tokens.CAUTION);
            }
        }
        c.textRightV(reading, r.right() - 12.0f, y, status, Text.META);
    }

    private static void chip(PanelSurface c, PeerArt p, Rect r, int status) {
        final Rect body = new Rect(r.x + 8.0f, r.y + 18.0f, 10.0f, Math.max(10.0f, r.h - 26.0f));
        Shapes.roundedRect(c, body, 2.0f, Tokens.alpha(Tokens.CANVAS, 0.9f));
        if (p.data().hasCapacity()) {
            final float h = Math.max(1.5f, body.h * p.shownFill());
            Shapes.roundedRect(c, new Rect(body.x, body.bottom() - h, body.w, h), 2.0f, status);
        }
        Shapes.roundedRectOutline(c, body, 2.0f, 1.0f, Tokens.LINE_HI);
        c.textV(p.data().hasCapacity()
                        ? Math.round(p.printedPct()) + "%" : p.data().compact(p.shownLevel()),
                body.right() + 6.0f, r.centerY(), Tokens.INK_2, Text.MICRO);
    }

    private static void drawGauge(PanelSurface c, PeerArt p, Rect a, float vesselW, float lane,
                                  float readoutWish, float availH, float mid, float big,
                                  int status, float time) {
        final Vessel v = p.data();
        final float vesselH = Math.min(availH, vesselW * ASPECT);
        final Rect vessel = new Rect(a.x, a.y + (availH - vesselH) * 0.5f, vesselW, vesselH);

        drawVessel(c, vessel, v, p.shownFill(), status, time);

        drawThresholds(c, vessel, v, big);

        final Rect rest = new Rect(vessel.right() + lane + Tokens.S2, a.y,
                Math.max(0.0f, a.right() - vessel.right() - lane - Tokens.S2), availH);
        final float readoutW = Math.min(rest.w, readoutWish);
        if (readoutW > 44.0f) {
            drawReadout(c, p, rest.withWidth(readoutW), big, mid, status);
        }

        final Rect spark = new Rect(rest.x + readoutW + Tokens.S2, rest.y,
                rest.w - readoutW - Tokens.S2, rest.h);
        if (spark.w > 76.0f && spark.h > 34.0f) {
            drawSpark(c, spark, v, status);
        }

        if (mid > 0.25f) {
            final float y = a.y + availH + 8.0f;
            c.textV(v.amountText(), a.x, y, Tokens.INK_2, Text.MICRO);
            if (v.hasCapacity()) {
                c.textV("of " + v.capacityText(),
                        a.x + c.textWidth(v.amountText(), Text.MICRO) + 6.0f, y,
                        Tokens.INK_3, Text.MICRO);
            }
            final String extra = extraLine(v);
            if (!extra.isEmpty() && a.w > 190.0f) {
                c.textRightV(Text.truncate(c, extra, a.w * 0.5f, Text.MICRO), a.right(), y,
                        Tokens.INK_3, Text.MICRO);
            }
        }
    }

    private static void drawSpark(PanelSurface c, Rect a, Vessel v, int color) {
        final float[] data = sample(v.hasCapacity() ? v.percentSeries() : v.history(), 28);
        if (data.length < 2) {
            return;
        }
        final Rect plot = a.inset(0.0f, a.h * 0.28f, 0.0f, 14.0f);
        float lo = 0.0f;
        float hi = 100.0f;
        if (!v.hasCapacity()) {
            lo = Float.MAX_VALUE;
            hi = -Float.MAX_VALUE;
            for (final float d : data) {
                lo = Math.min(lo, d);
                hi = Math.max(hi, d);
            }
            if (hi - lo < 1.0e-3f) {
                hi = lo + 1.0f;
            }
        }
        c.hairlineH(plot.x, plot.bottom(), plot.w, Tokens.LINE);
        final float[] xy = new float[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            xy[i * 2] = plot.x + plot.w * (i / (float) (data.length - 1));
            xy[i * 2 + 1] = plot.bottom()
                    - (Math.max(lo, Math.min(hi, data[i])) - lo) / (hi - lo) * plot.h;
        }
        Shapes.polyline(c, xy, data.length, 1.6f, color);
        Shapes.circle(c, xy[xy.length - 2], xy[xy.length - 1], 2.2f, color);
        c.text(Text.track(v.hasCapacity() ? "fill trend" : "observed"), plot.x,
                plot.bottom() + 3.0f, Tokens.INK_3, Text.MICRO);
    }

    static float[] sample(float[] data, int max) {
        if (data == null || data.length <= max || max < 2) {
            return data == null ? new float[0] : data;
        }
        final float[] out = new float[max];
        for (int i = 0; i < max; i++) {
            out[i] = data[Math.round((data.length - 1) * (i / (float) (max - 1)))];
        }
        return out;
    }

    private static String extraLine(Vessel v) {
        if (v.kind() == Kind.ITEMS) {
            if (v.uniqueTypes() > 0) {
                return Peers.count(v.uniqueTypes(), "type");
            }
            return v.content();
        }
        if (v.kind() == Kind.FLUID) {
            return v.content();
        }
        return v.hasCapacity() ? v.compact(v.headroom()) + " FE free" : "";
    }

    private static void drawReadout(PanelSurface c, PeerArt p, Rect a, float big, float mid,
                                    int status) {
        final Vessel v = p.data();
        final float scale = Math.max(Text.SUB, Math.min(Text.HERO, 2.0f + 3.4f * big));

        float y = a.y + 2.0f;
        if (v.hasCapacity()) {
            final String number = String.format(Locale.ROOT, "%.0f", p.printedPct());
            final Text.Fitted fitted = new Text.Fitted(number,
                    Text.fitScale(c, number + "%", a.w, a.h * 0.5f, scale, Text.SUB));
            c.text(fitted.text(), a.x, y, status, fitted.scale());
            c.text("%", a.x + c.textWidth(fitted.text(), fitted.scale()) + 2.0f,
                    y + Text.lineHeight(fitted.scale()) - Text.lineHeight(Text.META),
                    Tokens.INK_3, Text.META);
            y += Text.lineHeight(fitted.scale()) + 4.0f;
        } else {
            final Text.Fitted fitted = Text.fitValue(c, p.shownLevel(), a.w, a.h * 0.5f,
                    Math.min(scale, Text.TITLE), Text.SUB);
            c.text(fitted.text(), a.x, y, Tokens.INK, fitted.scale());
            y += Text.lineHeight(fitted.scale()) + 4.0f;
            c.text("observed", a.x, y, Tokens.INK_3, Text.MICRO);
            y += 14.0f;
        }

        if (mid > 0.2f && y + 18.0f < a.bottom()) {
            if (big > 0.25f) {
                PageArt.chip(c, a.x, y, v.state(), status);
                y += 24.0f;
            } else {
                c.textV(v.state(), a.x, y + 6.0f, status, Text.MICRO);
                y += 16.0f;
            }
        }
        if (mid > 0.35f && y + 12.0f < a.bottom()) {
            y = rateLine(c, a, v, y);
        }
        if (big > 0.2f && y + 12.0f < a.bottom() && v.hasSignal()) {
            signalLamp(c, a.x, y + 6.0f, v.powered());
        }
    }

    private static float rateLine(PanelSurface c, Rect a, Vessel v, float y) {
        final Float rate = v.slopePerSecond();
        if (rate == null || v.steady()) {
            c.textV("steady", a.x, y + 6.0f, Tokens.INK_3, Text.MICRO);
            return y + 16.0f;
        }
        final int color = rate > 0.0f ? Tokens.GOOD : Tokens.CAUTION;
        final String arrow = rate > 0.0f ? "▲" : "▼";
        final String text = arrow + " " + v.compact(Math.abs(rate) * 60.0f) + " / min";
        c.textV(text, a.x, y + 6.0f, color, Text.MICRO);
        final Float eta = v.etaSeconds();
        if (eta != null) {
            final String duration = PageArt.duration(eta, 3600.0f);
            if (duration != null) {
                c.textV(v.etaEnd() + " in " + duration, a.x, y + 19.0f,
                        eta < 120.0f ? color : Tokens.INK_3, Text.MICRO);
                return y + 32.0f;
            }
        }
        return y + 16.0f;
    }

    private static void signalLamp(PanelSurface c, float x, float y, boolean on) {
        final int color = on ? Tokens.CRITICAL : Tokens.INK_3;
        Shapes.circle(c, x + 4.0f, y, 4.0f, Tokens.alpha(color, on ? 0.28f : 0.12f));
        Shapes.circle(c, x + 4.0f, y, 2.4f, color);
        c.textV(on ? "SIGNAL ON" : "SIGNAL OFF", x + 12.0f, y, color, Text.MICRO);
    }

    public static int materialColor(Vessel v) {
        return switch (v.kind()) {
            case FLUID -> PageArt.fluidColor(v.content());

            case ENERGY -> v.pct() < 20.0f ? 0xFFFF4A4A : v.pct() < 50.0f ? 0xFFFFBA3C : 0xFF58DC7A;
            default -> Tokens.SERIES[2];
        };
    }

    public static void drawVessel(PanelSurface c, Rect r, Vessel v, float fill, int status,
                                  float time) {
        if (r.isEmpty()) {
            return;
        }
        final int material = materialColor(v);
        final float agitation = agitationOf(v);
        switch (v.kind()) {
            case FLUID -> PageArt.vessel(c, r, fill, material, agitation, time);
            case ENERGY -> drawCells(c, r, fill, material, agitation > 0.02f, time);
            default -> drawCrate(c, r, v, fill, material, agitation, time);
        }
        if (!v.hasCapacity()) {
            hatch(c, r);
            c.textCenterV("no cap", r.centerX(), r.centerY(), Tokens.INK_3, Text.MICRO);
        }

        if (v.hasCapacity() && (v.full() || v.starved())) {
            Shapes.roundedRectOutline(c, r.inset(-1.5f), 5.0f, 1.5f, Tokens.alpha(status, 0.75f));
        }
    }

    private static void hatch(PanelSurface c, Rect r) {
        final float step = Math.max(9.0f, r.h / 7.0f);
        for (float o = -r.h; o < r.w; o += step) {
            final float x0 = Math.max(r.x, r.x + o);
            final float y0 = r.y + Math.max(0.0f, -o);
            final float span = Math.min(r.right() - x0, r.bottom() - y0);
            if (span > 2.0f) {
                Shapes.line(c, x0, y0, x0 + span, y0 + span, 1.0f,
                        Tokens.alpha(Tokens.LINE_HI, 0.55f));
            }
        }
    }

    private static float agitationOf(Vessel v) {
        final Float rate = v.slopePerSecond();
        if (rate == null || v.steady()) {
            return 0.0f;
        }
        final float scale = Math.max(1.0f,
                (v.hasCapacity() ? v.capacity() : Math.max(1.0f, v.level())) * 0.02f);
        return Math.max(0.0f, Math.min(1.0f, Math.abs(rate) / scale));
    }

    private static void drawCrate(PanelSurface c, Rect r, Vessel v, float fill, int color,
                                  float motion, float time) {
        Shapes.roundedRect(c, r, 4.0f, Tokens.alpha(Tokens.CANVAS, 0.85f));

        final float clamped = Math.max(0.0f, Math.min(1.0f, fill));
        if (clamped > 0.001f) {
            final Rect in = r.inset(2.5f);

            final int cols = Math.max(2, Math.min(4, Math.round(in.w / 16.0f)));
            final int rows = Math.max(3, Math.min(9, Math.round(in.h / 16.0f)));
            final float gap = 1.6f;
            final float cw = (in.w - gap * (cols - 1)) / cols;
            final float ch = (in.h - gap * (rows - 1)) / rows;
            final float lit = clamped * rows;
            final int fullRows = (int) Math.floor(lit);
            final float partial = lit - fullRows;

            for (int row = 0; row < rows; row++) {
                final int fromBottom = rows - 1 - row;
                if (fromBottom > fullRows) {
                    continue;
                }
                final boolean topRow = fromBottom == fullRows;
                if (topRow && partial <= 0.02f) {
                    continue;
                }

                final float bob = topRow
                        ? (float) Math.sin(time * (2.4 + 3.0 * motion)) * motion * 1.6f : 0.0f;
                final float y = in.y + row * (ch + gap) + bob;
                for (int col = 0; col < cols; col++) {
                    final float shove = (fromBottom % 2 == 0 ? 0.0f : cw * 0.16f);
                    final float x = in.x + col * (cw + gap) + shove;
                    final float w = Math.min(cw, in.right() - x);
                    if (w < 1.0f) {
                        continue;
                    }
                    final float alpha = topRow ? 0.35f + 0.55f * partial : 0.92f;

                    Shapes.rect(c, x, y, w, ch,
                            Tokens.alpha(color, alpha * (0.72f + 0.28f * (fromBottom % 2))));
                }
            }

            final float surfaceY = in.bottom() - in.h * clamped;
            Shapes.rect(c, in.x, surfaceY, in.w, 1.2f, Tokens.alpha(color, 0.95f));
        }
        Shapes.roundedRectOutline(c, r, 4.0f, 1.0f, Tokens.LINE_HI);

        Shapes.rect(c, r.x + 1.0f, r.y + r.h * 0.34f, r.w - 2.0f, 1.0f,
                Tokens.alpha(Tokens.LINE_HI, 0.55f));
        Shapes.rect(c, r.x + 1.0f, r.y + r.h * 0.67f, r.w - 2.0f, 1.0f,
                Tokens.alpha(Tokens.LINE_HI, 0.55f));
    }

    private static void drawCells(PanelSurface c, Rect r, float fill, int color, boolean moving,
                                  float time) {
        final float nub = Math.min(6.0f, r.h * 0.06f);
        final Rect body = new Rect(r.x, r.y + nub, r.w, r.h - nub);
        Shapes.roundedRect(c, new Rect(r.centerX() - r.w * 0.16f, r.y, r.w * 0.32f, nub + 2.0f),
                1.5f, Tokens.LINE_HI);
        Shapes.roundedRect(c, body, 4.0f, Tokens.alpha(Tokens.CANVAS, 0.85f));
        final Rect in = body.inset(3.0f);
        if (!in.isEmpty()) {
            final int cols = Math.max(1, Math.min(2, Math.round(in.w / 30.0f)));
            final int rows = Math.max(3, Math.min(6, Math.round(in.h / 22.0f)));
            PageArt.cells(c, in, cols, rows, fill, color, moving, time);
        }
        Shapes.roundedRectOutline(c, body, 4.0f, 1.0f, Tokens.LINE_HI);
    }

    private static float laneWidth(PanelSurface c, Vessel v, float big) {
        if (!v.hasThresholds()) {
            return 0.0f;
        }
        return big > 0.45f ? 9.0f + c.textWidth("off", Text.MICRO) : 6.0f;
    }

    private static void drawThresholds(PanelSurface c, Rect r, Vessel v, float big) {
        if (!v.hasThresholds() || r.h < 40.0f) {
            return;
        }
        final float lowY = r.bottom() - r.h * Math.min(1.0f, v.lowerPct() / 100.0f);
        final float highY = r.bottom() - r.h * Math.min(1.0f, v.upperPct() / 100.0f);
        final float top = Math.min(lowY, highY);
        final float bottom = Math.max(lowY, highY);
        if (bottom - top > 1.0f) {
            Shapes.roundedRect(c, new Rect(r.right() + 2.0f, top, 2.0f, bottom - top), 1.0f,
                    Tokens.alpha(Tokens.ACCENT, 0.55f));
        }
        Shapes.rect(c, r.x, highY - 0.5f, r.w + 6.0f, 1.0f, Tokens.alpha(Tokens.CAUTION, 0.9f));
        Shapes.rect(c, r.x, lowY - 0.5f, r.w + 6.0f, 1.0f, Tokens.alpha(Tokens.GOOD, 0.9f));
        if (big > 0.45f) {
            c.textV("on", r.right() + 9.0f, highY, Tokens.CAUTION, Text.MICRO);
            c.textV("off", r.right() + 9.0f, lowY, Tokens.GOOD, Text.MICRO);
        }
    }

    private static void drawDetail(PanelSurface c, PeerArt p, Rect a, int status, float time) {
        final Vessel v = p.data();
        final float statsH = a.h >= 190.0f ? 60.0f : 0.0f;
        float y = a.y;

        if (statsH > 0.0f) {
            drawStats(c, new Rect(a.x, y, a.w, statsH), v);
            y += statsH + Tokens.S3;
        }

        final float rest = a.bottom() - y;
        if (rest < 54.0f) {
            return;
        }
        final boolean hasItems = !v.items().isEmpty();
        final boolean hasTrend = v.history() != null && v.history().length >= 4;

        final float contentsH = hasItems
                ? (hasTrend ? Math.max(76.0f, rest * 0.54f) : rest)
                : 0.0f;

        if (hasItems) {
            drawContents(c, new Rect(a.x, y, a.w, contentsH), v);
            y += contentsH + Tokens.S3;
        }
        if (hasTrend && a.bottom() - y >= 54.0f) {
            drawTrend(c, new Rect(a.x, y, a.w, a.bottom() - y), v, status, time);
        }
    }

    private static void drawStats(PanelSurface c, Rect a, Vessel v) {
        final float gap = Tokens.S2;
        final float w = (a.w - gap * 3.0f) / 4.0f;
        if (w < 46.0f) {
            return;
        }
        final Float rate = v.slopePerSecond();
        final Float eta = v.etaSeconds();
        final String etaText = eta == null ? "--" : PageArt.duration(eta, 3600.0f);

        stat(c, new Rect(a.x, a.y, w, a.h), "Stored", v.amountText(), Tokens.INK);
        stat(c, new Rect(a.x + w + gap, a.y, w, a.h), "Capacity",
                v.hasCapacity() ? v.capacityText() : "unknown",
                v.hasCapacity() ? Tokens.INK : Tokens.INK_3);
        stat(c, new Rect(a.x + (w + gap) * 2.0f, a.y, w, a.h), "Free",
                v.hasCapacity() ? v.format(v.headroom()) : "--",
                v.hasCapacity() ? Tokens.INK : Tokens.INK_3);
        stat(c, new Rect(a.x + (w + gap) * 3.0f, a.y, w, a.h),
                eta == null ? "Rate" : v.etaEnd() + " in",
                eta == null
                        ? (rate == null || v.steady() ? "steady"
                                : v.compact(Math.abs(rate) * 60.0f) + "/m")
                        : (etaText == null ? "--" : etaText),
                eta != null && eta < 120.0f ? v.statusColor() : Tokens.INK_2);
    }

    private static void stat(PanelSurface c, Rect r, String label, String value, int color) {
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = r.inset(Tokens.S2, Tokens.S2, Tokens.S2, Tokens.S2);
        c.text(Text.track(label), in.x, in.y, Tokens.INK_3, Text.MICRO);
        final Text.Fitted fitted = new Text.Fitted(value,
                Text.fitScale(c, value, in.w, in.h * 0.6f, Text.SUB, Text.MICRO));
        c.textV(fitted.text(), in.x, in.bottom() - Text.lineHeight(fitted.scale()) * 0.5f - 2.0f,
                color, fitted.scale());
    }

    private static void drawContents(PanelSurface c, Rect a, Vessel v) {
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = a.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        c.text(Text.track("Contents"), in.x, in.y, Tokens.INK_3, Text.MICRO);
        c.textRight(v.uniqueTypes() > 0 ? Peers.count(v.uniqueTypes(), "type") : "", in.right(),
                in.y, Tokens.INK_3, Text.MICRO);

        final float top = in.y + 15.0f;
        final float rowH = 14.0f;
        final int fits = (int) Math.floor((in.bottom() - top) / rowH);
        if (fits < 1) {
            return;
        }
        final List<Item> items = v.items();
        final int shown = Math.min(fits, items.size());

        float total = 0.0f;
        for (final Item item : items) {
            total += item.count();
        }
        final float biggest = items.isEmpty() ? 1.0f : Math.max(1.0f, items.get(0).count());

        final float countW = 52.0f;
        final float nameW = Math.max(40.0f, in.w * 0.44f);
        for (int i = 0; i < shown; i++) {
            final Item item = items.get(i);
            final float y = top + i * rowH;
            final int color = Tokens.seriesFor(item.name());
            c.textV(Text.truncate(c, item.name(), nameW, Text.MICRO), in.x, y + rowH * 0.5f,
                    Tokens.INK_2, Text.MICRO);

            final float barX = in.x + nameW + 6.0f;
            final float barW = in.right() - countW - barX;
            if (barW > 16.0f) {
                final Rect bar = new Rect(barX, y + rowH * 0.5f - 3.0f, barW, 6.0f);
                Shapes.roundedRect(c, bar, 3.0f, Tokens.LINE);
                Shapes.roundedRect(c, bar.withWidth(Math.max(2.0f,
                        barW * item.count() / biggest)), 3.0f, color);
            }
            c.textRightV(v.compact(item.count()), in.right(), y + rowH * 0.5f,
                    Tokens.INK, Text.MICRO);
        }
        if (items.size() > shown) {
            final float remainder = total - sumOf(items, shown);
            c.textV("+" + (items.size() - shown) + " more, " + v.compact(remainder),
                    in.x, top + shown * rowH + 5.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    private static float sumOf(List<Item> items, int count) {
        float total = 0.0f;
        for (int i = 0; i < count && i < items.size(); i++) {
            total += items.get(i).count();
        }
        return total;
    }

    private static void drawTrend(PanelSurface c, Rect a, Vessel v, int status, float time) {
        c.card(a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect in = a.inset(Tokens.S3, Tokens.S2, Tokens.S3, Tokens.S2);
        c.text(Text.track(v.hasCapacity() ? "Fill trend" : "Observed trend"), in.x, in.y,
                Tokens.INK_3, Text.MICRO);

        final float[] data = sample(v.hasCapacity() ? v.percentSeries() : v.history(), 110);
        final Rect plot = new Rect(in.x + 26.0f, in.y + 14.0f, in.w - 26.0f, in.h - 16.0f);
        if (plot.isEmpty() || data.length < 2) {
            c.textCenterV("collecting", plot.centerX(), plot.centerY(), Tokens.INK_3, Text.MICRO);
            return;
        }

        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (final float d : data) {
            lo = Math.min(lo, d);
            hi = Math.max(hi, d);
        }
        if (v.hasCapacity()) {
            lo = 0.0f;
            hi = 100.0f;
        } else {
            final float span = Math.max(1.0e-3f, hi - lo);
            lo -= span * 0.2f;
            hi += span * 0.2f;
            if (hi - lo < 1.0e-3f) {
                lo -= 0.5f;
                hi += 0.5f;
            }
            c.textRightV(v.compact(hi), plot.x - 5.0f, plot.y, Tokens.INK_3, Text.MICRO);
            c.textRightV(v.compact(lo), plot.x - 5.0f, plot.bottom(), Tokens.INK_3, Text.MICRO);
        }

        if (v.hasCapacity()) {
            for (final float mark : new float[]{0.0f, StorageReadings.LOW, 50.0f,
                    StorageReadings.HIGH, 100.0f}) {
                final float y = plot.bottom() - (mark - lo) / (hi - lo) * plot.h;
                final boolean edge = mark <= 0.01f || mark >= 99.99f;
                c.hairlineH(plot.x, y, plot.w, edge ? Tokens.alpha(Tokens.LINE_HI, 0.8f) : Tokens.LINE);
                if (edge || plot.h > 70.0f) {
                    c.textRightV(Math.round(mark) + "%", plot.x - 5.0f, y, Tokens.INK_3, Text.MICRO);
                }
            }
            if (v.hasThresholds()) {
                final float offY = plot.bottom() - (v.lowerPct() - lo) / (hi - lo) * plot.h;
                final float onY = plot.bottom() - (v.upperPct() - lo) / (hi - lo) * plot.h;
                c.hairlineH(plot.x, offY, plot.w, Tokens.alpha(Tokens.GOOD, 0.55f));
                c.hairlineH(plot.x, onY, plot.w, Tokens.alpha(Tokens.CAUTION, 0.55f));
            }
        }

        final float[] xy = new float[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            xy[i * 2] = plot.x + plot.w * (i / (float) (data.length - 1));
            xy[i * 2 + 1] = plot.bottom()
                    - (Math.max(lo, Math.min(hi, data[i])) - lo) / (hi - lo) * plot.h;
        }
        if (v.hasCapacity()) {
            c.areaFill(xy, data.length, plot.bottom(), Tokens.alpha(status, 0.24f),
                    Tokens.alpha(status, 0.02f));
        }
        Shapes.polyline(c, xy, data.length, Tokens.DATA_STROKE, status);
        Shapes.circle(c, xy[xy.length - 2], xy[xy.length - 1], 2.6f, status);

        final Float rate = v.slopePerSecond();

        final float flowX = Math.max(plot.x + 2.0f,
                in.x + c.textWidth(Text.track("Observed trend"), Text.MICRO) + 10.0f);
        if (rate != null && !v.steady() && plot.right() - flowX > 60.0f) {
            PageArt.flow(c, flowX, plot.y - 7.0f, plot.right() - 2.0f, plot.y - 7.0f,
                    1.4f, status, rate, FLOW_SCALE, time);
        }
    }

    public static void compareChart(PanelSurface c, Rect area, List<PeerArt> shown) {
        if (area.isEmpty()) {
            return;
        }
        c.card(area, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.text(Text.track("Level as a share of each store's own capacity"),
                area.x + 12.0f, area.y + 8.0f, Tokens.INK_3, Text.MICRO);
        final Rect plot = area.inset(38.0f, 28.0f, 34.0f, 22.0f);
        if (plot.isEmpty() || shown.isEmpty()) {
            return;
        }

        for (final float mark : new float[]{0.0f, StorageReadings.LOW, 50.0f,
                StorageReadings.HIGH, 100.0f}) {
            final float y = plot.bottom() - mark / 100.0f * plot.h;
            final boolean edge = mark <= 0.01f || mark >= 99.99f;
            c.hairlineH(plot.x, y, plot.w, edge ? Tokens.alpha(Tokens.LINE_HI, 0.8f) : Tokens.LINE);
            c.textRightV(Math.round(mark) + "%", plot.x - 6.0f, y, Tokens.INK_3, Text.MICRO);
        }

        final List<float[]> ends = new ArrayList<>();
        int plotted = 0;
        for (int s = 0; s < shown.size(); s++) {
            final float[] data = sample(shown.get(s).data().percentSeries(), 110);
            if (data.length < 2) {
                continue;
            }
            plotted++;
            final float[] xy = new float[data.length * 2];
            for (int i = 0; i < data.length; i++) {
                xy[i * 2] = plot.x + plot.w * (i / (float) (data.length - 1));
                xy[i * 2 + 1] = plot.bottom()
                        - Math.max(0.0f, Math.min(100.0f, data[i])) / 100.0f * plot.h;
            }
            Shapes.polyline(c, xy, data.length, Tokens.DATA_STROKE, shown.get(s).series());
            final float ly = xy[xy.length - 1];
            Shapes.circle(c, plot.right(), ly, 3.0f, shown.get(s).series());
            ends.add(new float[]{ly, s});
        }
        if (plotted == 0) {
            c.textCenterV("None of these stores reports a capacity to be a share of",
                    plot.centerX(), plot.centerY(), Tokens.INK_3, Text.META);
            return;
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

    public static void identify(PanelSurface c, Rect area, Rect card, PeerArt p, float t) {
        if (t < 0.02f || card.isEmpty() || card.w < 90.0f) {
            return;
        }
        final String s = "Highlighted in world · "
                + (p.bearing().isEmpty() ? "here" : p.bearing());
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
        final Rect box = area.inset(0.0f, HEADER_H, 0.0f, 0.0f).top(112.0f);
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenterV("No store linked", box.centerX(), box.centerY() - 18.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenterV("Point a Display Link at this panel from anything that holds something:",
                box.centerX(), box.centerY() + 2.0f, Tokens.INK_3, Text.META);
        c.textCenterV("a Stockpile Switch, a Content Observer on a vault, a tank, a battery",
                box.centerX(), box.centerY() + 18.0f, Tokens.INK_3, Text.META);
    }

    public static void emptyFilter(PanelSurface c, Rect area, Kind kind) {
        final Rect box = area.inset(0.0f, HEADER_H, 0.0f, 0.0f).top(88.0f);
        c.card(box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenterV("No " + kind.label.toLowerCase(Locale.ROOT) + " store linked",
                box.centerX(), box.centerY() - 9.0f, Tokens.INK_2, Text.BODY);
        c.textCenterV("Click the kind again to see everything on this panel",
                box.centerX(), box.centerY() + 9.0f, Tokens.INK_3, Text.META);
    }

    public static void kindIcon(PanelSurface c, float cx, float cy, float r, Kind kind, int color) {
        final float stroke = Math.max(1.2f, r * 0.24f);
        switch (kind) {
            case FLUID -> {
                c.tri(cx, cy - r, color, cx - r * 0.62f, cy + r * 0.15f, color,
                        cx + r * 0.62f, cy + r * 0.15f, color);
                Shapes.circle(c, cx, cy + r * 0.25f, r * 0.62f, color);
            }
            case ENERGY -> {
                Shapes.line(c, cx + r * 0.30f, cy - r, cx - r * 0.45f, cy + r * 0.08f,
                        stroke, color);
                Shapes.line(c, cx - r * 0.45f, cy + r * 0.08f, cx + r * 0.15f, cy + r * 0.08f,
                        stroke, color);
                Shapes.line(c, cx + r * 0.15f, cy + r * 0.08f, cx - r * 0.30f, cy + r,
                        stroke, color);
            }
            default -> {
                Shapes.roundedRectOutline(c, new Rect(cx - r * 0.85f, cy - r * 0.75f,
                        r * 1.7f, r * 1.5f), Math.max(1.5f, r * 0.25f), stroke, color);
                Shapes.rect(c, cx - r * 0.85f, cy - r * 0.25f, r * 1.7f, stroke * 0.9f, color);
            }
        }
    }

    static float ramp(float v, float lo, float hi) {
        return PeerRoster.ramp(v, lo, hi);
    }
}
