package com.createstats.statsmk3.page;

import java.util.List;
import java.util.Locale;

import com.createstats.data.WindowedTrend;
import com.createstats.rs.RsNetworkState;
import com.createstats.statsmk3.page.RsReadings.Stock;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// pure so it renders offline. otherwise every review is build a jar and fly to a test world
@OnlyIn(Dist.CLIENT)
public final class RsArt {
    private RsArt() {
    }

    public static final int RS = 0xFF31C6D9;
    public static final int RS_DEEP = 0xFF1B8CA0;
    public static final int RS_DIM = 0xFF0F2F37;

    public static final int UP = Tokens.GOOD;

    public static final int DOWN = Tokens.CAUTION;

    public static final int FLAT = Tokens.INK_3;

    public static final float HEADER_H = 60.0f;

    private static final float STEADY_AT = 0.02f;

    private static final float VOLATILE_AT = 0.12f;

    public static void diskGlyph(PanelSurface c, float x, float y, float size, int color) {
        final float notch = size * 0.26f;

        Shapes.roundedRect(c, new Rect(x, y, size, size), size * 0.12f, color);
        c.tri(x + size - notch, y, Tokens.CANVAS, x + size, y, Tokens.CANVAS,
                x + size, y + notch, Tokens.CANVAS);

        Shapes.rect(c, x + size * 0.30f, y + size * 0.08f, size * 0.34f, size * 0.34f,
                Tokens.mix(color, Tokens.CANVAS, 0.72f));

        Shapes.rect(c, x + size * 0.18f, y + size * 0.56f, size * 0.64f, size * 0.30f,
                Tokens.mix(color, Tokens.CANVAS, 0.55f));
    }

    public static float brand(PanelSurface c, float x, float centerY, float scale, float alpha) {
        final float glyph = 11.0f * scale;
        diskGlyph(c, x, centerY - glyph * 0.5f, glyph, Tokens.alpha(RS, 0.85f * alpha));
        final float textX = x + glyph + 5.0f * scale;

        c.textV(Text.track("REFINED"), textX, centerY - 6.0f * scale,
                Tokens.alpha(RS, alpha), Text.MICRO * scale);
        c.textV(Text.track("STORAGE"), textX, centerY + 6.0f * scale,
                Tokens.alpha(RS_DEEP, alpha), Text.MICRO * scale);
        return glyph + 5.0f * scale
                + Math.max(c.textWidth(Text.track("REFINED"), Text.MICRO * scale),
                c.textWidth(Text.track("STORAGE"), Text.MICRO * scale));
    }

    public static void sourceBadge(PanelSurface c, float x, float y, float size) {
        final Rect slot = new Rect(x, y, size, size);
        c.card(slot, Tokens.R_CHIP, Tokens.alpha(RS_DIM, 0.9f), Tokens.alpha(RS_DEEP, 0.5f));
        final net.minecraft.world.item.ItemStack grid = RsReadings.stackOf("refinedstorage:grid");
        if (grid != null && !grid.isEmpty()) {
            c.itemIcon(grid, x + size * 0.16f, y + size * 0.16f, size * 0.68f);
        } else {
            diskGlyph(c, x + size * 0.22f, y + size * 0.22f, size * 0.56f, Tokens.alpha(RS, 0.6f));
        }
    }

    public static final float RAIL_W = 268.0f;
    public static final float GAP = 10.0f;

    public static final float NETWORK_CARD_H = 150.0f;

    public static final float SELECTOR_H = 30.0f;

    public static Rect body(Rect area, boolean hasSelector) {
        final float top = area.y + HEADER_H + (hasSelector ? SELECTOR_H : 0.0f);
        return new Rect(area.x, top, area.w, area.bottom() - top);
    }

    public static Rect heroBox(Rect area, boolean hasSelector) {
        final Rect b = body(area, hasSelector);
        return new Rect(b.x, b.y, Math.max(180.0f, b.w - RAIL_W - GAP), b.h);
    }

    public static Rect rail(Rect area, boolean hasSelector) {
        final Rect b = body(area, hasSelector);
        return new Rect(b.right() - RAIL_W, b.y, RAIL_W, b.h);
    }

