package com.createstats.gui.pdu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class PduGauge {
    private PduGauge() {
    }

    public static void drawHorizontal(GuiGraphics g, int x, int y, int w, int h, float fraction,
                                      float notch, int fillColour) {
        g.fill(x, y, x + w, y + h, PduTheme.BG_DEEP);
        PduTheme.outline(g, x, y, w, h, PduTheme.STROKE);
        final int fillW = Math.round((w - 2) * Mth.clamp(fraction, 0.0f, 1.0f));
        g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, fillColour);

        if (notch >= 0.0f) {
            final int nx = x + 1 + Math.round((w - 2) * Mth.clamp(notch, 0.0f, 1.0f));

            g.fill(nx, y - 1, nx + 1, y + h + 1, PduTheme.ACCENT);
            g.fill(nx - 2, y - 4, nx + 3, y - 3, PduTheme.ACCENT);
            g.fill(nx - 1, y - 3, nx + 2, y - 2, PduTheme.ACCENT);
            g.fill(nx, y - 2, nx + 1, y - 1, PduTheme.ACCENT);
        }
    }
}
