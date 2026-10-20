package com.createstats.flight.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class FccHud {
    private FccHud() {}

    public record Theme(int sky, int ground, int horizonLine, int crosshair,
                        int frame, int text, int textDim, int barOk, int barWarn, int barBad) {
        public static Theme NAVY = new Theme(
            0xFF1B6FB0,
            0xFFB07B2C,
            0xFFEAEAEA,
            0xFFD4AF37,
            0xFF39C8FF,
            0xFFE4F0FA,
            0xFF7AA0C0,
            0xFF55E0FF,
            0xFFF2A53A,
            0xFFFF5555
        );
    }

    public static void drawRpmBars(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   int[] commanded, int[] actual, int rowCount, Theme theme) {
        int rows = Math.max(1, Math.min(rowCount, 5));
        int rowH = Math.max(7, h / rows);
        for (int i = 0; i < rows; i++) {
            int ry = y + i * rowH;
            int cmd = (commanded != null && i < commanded.length) ? commanded[i] : 0;
            int act = (actual    != null && i < actual.length)    ? actual[i]    : 0;

            g.fill(x + 18, ry + 1, x + w, ry + rowH - 1, 0xFF0E2A4D);
            int center = x + 18 + (w - 18) / 2;

            g.fill(center, ry + 1, center + 1, ry + rowH - 1, theme.textDim);

            int cmdW = (int) ((w - 18) / 2.0 * (cmd / 256.0));
            int actW = (int) ((w - 18) / 2.0 * (act / 256.0));
            int cmdColor = theme.barOk;
            int diff = Math.abs(cmd - act);
            if (cmd != 0 && diff > Math.max(20, Math.abs(cmd) / 4)) cmdColor = theme.barWarn;
            if (cmd != 0 && act == 0) cmdColor = theme.barBad;

            int cmdX1 = center, cmdX2 = center + cmdW;
            if (cmdW < 0) { cmdX1 = center + cmdW; cmdX2 = center; }
            g.fill(cmdX1, ry + 1, cmdX2, ry + 2, cmdColor);
            g.fill(cmdX1, ry + rowH - 2, cmdX2, ry + rowH - 1, cmdColor);
            g.fill(cmdX1, ry + 1, cmdX1 + 1, ry + rowH - 1, cmdColor);
            g.fill(cmdX2 - 1, ry + 1, cmdX2, ry + rowH - 1, cmdColor);

            int actX1 = center, actX2 = center + actW;
            if (actW < 0) { actX1 = center + actW; actX2 = center; }
            g.fill(actX1, ry + 3, actX2, ry + rowH - 3, cmdColor);

            g.drawString(font, "P" + (i + 1), x, ry + 1, theme.text, false);
        }
    }

    public static void drawCompass(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   double headingDeg, Theme theme) {
        g.fill(x, y, x + w, y + h, 0xFF0E2A4D);
        g.fill(x, y, x + w, y + 1, theme.frame);
        g.fill(x, y + h - 1, x + w, y + h, theme.frame);

        double h360 = ((headingDeg % 360) + 360) % 360;

        double pxPerDeg = w / 90.0;
        int cx = x + w / 2;

        for (int deg = 0; deg < 360; deg += 30) {
            double off = deg - h360;

            while (off > 180) off -= 360;
            while (off < -180) off += 360;
            int tx = (int) (cx + off * pxPerDeg);
            if (tx < x + 2 || tx >= x + w - 2) continue;
            g.fill(tx, y + 2, tx + 1, y + h - 2, theme.textDim);
            String label = switch (deg) {
                case 0 -> "N"; case 90 -> "E"; case 180 -> "S"; case 270 -> "W";
                default -> null;
            };
            if (label != null) {
                g.drawString(font, label, tx - 2, y + 1, theme.text, false);
            }
        }

        g.fill(cx, y + 1, cx + 1, y + h - 1, theme.crosshair);
        g.fill(cx - 2, y + 1, cx + 3, y + 2, theme.crosshair);
    }

    public static void drawTelemetryStrip(GuiGraphics g, Font font, int x, int y,
                                          double altitude, double climbBps,
                                          double headingDeg, double speedBps, Theme theme) {
        int rowH = 11;
        drawKv(g, font, x, y + 0 * rowH, "ALT", String.format(Locale.ROOT, "%.1f m", altitude), theme);
        String climbStr = String.format(Locale.ROOT, "%+.1f", climbBps);
        if (climbBps > 0.05) climbStr = "↑" + climbStr;
        else if (climbBps < -0.05) climbStr = "↓" + climbStr;
        drawKv(g, font, x, y + 1 * rowH, "CLIMB", climbStr + " m/s", theme);
        drawKv(g, font, x, y + 2 * rowH, "HDG", String.format(Locale.ROOT, "%.0f°", ((headingDeg % 360) + 360) % 360), theme);
        drawKv(g, font, x, y + 3 * rowH, "SPEED", String.format(Locale.ROOT, "%.1f m/s", speedBps), theme);
    }

    private static void drawKv(GuiGraphics g, Font font, int x, int y, String k, String v, Theme theme) {
        g.drawString(font, k, x, y, theme.textDim, false);
        g.drawString(font, v, x + 28, y, theme.text, false);
    }

    public static void drawSensorLeds(GuiGraphics g, Font font, int x, int y,
                                      boolean gimbal, boolean alt, boolean vel, boolean nav, Theme theme) {
        String[] labels = {"GMB", "ALT", "VEL", "NAV"};
        boolean[] states = {gimbal, alt, vel, nav};
        int slot = 26;
        for (int i = 0; i < 4; i++) {
            int sx = x + i * slot;
            int color = states[i] ? theme.barOk : theme.textDim;
            g.fill(sx, y + 2, sx + 4, y + 6, color);
            g.drawString(font, labels[i], sx + 6, y + 1, color, false);
        }
    }

    public static String formatPos(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.0f,%.0f,%.0f", x, y, z);
    }

    public static double clamp(double v, double lo, double hi) {
        return Mth.clamp(v, lo, hi);
    }
}
