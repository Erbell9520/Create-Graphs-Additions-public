package com.createstats.gui.pdu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class PduStatCard {
    private PduStatCard() {
    }

    public static void draw(GuiGraphics g, Font font, PduRect bounds, String heading, String value,
                            String unit, String subtitle, PduRing spark, int valueColour) {
        final PduRect c = PduCard.draw(g, font, bounds, heading);

        final Component valueComponent = Component.literal(value).withStyle(ChatFormatting.BOLD);
        g.drawString(font, valueComponent, c.x(), c.y(), valueColour, false);
        if (unit != null) {
            g.drawString(font, unit, c.x() + font.width(valueComponent) + 3, c.y(), PduTheme.TEXT_DIM, false);
        }

        final int subtitleY = c.bottom() - 8;
        if (spark != null && subtitleY - (c.y() + 11) >= 8) {
            PduSparkline.draw(g, c.x(), c.y() + 11, c.w(), subtitleY - (c.y() + 11) - 2, spark, valueColour);
        }
        if (subtitle != null) {
            g.drawString(font, trim(font, subtitle, c.w()), c.x(), subtitleY, PduTheme.TEXT_DIM, false);
        }
    }

    private static String trim(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        final int ell = font.width("...");
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end)) + ell > maxW) {
            end--;
        }
        return s.substring(0, end) + "...";
    }
}
