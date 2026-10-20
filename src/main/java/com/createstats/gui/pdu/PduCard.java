package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class PduCard {
    private PduCard() {
    }

    public static final int HEADING_H = 11;

    public static PduRect draw(GuiGraphics g, Font font, PduRect bounds, String heading) {
        PduTheme.panel(g, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        PduRect content = bounds.inset(PduTheme.PAD);
        if (heading != null) {
            drawHeading(g, font, heading, content.x(), content.y());
            content = new PduRect(content.x(), content.y() + HEADING_H,
                    content.w(), Math.max(0, content.h() - HEADING_H));
        }
        return content;
    }

    // letterspaced by hand, vanilla font has none
    public static void drawHeading(GuiGraphics g, Font font, String text, int x, int y) {
        final String up = text.toUpperCase(Locale.ROOT);
        int cx = x;
        for (int i = 0; i < up.length(); i++) {
            final String ch = up.substring(i, i + 1);
            g.drawString(font, ch, cx, y, PduTheme.TEXT_DIM, false);
            cx += font.width(ch) + 1;
        }
    }
}
