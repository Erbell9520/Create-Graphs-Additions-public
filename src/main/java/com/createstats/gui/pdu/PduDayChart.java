package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class PduDayChart {
    private PduDayChart() {
    }

    public static final int AXIS_H = 10;

    private static final int WET_H = 3;

    private static final int SUPPLY_FILL = PduTheme.withAlpha(PduTheme.OK, 0x4D);
    private static final int RAIN_FILL = PduTheme.withAlpha(PduTheme.INFO, 0x40);
    private static final int SHORT_FILL = PduTheme.withAlpha(PduTheme.CRIT, 0x66);
    private static final int COLD_VEIL = PduTheme.withAlpha(PduTheme.BG_RAISED, 0xA0);
    private static final int GRID = PduTheme.withAlpha(PduTheme.STROKE, 0x80);

    public static PduRect plotBounds(PduRect bounds) {
        return new PduRect(bounds.x(), bounds.y(), bounds.w(), Math.max(1, bounds.h() - AXIS_H));
    }

    public static int stepAt(PduRect bounds, PduDayOutlook o, double mouseX, double mouseY) {
        final PduRect plot = plotBounds(bounds);
        if (!o.hasCurve() || mouseX < plot.x() || mouseX >= plot.right()
                || mouseY < plot.y() || mouseY >= plot.bottom()) {
            return -1;
        }
        final int i = (int) ((mouseX - plot.x()) * o.steps() / Math.max(1, plot.w()));
        return Mth.clamp(i, 0, o.steps() - 1);
    }

    public static void draw(GuiGraphics g, Font font, PduRect bounds, PduDayOutlook o, int hoverStep) {
        final PduRect plot = plotBounds(bounds);
        g.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, plot.x(), plot.y(), plot.w(), plot.h(), PduTheme.STROKE);
        if (!o.hasCurve()) {
            drawLearning(g, font, plot, o);
            return;
        }
        final int max = o.scaleMax();
        final int n = o.steps();
        for (int q = 1; q <= 2; q++) {
            final int gy = plot.y() + plot.h() * q / 3;
            g.fill(plot.x() + 1, gy, plot.right() - 1, gy + 1, GRID);
        }
        drawClockGrid(g, font, plot, o);

        final int critY = valueY(plot, o.criticalFePerTick(), max);
        for (int i = 0; i < n; i++) {
            final int x0 = stepX(plot, i, n);
            final int x1 = Math.max(x0 + 1, stepX(plot, i + 1, n));
            final int supplyY = valueY(plot, o.rate()[i], max);
            final int clearY = valueY(plot, o.clear()[i], max);

            if (clearY < supplyY) {
                g.fill(x0, clearY, x1, supplyY, RAIN_FILL);
            }
            g.fill(x0, supplyY, x1, plot.bottom() - 1, SUPPLY_FILL);
            g.fill(x0, supplyY, x1, supplyY + 1, PduTheme.OK);

            if (o.criticalFePerTick() > 0 && supplyY > critY) {
                g.fill(x0, critY + 1, x1, supplyY, SHORT_FILL);
            }
            if (o.isCold(i)) {
                g.fill(x0, plot.y() + 1, x1, plot.bottom() - 1, COLD_VEIL);
                for (int x = x0; x < x1; x += 2) {
                    g.fill(x, plot.y() + 1 + WET_H + 1, x + 1, plot.y() + 2 + WET_H + 1, PduTheme.TEXT_FAINT);
                }
            }
            if (o.wetAt(i)) {
                g.fill(x0, plot.y() + 1, x1, plot.y() + 1 + WET_H, PduTheme.withAlpha(PduTheme.INFO, 0xC0));
            }
        }

        if (o.criticalFePerTick() > 0) {
            for (int x = plot.x() + 1; x < plot.right() - 1; x += 4) {
                g.fill(x, critY, Math.min(x + 2, plot.right() - 1), critY + 1, PduTheme.CRIT);
            }
        }

        final int deepest = o.shortfallFe() > 0 ? o.deepestStep() : -1;
        if (deepest >= 0) {
            final int x = stepX(plot, deepest + 1, n) - 1;
            for (int y = plot.y() + 1 + WET_H + 1; y < plot.bottom() - 1; y += 3) {
                g.fill(x, y, x + 1, y + 1, PduTheme.WARN);
            }
            final String label = "lowest";
            final int lx = Math.min(plot.right() - 2 - font.width(label), x + 3);
            g.drawString(font, label, Math.max(plot.x() + 4, lx), plot.bottom() - 10, PduTheme.WARN, false);
        }

        g.fill(plot.x() + 1, plot.y() + 1, plot.x() + 2, plot.bottom() - 1, PduTheme.withAlpha(PduTheme.TEXT, 0xA0));

        final String maxLabel = PduDayOutlook.compact(max) + " FE/t";

        g.drawString(font, maxLabel, plot.x() + 4, plot.y() + WET_H + 3, PduTheme.TEXT_FAINT, false);

        if (hoverStep >= 0 && hoverStep < n) {
            final int x0 = stepX(plot, hoverStep, n);
            final int x1 = Math.max(x0 + 1, stepX(plot, hoverStep + 1, n));
            g.fill((x0 + x1) / 2, plot.y() + 1, (x0 + x1) / 2 + 1, plot.bottom() - 1, PduTheme.TEXT);
        }
    }

    private static void drawClockGrid(GuiGraphics g, Font font, PduRect plot, PduDayOutlook o) {
        final int span = Math.max(1, o.spanTicks());
        final int every = plot.w() >= 360 ? 3 : 6;
        final int labelY = plot.bottom() + 2;
        int lastRight = Integer.MIN_VALUE;
        for (int t = o.ticksToNextMark(every); t < span; t += every * 1_000) {
            final int x = plot.x() + (int) ((long) (plot.w() - 1) * t / span);
            if (t > 0) {
                g.fill(x, plot.y() + 1, x + 1, plot.bottom() - 1, GRID);
            }
            final String label = PduDayOutlook.clock(o.startDayTime() + t);
            final int lx = Mth.clamp(x - font.width(label) / 2, plot.x(), plot.right() - font.width(label));
            if (lx > lastRight + 4) {
                g.drawString(font, label, lx, labelY, PduTheme.TEXT_FAINT, false);
                lastRight = lx + font.width(label);
            }
        }
    }

    private static void drawLearning(GuiGraphics g, Font font, PduRect plot, PduDayOutlook o) {
        final String head = o.summary(plot.w() < 220);
        g.drawString(font, head, plot.centerX() - font.width(head) / 2, plot.centerY() - 10,
                PduTheme.TEXT_DIM, false);
        final String sub = plot.w() < 260 ? "Half the day before it forecasts"
                : "It forecasts once half the day has been watched";
        g.drawString(font, sub, plot.centerX() - font.width(sub) / 2, plot.centerY() + 2,
                PduTheme.TEXT_FAINT, false);

        final int cell = Math.max(3, Math.min(8, (plot.w() - 20) / PduDayOutlook.HOURS));
        final int rowW = cell * PduDayOutlook.HOURS;
        final int x0 = plot.centerX() - rowW / 2;
        final int y = plot.centerY() + 16;
        for (int h = 0; h < PduDayOutlook.HOURS; h++) {
            g.fill(x0 + h * cell, y, x0 + h * cell + cell - 1, y + 4,
                    h < o.warmBuckets() ? PduTheme.OK : PduTheme.BG_RAISED);
        }
    }

    private static int stepX(PduRect plot, int i, int n) {
        return plot.x() + 1 + (int) ((long) (plot.w() - 2) * i / Math.max(1, n));
    }

    private static int valueY(PduRect plot, int v, int max) {
        return plot.bottom() - 1 - Math.round((plot.h() - 2) * Mth.clamp(v / (float) max, 0.0f, 1.0f));
    }
}
