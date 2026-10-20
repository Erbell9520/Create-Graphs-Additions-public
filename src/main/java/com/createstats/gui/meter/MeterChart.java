package com.createstats.gui.meter;

import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class MeterChart {
    private MeterChart() {
    }

    private static final int LABEL_H = 9;

    public static PduRect plotBounds(PduRect bounds) {
        return new PduRect(bounds.x(), bounds.y(), bounds.w(), Math.max(1, bounds.h() - LABEL_H));
    }

    public static void draw(GuiGraphics g, Font font, PduRect bounds, MeterHistory h, int yMax,
                            int scrub, int cadenceTicks) {
        final PduRect plot = plotBounds(bounds);
        g.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, plot.x(), plot.y(), plot.w(), plot.h(), PduTheme.STROKE);

        final int n = h.count();
        if (n == 0) {
            final String msg = "Gathering history";
            g.drawString(font, msg, plot.centerX() - font.width(msg) / 2, plot.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        final int max = Math.max(1, yMax);
        final int top = plot.y() + 1;
        final int bottom = plot.bottom() - 1;

        for (int q = 1; q <= 3; q++) {
            final int gy = top + (bottom - top) * q / 4;
            g.fill(plot.x() + 1, gy, plot.right() - 1, gy + 1, PduTheme.withAlpha(PduTheme.STROKE, 0x66));
        }

        final int span = bottom - top;
        for (int i = 0; i < n; i++) {
            final int x = sampleX(plot, i, n);
            final int v = Math.abs(h.at(i));
            if (v == 0) {
                g.fill(x, bottom, x + 1, bottom + 1, PduTheme.withAlpha(PduTheme.TEXT_FAINT, 0xAA));
                continue;
            }
            final int reach = Math.round(span * Mth.clamp(v / (float) max, 0.0f, 1.0f));
            g.fill(x, bottom - reach, x + 1, bottom, PduTheme.withAlpha(PduTheme.OK, 0x99));
        }

        g.drawString(font, compact(max) + " FE/t", plot.x() + 3, plot.y() + 2, PduTheme.TEXT_FAINT, false);

        if (scrub >= 0 && scrub < n) {
            final int x = sampleX(plot, scrub, n);
            g.fill(x, plot.y() + 1, x + 1, plot.bottom() - 1, PduTheme.withAlpha(PduTheme.TEXT, 0x99));
        }

        final String oldest = "-" + duration((n - 1) * cadenceTicks);
        g.drawString(font, oldest, plot.x(), plot.bottom() + 1, PduTheme.TEXT_FAINT, false);
        g.drawString(font, "now", plot.right() - font.width("now"), plot.bottom() + 1,
                PduTheme.TEXT_FAINT, false);
    }

    public static int sampleAt(PduRect bounds, MeterHistory h, double mouseX, double mouseY) {
        final PduRect plot = plotBounds(bounds);
        if (h.count() == 0 || !plot.contains(mouseX, mouseY)) {
            return -1;
        }
        final int n = h.count();
        final int i = Math.round((float) ((mouseX - plot.x()) / Math.max(1, plot.w() - 1)) * (n - 1));
        return Mth.clamp(i, 0, n - 1);
    }

    private static int sampleX(PduRect plot, int i, int n) {
        return plot.x() + 1 + (n <= 1 ? 0
                : Math.round((plot.w() - 3) * (i / (float) (n - 1))));
    }

    public static String duration(int ticks) {
        final int seconds = ticks / 20;
        if (seconds < 60) {
            return seconds + "s";
        }
        final int minutes = seconds / 60;
        final int rest = seconds % 60;
        return rest == 0 ? minutes + "m" : minutes + "m" + rest + "s";
    }

    public static String compact(long v) {
        final long m = Math.abs(v);
        if (m >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fG", v / 1_000_000_000.0);
        }
        if (m >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        }
        if (m >= 10_000L) {
            return String.format(Locale.ROOT, "%.1fk", v / 1_000.0);
        }
        return String.format(Locale.ROOT, "%,d", v);
    }
}
