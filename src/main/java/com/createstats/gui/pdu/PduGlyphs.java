package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import net.minecraft.client.gui.GuiGraphics;

/** [1] */
public final class PduGlyphs {

    private PduGlyphs() {
    }

    /** [2] */
    public static int drawPip(GuiGraphics g, int x, int y, BranchDevice d) {
        final PduTableModel.Status status = PduTableModel.Status.of(d);
        final boolean lockedOn = d.lock() == FeLock.FORCE_ON;
        final int size = 6;
        final int colour = switch (status) {
            case OFF -> PduTheme.TEXT_FAINT;
            case HOLDING, SHED -> PduTheme.WARN;
            case RUNNING -> lockedOn ? PduTheme.INFO : PduTheme.OK;
        };
        switch (status) {
            case OFF -> g.fill(x, y + 2, x + size, y + 4, colour);
            case HOLDING -> {
                g.fill(x, y, x + size, y + 2, colour);
                g.fill(x, y + 4, x + size, y + 6, colour);
            }
            case SHED -> PduTheme.outline(g, x, y, size, size, colour);
            case RUNNING -> g.fill(x, y, x + size, y + size, colour);
        }
        return colour;
    }

    /** A small padlock in an 8 by 8 box: open shackle for auto, closed for pinned, a minus for off. */
    public static void drawLock(GuiGraphics g, int x, int y, FeLock lock, boolean hovered) {
        final int colour = switch (lock) {
            case NONE -> hovered ? PduTheme.TEXT : PduTheme.TEXT_DIM;
            case FORCE_ON -> PduTheme.INFO;
            case FORCE_OFF -> PduTheme.TEXT_FAINT;
        };
        final int bx = x + 1;
        final int by = y + 3;
        if (lock == FeLock.FORCE_ON) {
            g.fill(bx, by, bx + 6, by + 5, colour);
        } else {
            PduTheme.outline(g, bx, by, 6, 5, colour);
        }
        if (lock == FeLock.NONE) {
            // Open shackle: raised, and hooked on one side only, so it reads as unlatched.
            g.fill(bx + 1, y, bx + 5, y + 1, colour);
            g.fill(bx + 1, y + 1, bx + 2, by, colour);
        } else {
            g.fill(bx + 1, y, bx + 5, y + 1, colour);
            g.fill(bx + 1, y + 1, bx + 2, by, colour);
            g.fill(bx + 4, y + 1, bx + 5, by, colour);
        }
        if (lock == FeLock.FORCE_OFF) {
            g.fill(bx + 2, by + 2, bx + 4, by + 3, colour);
        }
    }

    /** [3] */
    public static int tierColour(FePriority tier) {
        return switch (tier) {
            case CRITICAL -> PduTheme.CRIT;
            case HIGH -> PduTheme.WARN;
            case NORMAL -> PduTheme.INFO;
            case LOW -> PduTheme.TEXT_DIM;
            case OPTIONAL -> PduTheme.TEXT_FAINT;
        };
    }
}
