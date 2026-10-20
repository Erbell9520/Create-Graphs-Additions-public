package com.createstats.gui.battery;

import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;

// two strips, percent and FE/t don't share an axis
public final class BatteryChart {
    private BatteryChart() {
    }

    private static final int LABEL_H = 9;

    private static final float CHARGE_SHARE = 0.58f;

    public static PduRect plotBounds(PduRect bounds) {
        return new PduRect(bounds.x(), bounds.y(), bounds.w(), Math.max(1, bounds.h() - LABEL_H));
    }

    private static PduRect chargeBounds(PduRect bounds) {
        final PduRect plot = plotBounds(bounds);
        return new PduRect(plot.x(), plot.y(), plot.w(),
                Math.max(1, Math.round(plot.h() * CHARGE_SHARE)));
    }

    private static PduRect flowBounds(PduRect bounds) {
        final PduRect plot = plotBounds(bounds);
        final PduRect charge = chargeBounds(bounds);
        return new PduRect(plot.x(), charge.bottom() + 1, plot.w(),
                Math.max(1, plot.bottom() - charge.bottom() - 1));
    }

    public static void draw(GuiGraphics g, Font font, PduRect bounds, BatteryHistory h,
                            int yMax, int scrubIndex, int cadenceTicks, int floorPct) {
        final PduRect charge = chargeBounds(bounds);
        final PduRect flow = flowBounds(bounds);

        g.fill(charge.x(), charge.y(), charge.right(), flow.bottom(), PduTheme.BG_DEEP);

        final int n = h.count();
        if (n == 0) {
            final String msg = "Gathering history";
            g.drawString(font, msg, charge.centerX() - font.width(msg) / 2,
                    charge.y() + charge.h() / 2 - 4, PduTheme.TEXT_FAINT, false);
            PduTheme.outline(g, charge.x(), charge.y(), charge.w(),
                    flow.bottom() - charge.y(), PduTheme.STROKE);
            drawTimeAxis(g, font, bounds, 0, cadenceTicks);
            return;
        }

        drawCharge(g, font, charge, h, n, floorPct);
        drawFlow(g, font, flow, h, n, Math.max(1, yMax));

        g.fill(charge.x(), charge.bottom(), charge.right(), charge.bottom() + 1, PduTheme.STROKE);
        PduTheme.outline(g, charge.x(), charge.y(), charge.w(), flow.bottom() - charge.y(),
                PduTheme.STROKE);

        if (scrubIndex >= 0 && scrubIndex < n) {
            final int x = sampleX(charge, scrubIndex, n);
            g.fill(x, charge.y(), x + 1, flow.bottom(), PduTheme.TEXT);
        }
        drawTimeAxis(g, font, bounds, n, cadenceTicks);
    }

    private static void drawCharge(GuiGraphics g, Font font, PduRect a, BatteryHistory h,
                                   int n, int floorPct) {
        for (int q = 1; q <= 3; q++) {
            final int gy = a.y() + a.h() * q / 4;
            g.fill(a.x() + 1, gy, a.right() - 1, gy + 1,
                    PduTheme.withAlpha(PduTheme.STROKE, 0x66));
        }
        if (floorPct > 0 && floorPct < 100) {
            final int fy = a.bottom() - 1 - Math.round((a.h() - 2) * floorPct / 100.0f);
            g.fill(a.x() + 1, fy, a.right() - 1, fy + 1, PduTheme.withAlpha(PduTheme.WARN, 0x99));
        }
        for (int i = 0; i < n; i++) {
            final int x = sampleX(a, i, n);
            final int soc = h.socAt(i);
            final int y = valueY(a, soc, 100);

            final int colour = soc < 15 ? PduTheme.CRIT : soc < 30 ? PduTheme.WARN : PduTheme.INFO;
            g.fill(x, y, x + 1, a.bottom() - 1, PduTheme.withAlpha(colour, 0x4D));
            g.fill(x, y, x + 1, Math.min(y + 1, a.bottom() - 1), colour);
        }
        g.drawString(font, "CHARGE", a.x() + 2, a.y() + 2, PduTheme.TEXT_FAINT, false);
        final String now = h.latestSocPct() + "%";
        g.drawString(font, now, a.right() - 2 - font.width(now), a.y() + 2,
                PduTheme.TEXT_DIM, false);
    }

    private static void drawFlow(GuiGraphics g, Font font, PduRect a, BatteryHistory h,
                                 int n, int max) {
        for (int i = 0; i < n; i++) {
            final int x = sampleX(a, i, n);
            final int y = valueY(a, h.inAt(i), max);
            g.fill(x, y, x + 1, a.bottom() - 1, PduTheme.withAlpha(PduTheme.OK, 0x40));
        }
        int px = -1;
        int py = -1;
        for (int i = 0; i < n; i++) {
            final int x = sampleX(a, i, n);
            final int y = valueY(a, h.outAt(i), max);
            if (px >= 0) {
                line(g, px, py, x, y, PduTheme.WARN);
            }
            px = x;
            py = y;
        }
        final String label = "IN/OUT  " + compact(max) + " FE/t";
        g.drawString(font, label, a.x() + 2, a.y() + 1, PduTheme.TEXT_FAINT, false);
    }

    private static void drawTimeAxis(GuiGraphics g, Font font, PduRect bounds, int n,
                                     int cadenceTicks) {
        final PduRect plot = plotBounds(bounds);
        final int y = plot.bottom() + 1;
        final String span = "-" + duration(Math.max(0, n - 1) * cadenceTicks);
        g.drawString(font, span, plot.x(), y, PduTheme.TEXT_FAINT, false);
        g.drawString(font, "now", plot.right() - font.width("now"), y, PduTheme.TEXT_FAINT, false);
    }

    public static int sampleAt(PduRect bounds, BatteryHistory h, double mouseX, double mouseY) {
        final PduRect plot = plotBounds(bounds);
        if (h.count() == 0 || mouseX < plot.x() || mouseX >= plot.right()
                || mouseY < plot.y() || mouseY >= plot.bottom()) {
            return -1;
        }
        final int n = h.count();
        final int i = Math.round((float) ((mouseX - plot.x()) / Math.max(1, plot.w() - 1)) * (n - 1));
        return Mth.clamp(i, 0, n - 1);
    }

    private static int sampleX(PduRect a, int i, int n) {
        return a.x() + (n <= 1 ? 0 : Math.round((a.w() - 1) * (i / (float) (n - 1))));
    }

    private static int valueY(PduRect a, int v, int max) {
        return a.bottom() - 1 - Math.round((a.h() - 2) * Mth.clamp(v / (float) max, 0.0f, 1.0f));
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

    public static String duration(int ticks) {
        final int secs = Math.max(0, ticks) / 20;
        return secs >= 60 ? (secs / 60) + "m" : secs + "s";
    }

    public static String compact(long v) {
        final long abs = Math.abs(v);
        if (abs >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        }
        if (abs >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", v / 1_000.0);
        }
        if (abs >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1_000.0);
        }
        return String.valueOf(v);
    }
}