    public static Rect listBox(Rect area, boolean hasSelector) {
        final Rect r = rail(area, hasSelector);
        final float top = r.y + NETWORK_CARD_H + GAP + 20.0f;
        return new Rect(r.x, top, r.w, Math.max(20.0f, r.bottom() - top));
    }

    public static void page(PanelSurface c, Rect area, RsReadings.Board board, int networkCount,
                            WindowedTrend.Window window, String focusItemId, double shownCount,
                            float time, float scroll, String hovered, RowSink rowSink) {
        final boolean hasSelector = networkCount > 1;
        header(c, area, board, networkCount, 250.0f);
        if (board == null) {
            empty(c, body(area, hasSelector), "No Refined Storage network linked",
                    "Point a Display Link at a Grid or Controller and target this panel");
            return;
        }
        final List<Stock> stock = board.stock();
        hero(c, heroBox(area, hasSelector), RsReadings.hero(stock, focusItemId), board.state(),
                window, shownCount, time);

        final Rect rail = rail(area, hasSelector);
        networkCard(c, new Rect(rail.x, rail.y, rail.w, NETWORK_CARD_H), board.state());
        stockHeader(c, new Rect(rail.x, rail.y + NETWORK_CARD_H + GAP, rail.w, 18.0f),
                stock.size(),
                board.state() == null ? stock.size()
                        : Math.max(stock.size(), board.state().itemTypes()),
                window);
        stockList(c, listBox(area, hasSelector), stock, focusItemId, window, scroll,
                hovered == null ? "" : hovered, rowSink);
    }

    public interface RowSink {
        void row(Rect rect, String itemId);
    }

    public static void stockList(PanelSurface c, Rect list, List<Stock> stock, String focusItemId,
                                 WindowedTrend.Window window, float scroll, String hoveredItemId,
                                 RowSink sink) {
        if (stock.isEmpty()) {
            c.textCenterV("Network is empty", list.centerX(), list.y + 24.0f,
                    Tokens.INK_3, Text.MICRO);
            return;
        }
        final long peak = peak(stock);
        final Stock focused = RsReadings.hero(stock, focusItemId);
        final boolean scrollable = stock.size() * ROW_H + 6.0f > list.h;
        final float trackW = scrollable ? 6.0f : 0.0f;

        c.clip(list);
        float y = list.y - scroll;
        for (final Stock s : stock) {
            final Rect row = new Rect(list.x, y, list.w - trackW, ROW_H);
            y += ROW_H;
            if (sink != null) {
                sink.row(row, s.itemId());
            }
            if (row.bottom() < list.y || row.y > list.bottom()) {
                continue;
            }
            stockRow(c, row, s, window,
                    focused != null && focused.itemId().equals(s.itemId()),
                    s.itemId().equals(hoveredItemId), peak);
        }
        c.unclip();

        Shapes.rect(c, list.x, list.y - 1.0f, list.w, 1.0f, Tokens.LINE);
    }

    public static void header(PanelSurface c, Rect area, RsReadings.Board board, int networkCount,
                              float reserveRight) {
        final Rect band = new Rect(area.x, area.y, area.w, HEADER_H);
        sourceBadge(c, band.x, band.y + 6.0f, 26.0f);
        final float brandW = brand(c, band.x + 33.0f, band.y + 19.0f, 1.0f, 1.0f);

        final float nameX = band.x + 33.0f + brandW + 14.0f;
        final boolean online = board != null && board.online();
        final String name = board == null ? "No network linked" : board.label();
        final float room = Math.max(40.0f, band.right() - reserveRight - nameX - 96.0f);
        c.textV(Text.truncate(c, name, room, Text.SUB), nameX, band.y + 17.0f,
                online ? Tokens.INK : Tokens.INK_2, Text.SUB);

        final String state = board == null ? "UNLINKED" : (online ? "ONLINE" : "OFFLINE");
        final int stateInk = board == null ? Tokens.INK_3 : (online ? Tokens.GOOD : Tokens.CRITICAL);
        final float dotX = nameX + 5.0f;
        Shapes.circle(c, dotX, band.y + 35.0f, 3.0f, stateInk);
        c.textV(Text.track(state), dotX + 8.0f, band.y + 35.0f, stateInk, Text.MICRO);

        if (networkCount > 1) {
            final float sx = dotX + 8.0f + c.textWidth(Text.track(state), Text.MICRO) + 12.0f;
            c.textV(networkCount + " networks linked", sx, band.y + 35.0f, Tokens.INK_3, Text.MICRO);
        }
        Shapes.rect(c, band.x, band.bottom() - 1.0f, band.w, 1.0f, Tokens.LINE);
    }

