package com.createstats.compat.linktablet;

import com.createstats.client.StressometerHudReadout;
import com.createstats.client.hud.HudInterpolation;
import com.modpack.linktablet.api.client.TabletFaceContext;
import com.modpack.linktablet.theme.ScreenTheme;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
final class StatsTabletFace {
    private StatsTabletFace() {
    }

    static void paint(TabletFaceContext ctx) {
        final float w = ctx.width();
        final float h = ctx.height();
        final ScreenTheme theme = ctx.theme();

        ctx.text("STATS", w / 2f, 0.6f, 1.7f, theme.textMuted);

        final StressometerHudReadout r =
                StatsTabletPairing.getPaired() == null ? null : StatsStressReadout.paired();
        if (r == null) {
            ctx.text("no link", w / 2f, h / 2f - 1f, 2.0f, theme.textFaint);
            return;
        }

        final String key = ctx.blockEntity() != null
                ? "linktablet.face." + ctx.blockEntity().getBlockPos().asLong()
                : "linktablet.face";
        final float pct = HudInterpolation.smooth(key, r.stressPercent());
        final int color = loadColor(pct, r.overstressed());
        ctx.text(String.format(Locale.ROOT, "%.0f%%", pct), w / 2f, h * 0.30f, Math.max(3f, h * 0.34f), color);

        final float barH = Math.max(1.4f, h * 0.13f);
        final float barY = h - barH - 1.2f;
        final float barX = 1.4f;
        final float barW = w - 2.8f;
        ctx.fill(barX, barY, barW, barH, theme.surfaceLo);
        final float filled = barW * Math.max(0f, Math.min(1f, pct / 100f));
        if (filled > 0f) {
            ctx.fill(barX, barY, filled, barH, color);
        }
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
}
