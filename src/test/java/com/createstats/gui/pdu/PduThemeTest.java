package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduThemeTest {
    private static final int LARGE_W = PduTheme.Breakpoint.LARGE.idealW + 16;
    private static final int LARGE_H = PduTheme.Breakpoint.LARGE.idealH + 16;
    private static final int NORMAL_W = PduTheme.Breakpoint.NORMAL.idealW + 16;
    private static final int NORMAL_H = PduTheme.Breakpoint.NORMAL.idealH + 16;

    private static final int XL_W = PduTheme.Breakpoint.XL.idealW + 16;
    private static final int XL_H = PduTheme.Breakpoint.XL.idealH + 16;

    @Test
    void aRoomyWindowGetsTheLargeLayout() {
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(LARGE_W, LARGE_H));
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(XL_W - 1, XL_H));
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(XL_W, XL_H - 1));
    }

    @Test
    void theRoomiestWindowGetsXlAtAboutDoubleTheArea() {
        assertEquals(PduTheme.Breakpoint.XL, PduTheme.currentFor(XL_W, XL_H));
        final double large = PduTheme.Breakpoint.LARGE.idealW * (double) PduTheme.Breakpoint.LARGE.idealH;
        final double xl = PduTheme.Breakpoint.XL.idealW * (double) PduTheme.Breakpoint.XL.idealH;
        assertTrue(xl / large > 1.8 && xl / large < 2.1, "XL is " + xl / large + "x LARGE");
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
        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(3840, NORMAL_H - 1));
    }

    @Test
    void growingTheWindowNeverShrinksTheLayout() {
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
        assertEquals(20, PduTheme.Breakpoint.XL.rowH);
    }

    @Test
    void realWorldGuiScaleCanvasesResolveCorrectly() {
        assertEquals(PduTheme.Breakpoint.LARGE, PduTheme.currentFor(640, 360));

        assertEquals(PduTheme.Breakpoint.COMPACT, PduTheme.currentFor(480, 270));

        assertEquals(PduTheme.Breakpoint.XL, PduTheme.currentFor(854, 480));

        assertEquals(PduTheme.Breakpoint.XL, PduTheme.currentFor(960, 540));

        assertEquals(PduTheme.Breakpoint.XL, PduTheme.currentFor(1280, 720));
    }
}
