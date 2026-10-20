package com.createstats.statsmk3.paint;

import java.util.List;

import com.createstats.data.WindowedTrend;
import com.createstats.statsmk3.page.RsArt;
import com.createstats.statsmk3.page.RsReadings;
import com.createstats.statsmk3.page.RsReadings.Board;
import com.createstats.statsmk3.page.RsReadings.Stock;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RefinedStorageFacePainter {
    private RefinedStorageFacePainter() {
    }

    private static final float ROW_UNIT = 16.0f;

    public static void paint(PanelSurface s, Rect area, PanelModel model, Lod lod) {
        final PanelStyle style = model.style;
        Shapes.rect(s, area, style.ground);
        s.advanceLayer();

        final Board net = model.refinedStorage.isEmpty() ? null : model.refinedStorage.get(0);
        if (net == null) {
            paintNoData(s, area, "No storage network", "Link a Grid to this panel");
            return;
        }
        final List<Stock> stock = net.stock();
        final Stock hero = RsReadings.hero(stock, model.rsFocusItem);
        final WindowedTrend.Window window = WindowedTrend.Window.byOrdinal(model.rsWindow);

        if (!net.online()) {
            paintNoData(s, area, "Network offline", "No controller, or no power");
            return;
        }
        if (hero == null) {
            paintNoData(s, area, "Network is empty", "Nothing stored yet");
            return;
        }
        final float scale = faceScale(area);

        if (lod.isGlyph()) {
            paintGlyph(s, area, hero, window, scale);
            return;
        }
        final float pad = 6.0f * scale;

        if (area.h - pad * 2.0f < 86.0f * scale) {
            paintSolo(s, area, hero, window, scale);
            return;
        }

        float y = area.y + pad;
        if (lod.showsHeader()) {
            y = drawHeader(s, area, net, scale, pad, lod);
        }
        final Rect body = new Rect(area.x + pad, y, area.w - pad * 2.0f, area.bottom() - pad - y);
        if (body.h <= 10.0f) {
            return;
        }
        final float heroH = Math.min(body.h * 0.62f, 34.0f * scale);
        drawHero(s, new Rect(body.x, body.y, body.w, heroH), hero, window, scale);

        final Rect rest = new Rect(body.x, body.y + heroH + 3.0f * scale, body.w,
                body.h - heroH - 3.0f * scale);
        if (rest.h < 12.0f * scale) {
            return;
        }
        // curve only once it's wide enough to be a curve

        final boolean plot = !lod.collapsesToPrimary() && rest.w > 90.0f * scale
                && rest.h > 34.0f * scale;
        if (plot) {
            final float chartH = Math.min(rest.h * 0.55f, 44.0f * scale);
            drawCurve(s, new Rect(rest.x, rest.y, rest.w, chartH), hero, window, scale, model.time);
            drawRows(s, new Rect(rest.x, rest.y + chartH + 3.0f * scale, rest.w,
                    rest.h - chartH - 3.0f * scale), stock, hero, window, scale);
        } else {
            drawRows(s, rest, stock, hero, window, scale);
        }
    }

    private static void paintGlyph(PanelSurface s, Rect area, Stock hero,
                                   WindowedTrend.Window window, float scale) {
        final int ink = RsArt.trendColor(hero.delta(window), hero.amount());
        Shapes.rect(s, area.x, area.y, area.w, area.h, Tokens.alpha(ink, 0.07f));
        final float icon = Math.min(area.w, area.h) * 0.40f;
        s.itemIcon(hero.stack(), area.centerX() - icon * 0.5f,
                area.y + area.h * 0.16f, icon);
        final String count = Text.compact(hero.amount());
        s.textCenterV(count, area.centerX(), area.y + area.h * 0.72f, Tokens.INK,
                Math.max(Text.SUB, Math.min(Text.HERO, area.h / 44.0f)));
        RsArt.arrow(s, area.centerX(), area.y + area.h * 0.90f, Math.max(2.5f, area.h * 0.045f),
                hero.delta(window), ink);
    }

    private static void paintSolo(PanelSurface s, Rect area, Stock hero,
                                  WindowedTrend.Window window, float scale) {
        final int ink = RsArt.trendColor(hero.delta(window), hero.amount());

        final float icon = Math.min(area.h * 0.40f, area.w * 0.26f);
        s.itemIcon(hero.stack(), area.x + 4.0f * scale, area.y + 4.0f * scale, icon);
        final float textX = area.x + 4.0f * scale + icon + 4.0f * scale;
        s.textV(Text.truncate(s, hero.name(), area.right() - 3.0f - textX, Text.MICRO * scale),
                textX, area.y + 6.0f * scale + icon * 0.2f, Tokens.INK_2, Text.MICRO * scale);
        final String count = Text.compact(hero.amount());
        s.textV(count, textX, area.y + 6.0f * scale + icon * 0.70f, Tokens.INK,
                Text.fitScale(s, count, area.right() - 4.0f * scale - textX, icon * 0.85f,
                        Text.TITLE * scale, Text.META * scale));
        final long delta = hero.delta(window);
        RsArt.arrow(s, area.x + 6.0f * scale, area.bottom() - 7.0f * scale, 3.0f * scale, delta, ink);
        s.textV(delta == 0L ? "steady " + window.label()
                        : RsArt.signed(delta) + " " + window.label(),
                area.x + 12.0f * scale, area.bottom() - 7.0f * scale, ink, Text.MICRO * scale);
    }

    private static float drawHeader(PanelSurface s, Rect area, Board net, float scale, float pad,
                                   Lod lod) {
        final float y = area.y + pad;
        final float h = 12.0f * scale;
        RsArt.diskGlyph(s, area.x + pad, y, h * 0.8f, Tokens.alpha(RsArt.RS, 0.85f));
        final float nameX = area.x + pad + h * 0.8f + 4.0f * scale;
        final String totals = net.state() == null ? ""
                : Text.compact(net.state().itemsStored()) + " · " + net.state().itemTypes() + " types";
        final float totalsW = totals.isEmpty() ? 0.0f : s.textWidth(totals, Text.MICRO * scale) + 6.0f;
        s.textV(Text.truncate(s, net.label(), area.right() - pad - totalsW - nameX,
                        Text.META * scale), nameX, y + h * 0.42f,
                Tokens.alpha(Tokens.INK_2, lod.headerAlpha()), Text.META * scale);
        if (!totals.isEmpty()) {
            s.textRightV(totals, area.right() - pad, y + h * 0.42f,
                    Tokens.alpha(Tokens.INK_3, lod.headerAlpha()), Text.MICRO * scale);
        }
        s.hairlineH(area.x + pad, y + h + 2.0f * scale, area.w - pad * 2.0f, Tokens.LINE);
        return y + h + 5.0f * scale;
    }

    private static void drawHero(PanelSurface s, Rect r, Stock hero, WindowedTrend.Window window,
                                 float scale) {
        final int ink = RsArt.trendColor(hero.delta(window), hero.amount());
        final float icon = Math.min(r.h * 0.72f, 26.0f * scale);
        s.itemIcon(hero.stack(), r.x, r.centerY() - icon * 0.5f, icon);

        final float textX = r.x + icon + 5.0f * scale;
        s.textV(Text.truncate(s, hero.name(), r.w - icon - 6.0f * scale, Text.MICRO * scale),
                textX, r.y + r.h * 0.24f, Tokens.INK_2, Text.MICRO * scale);

        final String count = Text.group(hero.amount());
        final float countScale = Text.fitScale(s, count, r.right() - textX, r.h * 0.45f,
                Text.TITLE * scale, Text.MICRO * scale);
        s.textV(count, textX, r.y + r.h * 0.60f, Tokens.INK, countScale);

        final long delta = hero.delta(window);
        final String movement = delta == 0L
                ? "steady over " + window.label()
                : RsArt.signed(delta) + " in " + window.label();
        RsArt.arrow(s, textX + 2.5f * scale, r.bottom() - 4.0f * scale, 2.8f * scale, delta, ink);
        s.textV(Text.truncate(s, movement, r.right() - textX - 10.0f * scale, Text.MICRO * scale),
                textX + 8.0f * scale, r.bottom() - 4.0f * scale, ink, Text.MICRO * scale);
    }

    private static void drawCurve(PanelSurface s, Rect r, Stock hero, WindowedTrend.Window window,
                                  float scale, float time) {
        RsArt.chart(s, r, hero, window, RsArt.trendColor(hero.delta(window), hero.amount()), time);
    }

    private static void drawRows(PanelSurface s, Rect r, List<Stock> stock, Stock hero,
                                 WindowedTrend.Window window, float scale) {
        final float rowH = ROW_UNIT * scale;
        final int rows = Math.max(0, (int) (r.h / rowH));
        if (rows == 0) {
            return;
        }
        final long peak = RsArt.peak(stock);
        int drawn = 0;
        for (final Stock item : stock) {
            if (drawn >= rows) {
                break;
            }
            if (hero != null && item.itemId().equals(hero.itemId())) {
                continue;
            }
            final Rect row = new Rect(r.x, r.y + drawn * rowH, r.w, rowH - 1.0f * scale);
            if (row.bottom() > r.bottom()) {
                break;
            }
            drawFaceRow(s, row, item, window, scale, peak);
            drawn++;
        }
    }

    private static void drawFaceRow(PanelSurface s, Rect r, Stock item, WindowedTrend.Window window,
                                    float scale, long peak) {
        final int ink = RsArt.trendColor(item.delta(window), item.amount());
        if (peak > 0L) {
            final float w = Math.max(1.0f, r.w * (float) (item.amount() / (double) peak));
            Shapes.rect(s, r.x, r.bottom() - 1.0f * scale, w, 1.0f * scale,
                    Tokens.alpha(Tokens.LINE_HI, 0.8f));
        }
        final float icon = Math.min(r.h - 2.0f * scale, 11.0f * scale);
        s.itemIcon(item.stack(), r.x, r.centerY() - icon * 0.5f, icon);

        final String amount = Text.compact(item.amount());
        final float amountW = s.textWidth(amount, Text.MICRO * scale);
        final float nameX = r.x + icon + 3.0f * scale;
        s.textV(Text.truncate(s, item.name(), Math.max(10.0f, r.right() - amountW - 12.0f * scale
                        - nameX), Text.MICRO * scale), nameX, r.centerY(),
                Tokens.INK_2, Text.MICRO * scale);
        RsArt.arrow(s, r.right() - amountW - 6.0f * scale, r.centerY(), 2.2f * scale,
                item.delta(window), ink);
        s.textRightV(amount, r.right(), r.centerY(), Tokens.INK, Text.MICRO * scale);
    }

    private static void paintNoData(PanelSurface s, Rect area, String title, String detail) {
        final float cx = area.centerX();
        final float cy = area.centerY();
        RsArt.diskGlyph(s, cx - 7.0f, cy - Text.lineHeight(Text.SUB) - 20.0f, 14.0f,
                Tokens.alpha(RsArt.RS, 0.45f));
        s.textCenter(title, cx, cy - Text.lineHeight(Text.SUB), Tokens.INK_3, Text.SUB);
        s.textCenter(detail, cx, cy + 4.0f, Tokens.INK_3, Text.MICRO);
    }

    private static float faceScale(Rect area) {
        final float shortSide = Math.min(area.w, area.h);
        return Math.max(0.6f, Math.min(2.4f, (float) Math.sqrt(shortSide / 92.0f)));
    }
}
