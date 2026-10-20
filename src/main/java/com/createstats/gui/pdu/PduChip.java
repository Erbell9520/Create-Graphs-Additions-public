package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class PduChip {
    private PduChip() {
    }

    public static final int HEIGHT = 11;

    public static int width(Font font, String label) {
        return font.width(label) + 10;
    }

    public static int draw(GuiGraphics g, Font font, int x, int y, String label, int tint,
                           boolean hovered, boolean focused) {
        final int w = width(font, label);
        g.fill(x, y, x + w, y + HEIGHT, hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, x, y, w, HEIGHT, PduTheme.withAlpha(tint, 0x88));
        g.drawString(font, label, x + 5, y + 2, tint, false);
        if (focused) {
            PduTheme.focusRing(g, x, y, w, HEIGHT);
        }
        return w;
    }
}
