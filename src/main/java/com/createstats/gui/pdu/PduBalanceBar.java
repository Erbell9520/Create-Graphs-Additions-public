package com.createstats.gui.pdu;

import com.createstats.power.FePriority;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class PduBalanceBar {
    private PduBalanceBar() {
    }

    static String emptyStateText(int deviceCount) {
        if (deviceCount <= 0) {
            return "No devices on the branch";
        }
        return deviceCount == 1 ? "1 device connected, drawing nothing"
                : deviceCount + " devices connected, drawing nothing";
    }

    public static void draw(GuiGraphics g, Font font, PduRect bounds, int[] tierDraw,
                            int[] tierServedDraw, int deviceCount) {
        PduTheme.panel(g, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        final int pad = 6;
        final int left = bounds.x() + pad;
        final int right = bounds.right() - pad;

        int total = 0;
        int served = 0;
        for (int i = 0; i < tierDraw.length; i++) {
            total += tierDraw[i];
            served += tierServedDraw[i];
        }

        PduCard.drawHeading(g, font, "Load balance", left, bounds.y() + pad);
        final String numbers = String.format(Locale.ROOT, "%,d of %,d FE/t served", served, total);
        g.drawString(font, numbers, right - font.width(numbers), bounds.y() + pad,
                served < total ? PduTheme.WARN : PduTheme.TEXT_DIM, false);

        final int barY = bounds.y() + pad + 11;
        final int barH = bounds.bottom() - pad - 8 - barY;
        final int barW = right - left;
        if (total <= 0) {
            g.drawString(font, emptyStateText(deviceCount), left, barY + Math.max(0, (barH - 8) / 2),
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        g.fill(left, barY, right, barY + barH, PduTheme.BG_DEEP);
        int x = left;
        for (int i = 0; i < tierDraw.length; i++) {
            if (tierDraw[i] <= 0) {
                continue;
            }
            final int bandW = Math.max(1, Math.round(barW * (tierDraw[i] / (float) total)));
            final int servedW = Math.round(bandW * (tierServedDraw[i] / (float) tierDraw[i]));
            final int tierColour = PduGlyphs.tierColour(FePriority.values()[i]);
            g.fill(x, barY, x + servedW, barY + barH, tierColour);
            if (servedW < bandW) {
                g.fill(x + servedW, barY, x + bandW, barY + barH, PduTheme.withAlpha(tierColour, 0x3A));
                hatch(g, x + servedW, barY, x + bandW, barY + barH);
            }
            g.fill(x + bandW - 1, barY, x + bandW, barY + barH, PduTheme.BG_DEEP);
            final String initial = FePriority.values()[i].getDisplayName().substring(0, 1);
            if (bandW >= 8) {
                g.drawString(font, initial, x + (bandW - font.width(initial)) / 2, bounds.bottom() - pad - 6,
                        tierColour, false);
            }
            x += bandW;
        }

        final int ruleX = left + Math.round(barW * (served / (float) total));
        g.fill(ruleX, barY - 3, ruleX + 1, barY + barH + 3, PduTheme.TEXT);
        if (served < total) {
            final String shed = "shed";
            final int shedX = Math.min(right - font.width(shed), ruleX + 3);
            g.drawString(font, shed, shedX, bounds.bottom() - pad - 6, PduTheme.WARN, false);
        }
    }

    private static void hatch(GuiGraphics g, int x0, int y0, int x1, int y1) {
        if (x1 <= x0) {
            return;
        }
        final int h = y1 - y0;
        for (int x = x0 - h; x < x1; x += 4) {
            for (int row = 0; row < h; row++) {
                final int px = x + row;
                if (px >= x0 && px < x1) {
                    g.fill(px, y0 + row, px + 1, y0 + row + 1, 0x30000000);
                }
            }
        }
    }
}
