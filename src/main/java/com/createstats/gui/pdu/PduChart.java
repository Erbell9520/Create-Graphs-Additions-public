package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class PduChart {
    private PduChart() {
    }

    public static final int RAIL_H = 6;
    private static final int LABEL_H = 10;

    public static PduRect plotBounds(PduRect bounds) {
        return new PduRect(bounds.x(), bounds.y(), bounds.w(), Math.max(1, bounds.h() - RAIL_H - LABEL_H));
    }

    public static void draw(GuiGraphics g, Font font, PduRect bounds, PduHistory h, int scrubIndex, int yMax) {
        final PduRect plot = plotBounds(bounds);
        g.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, plot.x(), plot.y(), plot.w(), plot.h(), PduTheme.STROKE);

        final int n = h.count();
        if (n == 0) {
            final String msg = "Gathering history";
            g.drawString(font, msg, plot.centerX() - font.width(msg) / 2, plot.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            drawRail(g, plot, h, scrubIndex);
            return;
        }

        // caller eases the scale, fresh peaks grow instead of snapping

        final int max = Math.max(1, yMax);
        for (int q = 1; q <= 2; q++) {
            final int gy = plot.y() + plot.h() * q / 3;
            g.fill(plot.x() + 1, gy, plot.right() - 1, gy + 1, PduTheme.withAlpha(PduTheme.STROKE, 0x80));
        }

        for (int i = 0; i < n; i++) {
            final int x = sampleX(plot, i, n);
            final int sy = valueY(plot, h.supplyAt(i), max);
            g.fill(x, sy, x + 1, plot.bottom() - 1, PduTheme.withAlpha(PduTheme.OK, 0x4D));
        }

        int px = -1;
        int py = -1;
        for (int i = 0; i < n; i++) {
            final int x = sampleX(plot, i, n);
            final int dy = valueY(plot, h.demandAt(i), max);
            if (px >= 0) {
                line(g, px, py, x, dy, PduTheme.WARN);
            }
            px = x;
            py = dy;
        }

        final String maxLabel = compact(max) + " FE/t";
        g.drawString(font, maxLabel, plot.x() + 2, plot.y() + 2, PduTheme.TEXT_FAINT, false);

        drawRail(g, plot, h, scrubIndex);
    }

    private static void drawRail(GuiGraphics g, PduRect plot, PduHistory h, int scrubIndex) {
        final int railY = plot.bottom() + 2;
        g.fill(plot.x(), railY, plot.right(), railY + RAIL_H, PduTheme.BG_PANEL);
        PduTheme.outline(g, plot.x(), railY, plot.w(), RAIL_H, PduTheme.STROKE);
        final int n = h.count();
        for (int i = 0; i < n; i++) {
            final int s = h.shedAt(i);
            if (s > 0) {
                final int x = sampleX(plot, i, n);
                final int markH = Math.min(RAIL_H - 1, 1 + s);
                g.fill(x, railY + RAIL_H - 1 - markH, x + 1, railY + RAIL_H - 1, PduTheme.CRIT);
            }
        }
        if (scrubIndex >= 0 && scrubIndex < n) {
            final int x = sampleX(plot, scrubIndex, n);
            g.fill(x, plot.y(), x + 1, railY + RAIL_H, PduTheme.TEXT);
        }
    }

    public static int sampleAt(PduRect bounds, PduHistory h, double mouseX, double mouseY) {
        final PduRect plot = plotBounds(bounds);
        if (h.count() == 0 || mouseX < plot.x() || mouseX >= plot.right()
                || mouseY < plot.y() || mouseY >= plot.bottom()) {
            return -1;
        }
        final int n = h.count();
        final int i = Math.round((float) ((mouseX - plot.x()) / Math.max(1, plot.w() - 1)) * (n - 1));
        return Mth.clamp(i, 0, n - 1);
    }

    private static int sampleX(PduRect plot, int i, int n) {
        return plot.x() + (n <= 1 ? 0 : Math.round((plot.w() - 1) * (i / (float) (n - 1))));
    }

    public static int valueY(PduRect plot, int v, int max) {
        return plot.bottom() - 1 - Math.round((plot.h() - 2) * Mth.clamp(v / (float) max, 0.0f, 1.0f));
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, colour);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static String compact(int v) {
        if (v >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        }
        if (v >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1_000.0);
        }
        return String.valueOf(v);
    }
}
