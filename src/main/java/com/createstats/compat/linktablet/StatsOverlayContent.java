package com.createstats.compat.linktablet;

import com.createstats.client.StressometerHudReadout;
import com.createstats.client.hud.HudInterpolation;
import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
final class StatsOverlayContent implements OverlayContent {
    private static final int ROW_H = 30;

    @Override
    public int height(int rowWidth) {
        return ROW_H;
    }

    @Override
    public void render(GuiGraphics g, Font font, ScreenTheme theme, int x, int top, int rowWidth,
                       int mouseX, int mouseY, boolean reachable, int clipTop, int clipBottom) {
        g.fill(x, top, x + rowWidth, top + ROW_H - 2, theme.rowBg);
        final boolean shadow = theme.textShadow;
        final int pad = 4;
        final int lineY = top + 3;

        if (StatsTabletPairing.getPaired() == null) {
            g.drawString(font, "Stats", x + pad, lineY, theme.textPrimary, shadow);
            drawRight(g, font, "not paired", x + rowWidth - pad, lineY, theme.textFaint, shadow);
            return;
        }
        final StressometerHudReadout r = StatsStressReadout.paired();
        if (r == null) {
            g.drawString(font, "Stats", x + pad, lineY, theme.textPrimary, shadow);
            drawRight(g, font, "out of range", x + rowWidth - pad, lineY, theme.textMuted, shadow);
            return;
        }
        final float pct = HudInterpolation.smooth("linktablet.overlay.stress", r.stressPercent());
        final int color = loadColor(pct, r.overstressed());
        final String pctText = String.format(Locale.ROOT, "%.0f%%", pct);
        final int pctW = font.width(pctText);
        final String label = font.plainSubstrByWidth(
                r.sourceLabel().isEmpty() ? "Stress" : r.sourceLabel(), rowWidth - pad * 2 - pctW - 6);
        g.drawString(font, label, x + pad, lineY, theme.textPrimary, shadow);
        g.drawString(font, pctText, x + rowWidth - pad - pctW, lineY, color, shadow);

        final int barX = x + pad;
        final int barY = lineY + font.lineHeight + 2;
        final int barW = rowWidth - pad * 2;
        final int barH = 6;
        g.fill(barX, barY, barX + barW, barY + barH, theme.surfaceLo);
        final int filled = Math.round(barW * clamp01(pct / 100f));
        g.fill(barX, barY, barX + filled, barY + barH, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth) {
        return false;
    }

    private static void drawRight(GuiGraphics g, Font font, String s, int rightX, int y, int color, boolean shadow) {
        g.drawString(font, s, rightX - font.width(s), y, color, shadow);
    }

    private static int loadColor(float pct, boolean over) {
        if (over || pct >= 85f) {
            return 0xFFFF4D4D;
        }
        if (pct >= 60f) {
            return 0xFFE0AA46;
        }
        return 0xFF4ADE80;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
