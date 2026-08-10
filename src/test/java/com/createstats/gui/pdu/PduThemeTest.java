package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduThemeTest {

    // [2]
    private static final int LARGE_W = PduTheme.Breakpoint.LARGE.idealW + 16;
    private static final int LARGE_H = PduTheme.Breakpoint.LARGE.idealH + 16;
    private static final int NORMAL_W = PduTheme.Breakpoint.NORMAL.idealW + 16;
    private static final int NORMAL_H = PduTheme.Breakpoint.NORMAL.idealH + 16;

    @Test
    void aRoomyWindowGetsTheLargeLayout() {
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(854, 480));
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(LARGE_W, LARGE_H));
    }

    @Test
    void justUnderLargeFallsToNormal() {
        assertEquals(PduTheme.Breakpoint.NORMAL, PduTheme.currentFor(LARGE_W - 1, LARGE_H));
        assertEquals(PduTheme.Breakpoint.NORMAL, PduTheme.currentFor(LARGE_W, LARGE_H - 1));
    }

    @Test
    void normalHoldsDownToItsThreshold() {
        assertEquals(PduTheme.Breakpoint.NORMAL, PduTheme.currentFor(NORMAL_W, NORMAL_H));
    }

    @Test
    void justUnderNormalFallsToCompact() {
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(NORMAL_W - 1, NORMAL_H));
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(NORMAL_W, NORMAL_H - 1));
    }

    @Test
    void aTinyWindowStillResolvesToCompactRatherThanNothing() {
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(200, 150));
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(0, 0));
    }

    @Test
    void aWideButShortWindowIsGatedByHeight() {
        // Width alone must not buy a bigger layout: a letterbox window that cannot hold NORMAL's
        // height gets COMPACT even though it is plenty wide.
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(3840, NORMAL_H - 1));
    }

    @Test
    void growingTheWindowNeverShrinksTheLayout() {
        // Monotonicity along the diagonal: as the window grows the chosen breakpoint only ever climbs.
        int lastOrdinal = -1;
        for (int size = 100; size <= 1000; size += 5) {
            final int ordinal = PduTheme.currentFor(size, size).ordinal();
            assertTrue(ordinal >= lastOrdinal,
                    "breakpoint dropped at window " + size + "x" + size);
            lastOrdinal = ordinal;
        }
    }

    @Test
    void rowHeightMatchesTheSpecPerBreakpoint() {
        assertEquals(14, PduTheme.Breakpoint.COMPACT.rowH);
        assertEquals(16, PduTheme.Breakpoint.NORMAL.rowH);
        assertEquals(18, PduTheme.Breakpoint.LARGE.rowH);
    }

    @Test
    void realWorldGuiScaleCanvasesResolveCorrectly() {
        // [3]
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(640, 360));
        // GUI scale 4 on 1920x1080 gives 480x270. NORMAL needs 264 + 16 = 280 px of height and there
        // are 270, so only COMPACT fits.
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(480, 270));
        // Minecraft's minimum window, 854x480, still has room for LARGE.
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(854, 480));
    }
}
