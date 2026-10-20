package com.createstats.gui.pdu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduLoadColumnsTest {
    private static final int[] REAL_WIDTHS = {310, 382, 478, 678};

    private static final int NARROWEST = 140;

    @Test
    void columnsNeverOverlapAtAnyWidth() {
        for (int w = NARROWEST; w <= 900; w++) {
            final PduLoadColumns c = PduLoadColumns.of(0, w);
            assertTrue(c.nameX() >= c.pipX() + PduLoadColumns.PIP_W, "name over pip at " + w);
            assertTrue(c.tierX() >= c.nameX() + c.nameW(), "tier over name at " + w);
            assertTrue(c.drawX() >= c.tierX() + c.tierW(), "draw over tier at " + w);
            assertTrue(c.stateX() >= c.drawX() + c.drawW(), "state over draw at " + w);
            if (c.legShown()) {
                assertTrue(c.legX() >= c.stateX() + c.stateW(), "leg over state at " + w);
                assertTrue(c.lockX() >= c.legX() + c.legW(), "lock over leg at " + w);
            } else {
                assertTrue(c.lockX() >= c.stateX() + c.stateW(), "lock over state at " + w);
            }
        }
    }

    @Test
    void everyColumnHasPositiveWidthAtEveryRealSize() {
        for (int w = 260; w <= 900; w++) {
            final PduLoadColumns c = PduLoadColumns.of(0, w);
            assertTrue(c.nameW() > 0, "name collapsed at " + w);
            assertTrue(c.stateW() > 0, "state collapsed at " + w);
        }
    }

    @Test
    void noColumnIsEverNegative() {
        for (int w = 0; w <= 900; w++) {
            final PduLoadColumns c = PduLoadColumns.of(0, w);
            assertTrue(c.nameW() >= 0, "negative name width at " + w);
            assertTrue(c.stateW() >= 0, "negative state width at " + w);
        }
    }

    @Test
    void theOriginIsHonoured() {
        final PduLoadColumns at0 = PduLoadColumns.of(0, 400);
        final PduLoadColumns at100 = PduLoadColumns.of(100, 400);
        assertEquals(at0.pipX() + 100, at100.pipX());
        assertEquals(at0.lockX() + 100, at100.lockX());
        assertEquals(at0.nameW(), at100.nameW(), "widths must not depend on where the table starts");
    }

    @Test
    void theLegColumnIsGivenUpBeforeTheNameIsStarved() {
        final PduLoadColumns compact = PduLoadColumns.of(0, REAL_WIDTHS[0]);
        assertFalse(compact.legShown(),
                "a COMPACT panel gives up the leg column; the coloured tab on the row still carries it");
        assertTrue(compact.nameW() >= PduLoadColumns.NAME_MIN,
                "and the name is what the space it freed goes to");
    }

    @Test
    void aWidePanelShowsEveryColumn() {
        final PduLoadColumns wide = PduLoadColumns.of(0, REAL_WIDTHS[2]);
        assertTrue(wide.legShown());
        assertEquals(PduLoadColumns.STATE_FULL_W, wide.stateW(),
                "the widest panel gives the state everything it can use and no more");
    }

    @Test
    void theStateColumnIsCappedAndTheNameTakesTheRest() {
        final PduLoadColumns narrow = PduLoadColumns.of(0, 400);
        final PduLoadColumns wide = PduLoadColumns.of(0, 900);
        assertTrue(wide.stateW() <= PduLoadColumns.STATE_FULL_W);

        assertEquals(500 - (wide.stateW() - narrow.stateW()), wide.nameW() - narrow.nameW(),
                "every pixel past the cap goes to the name");
    }

    @Test
    void theStateColumnNeverFallsBelowItsFlag() {
        for (int w = 260; w <= 900; w++) {
            assertTrue(PduLoadColumns.of(0, w).stateW() >= PduLoadColumns.STATE_FLAG_W,
                    "no room for a state flag at " + w);
        }
    }

    @Test
    void nameKeepsMoreRoomThanState() {
        for (int w = 260; w <= 900; w++) {
            final PduLoadColumns c = PduLoadColumns.of(0, w);
            assertTrue(c.nameW() >= c.stateW(),
                    "the device's identity is what the row is for, at width " + w);
        }
    }

    @Test
    void headerHitTestLandsOnTheColumnItIsOver() {
        for (int w : REAL_WIDTHS) {
            final PduLoadColumns c = PduLoadColumns.of(0, w);
            assertEquals(PduTableModel.Sort.NAME, c.columnAt(c.nameX() + 1));
            assertEquals(PduTableModel.Sort.NAME, c.columnAt(c.nameX() + c.nameW() - 1));
            assertEquals(PduTableModel.Sort.TIER, c.columnAt(c.tierX() + 1));
            assertEquals(PduTableModel.Sort.DRAW, c.columnAt(c.drawX() + 1));
            assertEquals(PduTableModel.Sort.STATUS, c.columnAt(c.stateX() + 1));
            if (c.legShown()) {
                assertEquals(PduTableModel.Sort.LEG, c.columnAt(c.legX() + 1));
            }
        }
    }

    @Test
    void thePipAndTheLockAreNotSorts() {
        final PduLoadColumns c = PduLoadColumns.of(0, REAL_WIDTHS[1]);
        assertNull(c.columnAt(c.pipX()), "the pip column has no header and no sort");
        assertNull(c.columnAt(c.lockX() + 1), "the padlock is a control, not a sort");
    }

    @Test
    void aHiddenLegColumnCannotBeClicked() {
        final PduLoadColumns c = PduLoadColumns.of(0, REAL_WIDTHS[0]);
        assertFalse(c.legShown());

        assertNotNull(c.boundsOf(PduTableModel.Sort.LEG));
        assertNull(c.columnAt(c.legX()));
    }

    @Test
    void headerBoundsMatchTheDrawnColumns() {
        final PduLoadColumns c = PduLoadColumns.of(40, 400);
        assertEquals(c.nameX(), c.boundsOf(PduTableModel.Sort.NAME)[0]);
        assertEquals(c.nameW(), c.boundsOf(PduTableModel.Sort.NAME)[1]);
        assertEquals(c.drawX(), c.boundsOf(PduTableModel.Sort.DRAW)[0]);
        assertEquals(c.stateW(), c.boundsOf(PduTableModel.Sort.STATUS)[1]);
    }

    @Test
    void everythingFitsInsideTheTable() {
        for (int w = NARROWEST; w <= 900; w++) {
            final PduLoadColumns c = PduLoadColumns.of(0, w);
            assertTrue(c.right() <= w, "columns ran off the right edge at width " + w
                    + " (right " + c.right() + ")");
        }
    }
}
