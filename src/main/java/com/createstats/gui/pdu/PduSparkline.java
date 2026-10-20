package com.createstats.gui.pdu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class PduSparkline {
    private PduSparkline() {
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, PduRing ring, int colour) {
        g.fill(x, y + h - 1, x + w, y + h, PduTheme.withAlpha(PduTheme.STROKE, 0x80));
        final int n = ring.count();
        if (n < 2) {
            return;
        }
        final float max = Math.max(1.0f, ring.max());
        int prevX = -1;
        int prevY = -1;
        for (int i = 0; i < n; i++) {
            final int px = x + Math.round((w - 1) * (i / (float) (n - 1)));
            final int py = y + h - 1 - Math.round((h - 2) * Mth.clamp(ring.at(i) / max, 0.0f, 1.0f));
            if (prevX >= 0) {
                g.fill(px - 1, py, px, y + h - 1, PduTheme.withAlpha(colour, 0x22));
                line(g, prevX, prevY, px, py, colour);
            }
            prevX = px;
            prevY = py;
        }

        g.fill(prevX - 1, prevY - 1, prevX + 1, prevY + 1, colour);
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
}
