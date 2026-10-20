package com.createstats.client.statspower;

import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerLegend {
    private PowerLegend() {
    }

    private enum Mark {
        BREAKER_CLOSED, BREAKER_OPEN, BREAKER_TRIPPED,
        LOAD_RAMP, CONTINUOUS_TICK, SHED_HATCH,
        CURRENT, DASH_BUDGET, DASH_WIRING, SHED_LINE,
        CHARGE, DISCHARGE, SOC_LINE, FLOOR_LINE,
        BORE, CHEVRON, ODOMETER, HISTOGRAM
    }

    private record Row(Mark mark, String text) {
    }

    public static void draw(GuiGraphics g, Font font, PduRect area, StatsPowerPage.Sub sub,
                            float phase) {
        g.fill(area.x(), area.y(), area.right(), area.bottom(), PowerArt.alpha(0x000000, 0xBB));

        final Row[] rows = rowsFor(sub);
        final int cols = area.w() >= 430 ? 2 : 1;
        final int perCol = (rows.length + cols - 1) / cols;
        final int rowH = 13;
        final int cardH = Math.min(area.h() - 8, 30 + perCol * rowH);
        final int cardW = Math.min(area.w() - 8, cols == 2 ? 600 : 300);
        final PduRect card = new PduRect(area.centerX() - cardW / 2,
                area.y() + Math.max(4, (area.h() - cardH) / 2), cardW, cardH);
        final PduRect inner = PowerArt.card(g, card);

        g.fill(inner.x(), inner.y() + 1, inner.x() + 2, inner.y() + 8, PduTheme.ACCENT);
        g.drawString(font, "HOW TO READ THIS PAGE", inner.x() + 6, inner.y(), PduTheme.TEXT, false);
        final String hint = "press ? again to close";
        if (inner.w() > 200) {
            g.drawString(font, hint, inner.right() - font.width(hint), inner.y(),
                    PduTheme.TEXT_FAINT, false);
        }
        g.fill(inner.x(), inner.y() + 11, inner.right(), inner.y() + 12,
                PowerArt.alpha(PduTheme.STROKE, 0x99));

        final int colW = (inner.w() - (cols - 1) * 10) / cols;
        for (int i = 0; i < rows.length; i++) {
            final int col = i / perCol;
            final int idx = i % perCol;
            final int rx = inner.x() + col * (colW + 10);
            final int ry = inner.y() + 16 + idx * rowH;
            if (ry + 9 > inner.bottom()) {
                continue;
            }
            swatch(g, rows[i].mark(), rx, ry, phase);
            g.drawString(font, fit(font, rows[i].text(), colW - 24), rx + 22, ry + 1,
                    PduTheme.TEXT_DIM, false);
        }
    }

    private static Row[] rowsFor(StatsPowerPage.Sub sub) {
        return switch (sub) {
            case PDU -> new Row[]{
                    new Row(Mark.BREAKER_CLOSED, "Breaker closed and carrying"),
                    new Row(Mark.BREAKER_OPEN, "Breaker you opened — nothing offered"),
                    new Row(Mark.BREAKER_TRIPPED, "Breaker thrown by sustained overload"),
                    new Row(Mark.CURRENT, "Travelling dots: current, faster under load"),
                    new Row(Mark.LOAD_RAMP, "Bar fill: load against its own rating"),
                    new Row(Mark.CONTINUOUS_TICK, "Tick at 80%: the continuous-load line"),
                    new Row(Mark.SHED_HATCH, "Hatching: demand asked for and refused"),
                    new Row(Mark.DASH_BUDGET, "Brass dashes: the panel's spending budget"),
                    new Row(Mark.DASH_WIRING, "Red dashes: what the wiring can carry"),
                    new Row(Mark.SHED_LINE, "Shed line: where the tiers stop being served")};
            case BANK -> new Row[]{
                    new Row(Mark.CHARGE, "Above the rule: charging the reserve"),
                    new Row(Mark.DISCHARGE, "Below the rule: the reserve covering load"),
                    new Row(Mark.SOC_LINE, "Brass trace: charge, fixed 0-100 axis"),
                    new Row(Mark.FLOOR_LINE, "Column dashes: each output's floor"),
                    new Row(Mark.LOAD_RAMP, "Bar fill: flow against its rating"),
                    new Row(Mark.SHED_HATCH, "Hatching: asked for and not delivered"),
                    new Row(Mark.CURRENT, "Travelling dots: power arriving at a terminal"),
                    new Row(Mark.BREAKER_CLOSED, "Served — the circuit has all it asked for"),
                    new Row(Mark.BREAKER_OPEN, "Held off on purpose: floor, surplus or recharge"),
                    new Row(Mark.BREAKER_TRIPPED, "Shed — higher tiers took the reserve")};
            case FLOW -> new Row[]{
                    new Row(Mark.BORE, "The bore carries the real fluid, moving with it"),
                    new Row(Mark.CHEVRON, "Chevrons point the way the medium is going"),
                    new Row(Mark.LOAD_RAMP, "Range bar: rate against the window's own peak"),
                    new Row(Mark.DASH_WIRING, "Red tick: the armed redstone setpoint"),
                    new Row(Mark.ODOMETER, "Totaliser: everything passed since placement"),
                    new Row(Mark.HISTOGRAM, "Distribution: one bar steady, two bars bursty"),
                    new Row(Mark.DASH_BUDGET, "Brass dashes: the window mean")};
        };
    }

    private static void swatch(GuiGraphics g, Mark mark, int x, int y, float phase) {
        final int w = 18;
        final int h = 9;
        switch (mark) {
            case BREAKER_CLOSED -> PowerArt.breaker(g, x + 8, y + 4, true, false, true, PduTheme.OK);
            case BREAKER_OPEN -> PowerArt.breaker(g, x + 8, y + 4, false, false, false,
                    PduTheme.TEXT_FAINT);
            case BREAKER_TRIPPED -> PowerArt.breaker(g, x + 8, y + 4, false, true, false,
                    PduTheme.CRIT);
            case LOAD_RAMP -> {
                for (int i = 0; i < w; i++) {
                    g.fill(x + i, y + 1, x + i + 1, y + h - 1,
                            PowerArt.loadTint(i / (float) (w - 1) * 1.1f));
                }
                PduTheme.outline(g, x, y, w, h, PowerArt.alpha(PduTheme.STROKE, 0xCC));
            }
            case CONTINUOUS_TICK -> {
                PowerArt.bar(g, x, y + 1, w, h - 2, 0.65f, PduTheme.OK);
                PowerArt.dashedV(g, x + Math.round(w * PowerArt.CONTINUOUS_LOAD), y + 1, y + h - 1,
                        PowerArt.alpha(PduTheme.TEXT_FAINT, 0xEE), 2, 2);
            }
            case SHED_HATCH -> {
                g.fill(x, y + 1, x + w, y + h - 1, PowerArt.alpha(PduTheme.WARN, 0x2E));
                PowerArt.hatch(g, x, y + 1, w, h - 2, PowerArt.alpha(PduTheme.WARN, 0x99), phase);
                PduTheme.outline(g, x, y, w, h, PowerArt.alpha(PduTheme.STROKE, 0xCC));
            }
            case CURRENT -> {
                g.fill(x, y + 4, x + w, y + 5, PowerArt.alpha(PduTheme.ACCENT, 0xCC));
                PowerArt.currentDots(g, x, y + 4, x + w, y + 4, 0.8f, PduTheme.ACCENT, phase, false);
            }
            case DASH_BUDGET -> PowerArt.dashedH(g, x, x + w, y + 4,
                    PowerArt.alpha(PduTheme.ACCENT, 0xDD), 3, 3);
            case DASH_WIRING -> PowerArt.dashedH(g, x, x + w, y + 4,
                    PowerArt.alpha(PduTheme.CRIT, 0xDD), 3, 3);
            case SHED_LINE -> {
                g.fill(x, y + 1, x + w / 2, y + h - 1, PowerArt.alpha(PduTheme.OK, 0x55));
                g.fill(x + w / 2, y + 1, x + w, y + h - 1, PowerArt.alpha(PduTheme.CRIT, 0x33));
                PowerArt.dashedV(g, x + w / 2, y, y + h, PduTheme.CRIT, 2, 2);
            }
            case CHARGE -> {
                g.fillGradient(x, y + 1, x + w, y + 5, PowerArt.alpha(PduTheme.OK, 0xC8),
                        PowerArt.alpha(PduTheme.OK, 0x40));
                g.fill(x, y + 5, x + w, y + 6, PowerArt.alpha(PduTheme.TEXT_FAINT, 0xBB));
            }
            case DISCHARGE -> {
                g.fill(x, y + 3, x + w, y + 4, PowerArt.alpha(PduTheme.TEXT_FAINT, 0xBB));
                g.fillGradient(x, y + 4, x + w, y + h, PowerArt.alpha(PduTheme.WARN, 0x40),
                        PowerArt.alpha(PduTheme.WARN, 0xC8));
            }
            case SOC_LINE -> {
                g.fill(x, y + 4, x + w, y + 5, PowerArt.alpha(PduTheme.STROKE, 0xEE));
                PowerArt.line(g, x, y + 7, x + w, y + 2, PowerArt.alpha(PduTheme.ACCENT, 0xE0));
            }
            case FLOOR_LINE -> {
                g.fill(x + 5, y, x + 13, y + h, PduTheme.BG_DEEP);
                g.fillGradient(x + 6, y + 4, x + 12, y + h, PowerArt.scale(PduTheme.OK, 1.2f),
                        PowerArt.scale(PduTheme.OK, 0.6f));
                PowerArt.dashedH(g, x + 3, x + 15, y + 3, PduTheme.WARN, 3, 2);
            }
            case BORE -> {
                g.fill(x, y + 1, x + w, y + h - 1, PowerArt.alpha(0xFF2E8FE0, 0xE0));
                for (int i = 0; i < 3; i++) {
                    final int sx = x + Math.floorMod(Math.round(i * 7 + phase * 0.6f), w);
                    g.fill(sx, y + 3, Math.min(x + w, sx + 4), y + 4, PowerArt.alpha(0xFFFFFF, 0x66));
                }
                PduTheme.outline(g, x, y, w, h, PduTheme.STROKE);
            }
            case CHEVRON -> {
                for (int i = 0; i < 3; i++) {
                    final int ox = x + 2 + i * 6;
                    PowerArt.line(g, ox, y + 1, ox + 3, y + 4, PowerArt.alpha(0xFFFFFF, 0xAA));
                    PowerArt.line(g, ox, y + 7, ox + 3, y + 4, PowerArt.alpha(0xFFFFFF, 0xAA));
                }
            }
            case ODOMETER -> {
                for (int i = 0; i < 3; i++) {
                    final int cx = x + i * 6;
                    g.fill(cx, y, cx + 5, y + h, PduTheme.BG_DEEP);
                    PduTheme.outline(g, cx, y, 5, h, PowerArt.alpha(PduTheme.STROKE, 0xDD));
                    g.fill(cx + 2, y + 3, cx + 3, y + 6, PduTheme.ACCENT);
                }
            }
            case HISTOGRAM -> {
                final int[] bars = {2, 5, 8, 4, 2};
                for (int i = 0; i < bars.length; i++) {
                    final int bx = x + i * 4;
                    g.fill(bx, y + h - bars[i], bx + 3, y + h, PowerArt.alpha(PduTheme.INFO, 0xDD));
                }
            }
        }
    }

    private static String fit(Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 3 && font.width(cut + "…") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