    public static void hero(PanelSurface c, Rect r, Stock stock, RsNetworkState state,
                            WindowedTrend.Window window, double shown, float time) {
        c.card(r, Tokens.R_PANEL, Tokens.SURFACE, Tokens.LINE);
        if (stock == null) {
            c.textCenterV("Nothing in this network yet", r.centerX(), r.centerY(),
                    Tokens.INK_3, Text.META);
            return;
        }
        final float pad = Math.min(18.0f, r.w * 0.06f);
        final float iconSize = Math.min(48.0f, Math.max(20.0f, r.h * 0.14f));

        final Rect slot = new Rect(r.x + pad, r.y + pad, iconSize, iconSize);
        c.card(slot, Tokens.R_CHIP, Tokens.RAISED, Tokens.LINE_HI);
        c.itemIcon(stock.stack(), slot.x + iconSize * 0.14f, slot.y + iconSize * 0.14f,
                iconSize * 0.72f);

        final float nameX = slot.right() + 12.0f;
        final float nameRoom = r.right() - pad - nameX;
        c.textV(Text.truncate(c, stock.name(), nameRoom, Text.TITLE), nameX,
                slot.y + iconSize * 0.34f, Tokens.INK, Text.TITLE);
        c.textV(Text.truncate(c, stock.itemId(), nameRoom, Text.MICRO), nameX,
                slot.y + iconSize * 0.74f, Tokens.INK_3, Text.MICRO);

        final float countY = slot.bottom() + 30.0f;
        final String count = Text.group(Math.round(shown));
        final float countScale = fitScale(c, count, r.w - pad * 2.0f, Text.HERO);
        c.text(count, r.x + pad, countY - Text.lineHeight(countScale) * 0.5f, Tokens.INK, countScale);

        final long delta = stock.delta(window);
        final float perMin = stock.perMinute(window);
        final int trendInk = trendColor(delta, stock.amount());
        final float trendY = countY + Text.lineHeight(countScale) * 0.5f + 14.0f;
        arrow(c, r.x + pad + 4.0f, trendY, 5.0f, delta, trendInk);
        final String movement = delta == 0L
                ? "no change in " + window.label()
                : signed(delta) + " in " + window.label();
        c.textV(movement, r.x + pad + 14.0f, trendY, trendInk, Text.META);
        if (Math.abs(perMin) >= 0.05f) {
            c.textV(rate(perMin), r.x + pad + 18.0f + c.textWidth(movement, Text.META), trendY,
                    Tokens.INK_3, Text.META);
        }

        final float bodyTop = trendY + 16.0f;
        final float stripH = 34.0f;
        final float compareH = 46.0f;
        final float totalH = 76.0f;
        float room = r.bottom() - pad - bodyTop;

        final boolean showCompare = room > 150.0f + stripH + compareH;
        final boolean showTotal = state != null && room > 150.0f + stripH + compareH + totalH + 16.0f;
        room -= stripH + 6.0f;
        if (showCompare) {
            room -= compareH + 8.0f;
        }
        if (showTotal) {
            room -= totalH + 8.0f;
        }
        final float chartH = Math.max(30.0f, Math.min(room, 320.0f));

        float y = bodyTop;
        chart(c, new Rect(r.x + pad, y, r.w - pad * 2.0f, chartH), stock, window, trendInk, time);
        y += chartH + 6.0f;
        statStrip(c, new Rect(r.x + pad, y, r.w - pad * 2.0f, stripH), stock, window);
        y += stripH;
        if (showCompare) {
            y += 8.0f;
            windowCompare(c, new Rect(r.x + pad, y, r.w - pad * 2.0f, compareH), stock, window);
            y += compareH;
        }
        if (showTotal) {
            y += 8.0f;
            networkCurve(c, new Rect(r.x + pad, y, r.w - pad * 2.0f,
                    Math.max(40.0f, r.bottom() - pad - y)), state, window);
        }
    }

