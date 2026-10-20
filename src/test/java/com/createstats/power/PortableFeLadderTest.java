package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableFeLadderTest {
    private static final int CEILING = 32768;

    @Test
    @DisplayName("column zero is the floor rate, never an off position")
    void columnZeroIsARealRate() {
        assertEquals(PortableFeLadder.FLOOR_FE, PortableFeLadder.feAt(0, CEILING));
        assertTrue(PortableFeLadder.feAt(0, CEILING) > 0);
    }

    @Test
    @DisplayName("the top column is the ceiling, and every column is a Limiter ladder figure")
    void topColumnIsTheCeiling() {
        final int max = PortableFeLadder.maxDial(CEILING);
        assertEquals(CEILING, PortableFeLadder.feAt(max, CEILING));
        for (int dial = 0; dial <= max; dial++) {
            final int fe = PortableFeLadder.feAt(dial, CEILING);
            assertEquals(fe, LimiterLadder.feAt(LimiterLadder.nearestNotch(fe)), "column " + dial);
        }
    }

    @Test
    @DisplayName("columns rise strictly, so dragging the board never goes backwards")
    void columnsRise() {
        for (int dial = 1; dial <= PortableFeLadder.maxDial(CEILING); dial++) {
            assertTrue(PortableFeLadder.feAt(dial, CEILING) > PortableFeLadder.feAt(dial - 1, CEILING));
        }
    }

    @Test
    @DisplayName("milestones land on powers of two")
    void milestonesArePowersOfTwo() {
        for (int dial = 0; dial <= PortableFeLadder.maxDial(CEILING); dial += PortableFeLadder.MILESTONE) {
            assertEquals(1, Integer.bitCount(PortableFeLadder.feAt(dial, CEILING)), "column " + dial);
        }
    }

    @Test
    @DisplayName("a ceiling between notches is honoured downward, so nobody can dial past it")
    void ceilingRoundsDown() {
        final int max = PortableFeLadder.maxDial(10_000);
        assertEquals(8192, PortableFeLadder.feAt(max, 10_000));
        assertEquals(8192, PortableFeLadder.feAt(max + 5, 10_000));
    }

    @Test
    @DisplayName("a ceiling below the floor still leaves one usable column")
    void tinyCeilingKeepsOneColumn() {
        assertEquals(0, PortableFeLadder.maxDial(16));
        assertEquals(PortableFeLadder.FLOOR_FE, PortableFeLadder.feAt(3, 16));
    }

    @Test
    @DisplayName("a new block sits at the base connector rate, or the ceiling when that is lower")
    void defaultIsTheBaseRate() {
        assertEquals(8192, PortableFeLadder.feAt(PortableFeLadder.defaultDial(CEILING), CEILING));
        assertEquals(4096, PortableFeLadder.feAt(PortableFeLadder.defaultDial(4096), 4096));
    }

    @Test
    @DisplayName("dialFor and feAt agree both ways")
    void roundTrip() {
        for (int dial = 0; dial <= PortableFeLadder.maxDial(CEILING); dial++) {
            assertEquals(dial, PortableFeLadder.dialFor(PortableFeLadder.feAt(dial, CEILING), CEILING));
        }
        assertEquals(0, PortableFeLadder.dialFor(1, CEILING));
    }

    @Test
    @DisplayName("the cursor label fits Create's 56 pixel cursor sprite on every rung of the ladder")
    void cursorLabelAlwaysFits() {
        for (int notch = 0; notch <= LimiterLadder.MAX_NOTCH; notch++) {
            final String label = PortableFeLadder.cursorLabel(LimiterLadder.feAt(notch));
            final int width = PortableFeLadder.textWidth(label);
            assertTrue(width <= PortableFeLadder.CURSOR_TEXT_MAX_PX, label + " is " + width + " px");

            assertTrue((width / 2) * 2 + 3 <= 56, label);
            assertFalse(label.contains(PortableFeColors.CHARGE_GLYPH) || label.contains(PortableFeColors.DRAW_GLYPH),
                    label);
        }
    }

    @Test
    @DisplayName("the cursor shows the exact figure while it fits, then the shortest rounding that does")
    void cursorLabelPrefersTheExactFigure() {
        assertEquals("256 FE/t", PortableFeLadder.cursorLabel(256));
        assertEquals("4,096 FE/t", PortableFeLadder.cursorLabel(4096));
        assertEquals("6,144 FE/t", PortableFeLadder.cursorLabel(6144));
        assertEquals("12.3k FE/t", PortableFeLadder.cursorLabel(12288));
        assertEquals("32.8k FE/t", PortableFeLadder.cursorLabel(32768));
        assertEquals("98.3k FE/t", PortableFeLadder.cursorLabel(98304));
        assertEquals("131k FE/t", PortableFeLadder.cursorLabel(131072));
        assertEquals("786k FE/t", PortableFeLadder.cursorLabel(786432));
        assertEquals("1.05M FE/t", PortableFeLadder.cursorLabel(1048576));
    }

    @Test
    @DisplayName("the width model matches vanilla's advances for the characters a label uses")
    void widthModel() {
        assertEquals(52, PortableFeLadder.textWidth("4,096 FE/t"));
        assertEquals(58, PortableFeLadder.textWidth("12,288 FE/t"));
        assertEquals(51, PortableFeLadder.textWidth("32.8k FE/t"));
    }
}
