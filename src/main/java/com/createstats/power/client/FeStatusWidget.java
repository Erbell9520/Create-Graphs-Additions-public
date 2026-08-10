package com.createstats.power.client;

import com.createstats.power.FeConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Locale;

/** [1] */
public final class FeStatusWidget {

    private static final int COL_GREEN = 0xFF58C558;
    private static final int COL_AMBER = 0xFFFFC24B;
    private static final int COL_RED = 0xFFFF4D4D;
    private static final int COL_TEXT_DIM = 0xFFB0B8C0;
    private static final int COL_FRAME = 0xFF3A3F44;
    private static final int COL_BAR_BG = 0xFF16181B;

    private FeStatusWidget() {
    }

    /** Height the panel occupies when it will draw; 0 otherwise. Lets screens reserve space. */
    public static int height(@Nullable FeConsumer fe) {
        return fe != null && fe.shouldDisplay() ? 32 : 0;
    }

    /** [2] */
    public static int draw(GuiGraphics g, Font font, int x, int y, int width, @Nullable FeConsumer fe) {
        if (fe == null || !fe.shouldDisplay()) {
            return y;
        }
        final boolean modeOn = fe.feModeEnabled();
        final boolean powered = fe.isPowered();
        // [3]
        final boolean lockedOff = fe.lock() == com.createstats.power.FeLock.FORCE_OFF;
        final boolean shed = modeOn && fe.isLoadShed() && !lockedOff;
        final boolean dead = modeOn && !powered;
        final float pct = fe.fillPct();
        final int stateColor = lockedOff ? COL_TEXT_DIM
                : dead && !shed ? COL_RED : (shed || fe.isBrownout()) ? COL_AMBER : COL_GREEN;

        // Row 1: POWER label + stored / capacity.
        g.drawString(font, "POWER", x, y, stateColor, true);
        final String amount = String.format(Locale.ROOT, "%,d / %,d FE", fe.getEnergyStored(), fe.getMaxEnergyStored());
        g.drawString(font, amount, x + width - font.width(amount), y, COL_TEXT_DIM, true);

        // Row 2: buffer bar.
        final int barY = y + 10;
        g.fill(x, barY, x + width, barY + 6, COL_BAR_BG);
        final int fill = Mth.clamp(Math.round(width * pct / 100.0f), 0, width);
        if (fill > 0) {
            g.fill(x, barY, x + fill, barY + 6, stateColor);
        }
        // Thin frame.
        g.fill(x, barY, x + width, barY + 1, COL_FRAME);
        g.fill(x, barY + 5, x + width, barY + 6, COL_FRAME);

        // Row 3: prominent offline banner, the live draw, or the mode-off note.
        final int rowY = y + 19;
        if (modeOn && fe.isBurstStarved()) {
            // [4]
            g.fill(x, rowY - 1, x + width, rowY + 9, 0xFF8A4A10);
            final String warn = "BURST > BUFFER";
            g.drawString(font, warn, x + (width - font.width(warn)) / 2, rowY, 0xFFFFE0B0, true);
        } else if (lockedOff) {
            // Grey and calm. This is a standing instruction from the player, not a fault.
            final String note = "SWITCHED OFF AT THE DISTRIBUTION PANEL";
            g.fill(x, rowY - 1, x + width, rowY + 9, 0xFF23282F);
            g.drawString(font, note, x + (width - font.width(note)) / 2, rowY, COL_TEXT_DIM, true);
        } else if (shed) {
            // [5]
            final String warn = powered
                    ? String.format(Locale.ROOT, "LOAD SHED  (reserve %.0f%%)", pct)
                    : "LOAD SHED";
            g.fill(x, rowY - 1, x + width, rowY + 9, 0xFF6A4A00);
            g.drawString(font, warn, x + (width - font.width(warn)) / 2, rowY, 0xFFFFE0B0, true);
        } else if (dead) {
            // [6]
            final boolean blink = (System.currentTimeMillis() / 450L) % 2L == 0L;
            final boolean recharging = fe.isRecharging();
            g.fill(x, rowY - 1, x + width, rowY + 9,
                    recharging ? 0xFF6A4A00 : blink ? 0xFFFF4D4D : 0xFF8A1010);
            final String warn = recharging
                    ? String.format(Locale.ROOT, "RECHARGING  %.0f%% / %d%%", pct, fe.rebootThresholdPct())
                    : "⚠ NO POWER ⚠";
            g.drawString(font, warn, x + (width - font.width(warn)) / 2, rowY, 0xFFFFFFFF, true);
        } else if (!modeOn) {
            g.drawString(font, "FE mode off (stored only)", x, rowY, COL_TEXT_DIM, true);
        } else {
            final FeConsumer.Settings settings = fe.settings();
            final int measured = fe.measuredDrainFePerTick();
            final float multiplier = fe.appliedLoadMultiplier();
            // Prefer the real measured spend; fall back to the configured rate before the client
            // has seen a serviced tick.
            final String draw = measured > 0
                    ? String.format(Locale.ROOT, "Draw %,d FE/t", measured)
                    : String.format(Locale.ROOT, "Draw: %d idle / %d active FE/t",
                            settings.idleDrain(), settings.activeDrain());
            g.drawString(font, draw, x, rowY, COL_TEXT_DIM, true);
            // Right side: the load multiplier when a source curve scaled it, else incoming supply,
            // else the burst cost.
            final String right;
            if (multiplier > 1.005f) {
                right = String.format(Locale.ROOT, "x%.2f", multiplier);
            } else if (fe.measuredInputFePerTick() > 0) {
                right = String.format(Locale.ROOT, "In %,d", fe.measuredInputFePerTick());
            } else if (settings.burstCost() > 0) {
                right = String.format(Locale.ROOT, "+%,d/op", settings.burstCost());
            } else {
                right = "";
            }
            if (!right.isEmpty()) {
                g.drawString(font, right, x + width - font.width(right), rowY, COL_TEXT_DIM, true);
            }
        }
        return y + 32;
    }
}