    public static void networkCurve(PanelSurface c, Rect r, RsNetworkState state,
                                    WindowedTrend.Window window) {
        if (r.w < 80.0f || r.h < 30.0f || state == null) {
            return;
        }
        c.card(r, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
        c.textV(Text.track("NETWORK TOTAL"), r.x + 8.0f, r.y + 11.0f, Tokens.INK_3, Text.MICRO);
        c.textRightV(Text.group(state.itemsStored()), r.right() - 8.0f, r.y + 11.0f,
                Tokens.INK_2, Text.MICRO);

        final float[] data = state.totalSeries(window);
        final Rect plot = r.inset(8.0f, 20.0f, 8.0f, 7.0f);
        if (data.length < 2 || plot.h < 8.0f) {
            c.textCenterV("collecting", plot.centerX(), plot.centerY(), Tokens.INK_3, Text.MICRO);
            return;
        }
        float min = data[0];
        float max = data[0];
        for (final float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        final float span = Math.max(1.0f, max - min);

        final float fill = Math.min(1.0f, Math.max(0.12f,
                (data.length - 1) / (float) (WindowedTrend.TIER_SAMPLES - 1)));
        final Rect used = new Rect(plot.right() - plot.w * fill, plot.y, plot.w * fill, plot.h);
        final float[] pts = new float[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            pts[i * 2] = used.x + used.w * i / (float) (data.length - 1);
            pts[i * 2 + 1] = used.bottom() - used.h * 0.86f * (data[i] - min) / span - used.h * 0.07f;
        }
        final long netDelta = (long) (data[data.length - 1] - data[0]);
        final int ink = trendColor(netDelta, state.itemsStored());
        c.areaFill(pts, data.length, used.bottom(), Tokens.alpha(ink, 0.18f),
                Tokens.alpha(ink, 0.0f));
        c.polyline(pts, data.length, 1.4f, Tokens.alpha(ink, 0.9f));
    }

    public static void windowCompare(PanelSurface c, Rect r, Stock stock,
                                     WindowedTrend.Window selected) {
        if (r.w < 150.0f || r.h < 30.0f) {
            return;
        }
        final WindowedTrend.Window[] windows = WindowedTrend.Window.values();
        final float cell = (r.w - (windows.length - 1) * 6.0f) / windows.length;
        for (int i = 0; i < windows.length; i++) {
            final WindowedTrend.Window w = windows[i];
            final Rect box = new Rect(r.x + i * (cell + 6.0f), r.y, cell, r.h);
            final boolean on = w == selected;
            c.card(box, Tokens.R_CHIP, on ? Tokens.RAISED : Tokens.SURFACE,
                    on ? Tokens.alpha(RS, 0.45f) : Tokens.LINE);

            final int covered = stock.coverageSeconds(w);
            c.textV(Text.track(w.label().toUpperCase(Locale.ROOT)), box.x + 8.0f, box.y + 12.0f,
                    on ? Tokens.INK_2 : Tokens.INK_3, Text.MICRO);

            if (covered < w.spanSeconds()) {
                c.textRightV(covered <= 0 ? "no data" : duration(covered) + " so far",
                        box.right() - 8.0f, box.y + 12.0f, Tokens.INK_3, Text.MICRO);
            }
            final long delta = stock.delta(w);
            final int ink = trendColor(delta, stock.amount());
            arrow(c, box.x + 12.0f, box.y + 30.0f, 4.0f, delta, ink);
            c.textV(delta == 0L ? "steady" : signed(delta), box.x + 21.0f, box.y + 30.0f, ink,
                    Text.META);
            c.textRightV("σ " + Text.compact(stock.spread(w)), box.right() - 8.0f, box.y + 30.0f,
                    Tokens.INK_3, Text.MICRO);
        }
    }

    public static void chart(PanelSurface c, Rect r, Stock stock, WindowedTrend.Window window,
                             int ink, float time) {
        if (r.w < 20.0f || r.h < 16.0f) {
            return;
        }
        c.card(r, Tokens.R_CHIP, Tokens.CANVAS, Tokens.LINE);
        final float[] data = RsReadings.seriesFor(stock, window);
        final int covered = RsReadings.coveredSeconds(stock, window);
        final Rect plot = r.inset(8.0f, 8.0f, 8.0f, 14.0f);

        for (int i = 1; i < 4; i++) {
            c.hairlineH(plot.x, plot.y + plot.h * i / 4.0f, plot.w, Tokens.alpha(Tokens.LINE, 0.55f));
        }
        if (data.length < 2) {
            c.textCenterV("collecting", plot.centerX(), plot.centerY(), Tokens.INK_3, Text.MICRO);
            axis(c, r, window, covered);
            return;
        }
        float min = data[0];
        float max = data[0];
        for (final float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        final float span = Math.max(1.0f, max - min);
        final float lo = min - span * 0.1f;
        final float hi = max + span * 0.1f;

        final float fill = window.spanSeconds() <= 0
                ? 1.0f : Math.min(1.0f, Math.max(0.12f, covered / (float) window.spanSeconds()));
        final Rect used = new Rect(plot.right() - plot.w * fill, plot.y, plot.w * fill, plot.h);

        final float[] pts = new float[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            pts[i * 2] = used.x + (data.length == 1 ? used.w
                    : used.w * i / (float) (data.length - 1));
            pts[i * 2 + 1] = used.bottom() - used.h * (data[i] - lo) / (hi - lo);
        }
        c.areaFill(pts, data.length, used.bottom(), Tokens.alpha(ink, 0.26f),
                Tokens.alpha(ink, 0.0f));
        c.polyline(pts, data.length, Tokens.DATA_STROKE, ink);

        final double mean = meanOf(data);
        final double sigma = sigmaOf(data, mean);
        if (sigma > 0.0) {
            final float bandTop = used.bottom() - used.h * (float) ((mean + sigma) - lo) / (hi - lo);
            final float bandBottom = used.bottom() - used.h * (float) ((mean - sigma) - lo) / (hi - lo);
            final float top = Math.max(used.y, bandTop);
            final float bottom = Math.min(used.bottom(), bandBottom);

            Shapes.rect(c, used.x, top, used.w, bottom - top,
                    Tokens.alpha(Tokens.DEVIATION_LO, 0.07f));
            c.hairlineH(used.x, top, used.w, Tokens.alpha(Tokens.DEVIATION_LO, 0.35f));
            c.hairlineH(used.x, bottom, used.w, Tokens.alpha(Tokens.DEVIATION_LO, 0.35f));
        }
        final float meanY = used.bottom() - used.h * (float) (mean - lo) / (hi - lo);
        c.hairlineH(used.x, meanY, used.w, Tokens.alpha(Tokens.DEVIATION_LO, 0.8f));

        final float pulse = Math.abs(stock.delta(window)) > 0
                ? 2.6f + 0.9f * (float) Math.sin(time * 3.2f) : 2.6f;
        Shapes.circle(c, pts[pts.length - 2], pts[pts.length - 1], pulse, ink);

        c.textV(Text.compact(max), plot.x + 3.0f, plot.y + 5.0f, Tokens.INK_3, Text.MICRO);
        c.textV(Text.compact(min), plot.x + 3.0f, plot.bottom() - 5.0f, Tokens.INK_3, Text.MICRO);
        axis(c, r, window, covered);
    }

    private static void axis(PanelSurface c, Rect r, WindowedTrend.Window window, int covered) {
        final String left = covered >= window.spanSeconds() || covered <= 0
                ? "-" + window.label()
                : "-" + duration(covered) + " so far";
        c.textV(left, r.x + 8.0f, r.bottom() - 7.0f, Tokens.INK_3, Text.MICRO);
        c.textRightV("now", r.right() - 8.0f, r.bottom() - 7.0f, Tokens.INK_3, Text.MICRO);
    }

    public static void statStrip(PanelSurface c, Rect r, Stock stock, WindowedTrend.Window window) {
        if (r.w < 60.0f || r.h < 18.0f) {
            return;
        }
        final float cell = r.w / 3.0f;
        final float sigma = stock.spread(window);
        final float rel = stock.relativeSpread(window);

        cellStat(c, new Rect(r.x, r.y, cell, r.h), "SPREAD",
                "σ " + Text.compact(sigma), Tokens.INK_2);

        final String steadiness = rel < STEADY_AT ? "STEADY"
                : rel < VOLATILE_AT ? "VARIABLE" : "VOLATILE";
        final int steadyInk = rel < STEADY_AT ? Tokens.GOOD
                : rel < VOLATILE_AT ? Tokens.CAUTION : Tokens.CRITICAL;
        cellStat(c, new Rect(r.x + cell, r.y, cell, r.h), "STEADINESS",
                steadiness + "  " + String.format(Locale.ROOT, "%.1f%%", rel * 100.0f), steadyInk);

        final float perMin = stock.perMinute(window);
        final String runway;
        final int runwayInk;
        if (perMin < -0.01f && stock.amount() > 0L) {
            final float minutes = stock.amount() / -perMin;
            runway = "empty in " + duration(Math.round(minutes * 60.0f));
            runwayInk = minutes < 10.0f ? Tokens.CRITICAL
                    : minutes < 60.0f ? Tokens.CAUTION : Tokens.INK_2;
        } else if (perMin > 0.01f) {
            runway = "growing";
            runwayInk = Tokens.GOOD;
        } else {
            runway = "holding";
            runwayInk = Tokens.INK_3;
        }
        cellStat(c, new Rect(r.x + cell * 2.0f, r.y, cell, r.h), "OUTLOOK", runway, runwayInk);
    }

    private static void cellStat(PanelSurface c, Rect r, String label, String value, int ink) {
        c.textV(Text.track(label), r.x, r.y + 7.0f, Tokens.INK_3, Text.MICRO);
        c.textV(Text.truncate(c, value, r.w - 6.0f, Text.META), r.x, r.y + 22.0f, ink, Text.META);
    }

    public static void networkCard(PanelSurface c, Rect r, RsNetworkState s) {
        c.card(r, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        if (s == null) {
            c.textCenterV("No network data", r.centerX(), r.centerY(), Tokens.INK_3, Text.META);
            return;
        }
        final float pad = 12.0f;
        c.textV(Text.track("NETWORK"), r.x + pad, r.y + 13.0f, Tokens.INK_3, Text.MICRO);

        c.textV(Text.group(s.itemsStored()), r.x + pad, r.y + 34.0f, Tokens.INK, Text.SUB);
        c.textRightV(s.itemTypes() + " types", r.right() - pad, r.y + 34.0f, Tokens.INK_2, Text.META);

        final Rect bar = new Rect(r.x + pad, r.y + 48.0f, r.w - pad * 2.0f, 8.0f);
        Shapes.roundedRect(c, bar, 4.0f, Tokens.LINE);
        if (s.hasCapacity()) {
            final float pct = Math.max(0.0f, Math.min(s.fillPercent(), 100.0f));
            Shapes.roundedRect(c, bar.withWidth(Math.max(3.0f, bar.w * pct / 100.0f)), 4.0f,
                    Tokens.statusFor(pct / 100.0f));
            c.textV(String.format(Locale.ROOT, "%.1f%% of %s", pct, Text.compact(s.itemCapacity())),
                    r.x + pad, r.y + 68.0f, Tokens.INK_2, Text.MICRO);
        } else {
            c.textV("capacity unknown", r.x + pad, r.y + 68.0f, Tokens.INK_3, Text.MICRO);
        }

        float y = r.y + 86.0f;
        y = statLine(c, r, y, "Disks", String.valueOf(s.storageCount()));
        if (s.patternCount() > 0) {
            y = statLine(c, r, y, "Patterns", String.valueOf(s.patternCount()));
        }
        if (s.fluidTypes() > 0) {
            y = statLine(c, r, y, "Fluids",
                    Text.compact(s.fluidsStored()) + " mB, " + s.fluidTypes() + " types");
        }
        if (s.energyCapacity() > 0L) {
            statLine(c, r, y, "Energy", Text.compact(s.energyStored()) + " / "
                    + Text.compact(s.energyCapacity()) + " FE");
        }
    }

    private static float statLine(PanelSurface c, Rect r, float y, String label, String value) {
        if (y > r.bottom() - 10.0f) {
            return y;
        }
        c.textV(label, r.x + 12.0f, y, Tokens.INK_3, Text.MICRO);
        c.textRightV(Text.truncate(c, value, r.w * 0.62f, Text.MICRO), r.right() - 12.0f, y,
                Tokens.INK_2, Text.MICRO);
        return y + 15.0f;
    }

    public static final float ROW_H = 30.0f;

    public static void stockRow(PanelSurface c, Rect r, Stock stock, WindowedTrend.Window window,
                                boolean focused, boolean hovered, float maxAmount) {
        if (r.h < 10.0f) {
            return;
        }
        if (focused || hovered) {
            c.card(r, Tokens.R_CHIP, focused ? Tokens.RAISED : Tokens.alpha(Tokens.RAISED, 0.6f),
                    focused ? Tokens.alpha(RS, 0.55f) : Tokens.LINE);
        }

        if (maxAmount > 0.0f) {
            final float w = Math.max(1.0f, (r.w - 8.0f) * (float) (stock.amount() / (double) maxAmount));
            Shapes.rect(c, r.x + 4.0f, r.bottom() - 2.5f, w, 1.5f,
                    Tokens.alpha(focused ? RS : Tokens.LINE_HI, focused ? 0.8f : 0.7f));
        }

        final float icon = Math.min(18.0f, r.h - 8.0f);
        c.itemIcon(stock.stack(), r.x + 6.0f, r.centerY() - icon * 0.5f, icon);

        final long delta = stock.delta(window);
        final int ink = trendColor(delta, stock.amount());

        final String amount = Text.group(stock.amount());
        final float amountW = c.textWidth(amount, Text.META);
        final String change = delta == 0L ? "" : signed(delta);
        final float changeW = change.isEmpty() ? 0.0f : c.textWidth(change, Text.MICRO) + 10.0f;

        final float nameX = r.x + icon + 12.0f;
        final float sparkW = Math.min(52.0f, r.w * 0.22f);
        final float textRight = r.right() - 8.0f - amountW - changeW - sparkW - 8.0f;
        c.textV(Text.truncate(c, stock.name(), Math.max(20.0f, textRight - nameX), Text.META),
                nameX, r.centerY(), focused ? Tokens.INK : Tokens.INK_2, Text.META);

        if (sparkW >= 20.0f) {
            sparkline(c, new Rect(r.right() - 8.0f - amountW - changeW - sparkW,
                    r.y + 5.0f, sparkW, r.h - 12.0f), stock, window, ink);
        }
        if (!change.isEmpty()) {
            c.textRightV(change, r.right() - 8.0f - amountW - 8.0f, r.centerY(), ink, Text.MICRO);
        }
        c.textRightV(amount, r.right() - 8.0f, r.centerY(), Tokens.INK, Text.META);
    }

    private static void sparkline(PanelSurface c, Rect r, Stock stock, WindowedTrend.Window window,
                                  int ink) {
        if (r.w < 8.0f || r.h < 4.0f) {
            return;
        }

        Shapes.rect(c, r.x, r.bottom() - 0.5f, r.w, 1.0f, Tokens.alpha(Tokens.LINE, 0.7f));
        final float[] data = RsReadings.seriesFor(stock, window);
        if (data.length < 2) {
            return;
        }
        float min = data[0];
        float max = data[0];
        for (final float v : data) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        final float span = Math.max(1.0f, max - min);
        final float[] pts = new float[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            pts[i * 2] = r.x + r.w * i / (float) (data.length - 1);
            pts[i * 2 + 1] = r.bottom() - r.h * (data[i] - min) / span;
        }
        c.polyline(pts, data.length, 1.2f, Tokens.alpha(ink, 0.85f));
    }

    public static void stockHeader(PanelSurface c, Rect r, int shown, int total,
                                   WindowedTrend.Window window) {
        c.textV(Text.track("STOCK"), r.x + 6.0f, r.centerY(), Tokens.INK_3, Text.MICRO);
        final String scope = total > shown ? shown + " of " + total : String.valueOf(total);
        c.textV(scope, r.x + 6.0f + c.textWidth(Text.track("STOCK"), Text.MICRO) + 8.0f,
                r.centerY(), Tokens.INK_3, Text.MICRO);
        c.textRightV("Δ " + window.label(), r.right() - 8.0f, r.centerY(),
                Tokens.INK_3, Text.MICRO);
        c.hairlineH(r.x, r.bottom(), r.w, Tokens.LINE);
    }

    public static void empty(PanelSurface c, Rect area, String title, String hint) {
        final Rect card = new Rect(area.centerX() - 170.0f, area.centerY() - 54.0f, 340.0f, 108.0f);
        c.card(card, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        diskGlyph(c, card.centerX() - 13.0f, card.y + 16.0f, 26.0f, Tokens.alpha(RS, 0.5f));
        c.textCenterV(title, card.centerX(), card.y + 62.0f, Tokens.INK_2, Text.META);
        c.textCenterV(hint, card.centerX(), card.y + 82.0f, Tokens.INK_3, Text.MICRO);
    }

    public static void arrow(PanelSurface c, float cx, float cy, float size, long delta, int ink) {
        if (delta > 0L) {
            c.tri(cx, cy - size, ink, cx - size * 0.8f, cy + size * 0.5f, ink,
                    cx + size * 0.8f, cy + size * 0.5f, ink);
        } else if (delta < 0L) {
            c.tri(cx, cy + size, ink, cx - size * 0.8f, cy - size * 0.5f, ink,
                    cx + size * 0.8f, cy - size * 0.5f, ink);
        } else {
            Shapes.rect(c, cx - size * 0.8f, cy - 1.0f, size * 1.6f, 2.0f, ink);
        }
    }

    public static int trendColor(long delta, long amount) {
        if (delta == 0L || amount <= 0L) {
            return delta > 0L ? UP : FLAT;
        }
        final double fraction = Math.abs(delta) / (double) amount;
        if (fraction < 0.001) {
            return FLAT;
        }
        return delta > 0L ? UP : DOWN;
    }

    public static String signed(long delta) {
        return (delta > 0L ? "+" : delta < 0L ? "-" : "") + Text.compact(Math.abs(delta));
    }

    private static String rate(float perMinute) {
        return "(" + (perMinute > 0.0f ? "+" : "-") + Text.compact(Math.abs(perMinute)) + "/min)";
    }

    public static String duration(int seconds) {
        if (seconds < 90) {
            return Math.max(0, seconds) + "s";
        }
        if (seconds < 5400) {
            return Math.round(seconds / 60.0f) + "m";
        }
        if (seconds < 172800) {
            return Math.round(seconds / 3600.0f) + "h";
        }
        return Math.round(seconds / 86400.0f) + "d";
    }

    private static float fitScale(PanelSurface c, String s, float maxWidth, float preferred) {
        float scale = preferred;
        while (scale > Text.SUB && c.textWidth(s, scale) > maxWidth) {
            scale -= 0.25f;
        }
        return scale;
    }

    private static double meanOf(float[] data) {
        double sum = 0.0;
        for (final float v : data) {
            sum += v;
        }
        return data.length == 0 ? 0.0 : sum / data.length;
    }

    private static double sigmaOf(float[] data, double mean) {
        if (data.length < 2) {
            return 0.0;
        }
        double acc = 0.0;
        for (final float v : data) {
            final double d = v - mean;
            acc += d * d;
        }
        return Math.sqrt(acc / data.length);
    }

    public static long peak(List<Stock> stock) {
        long max = 0L;
        for (final Stock s : stock) {
            max = Math.max(max, s.amount());
        }
        return max;
    }
}
