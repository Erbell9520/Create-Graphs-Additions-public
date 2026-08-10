package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** [1] */
public final class PduCard {

    private PduCard() {
    }

    /** Height a heading claims at the top of a card: the text plus a little air under it. */
    public static final int HEADING_H = 11;

    /**
     * Draws the card and returns the rectangle its content may use, already inset by the padding and,
     * if a heading was given, dropped below it. Callers lay out against the returned rect and never
     * have to know the padding themselves.
     *
     * @param heading uppercase label drawn dim at the top left, or {@code null} for a bare surface
     */
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

    /** [2] */
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
