package com.createstats.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageDoorFrameTest {
    private record Art(String... rows) implements GarageDoorFrame.Probe {
        private char at(int h, int v) {
            final int row = -v;
            if (row < 0 || row >= rows.length) {
                return ' ';
            }
            final String line = rows[row];
            return (h < 0 || h >= line.length()) ? ' ' : line.charAt(h);
        }

        @Override
        public boolean hood(int h, int v) {
            return at(h, v) == 'H';
        }

        @Override
        public boolean clear(int h, int v) {
            return at(h, v) == '.';
        }
    }

    private static Art small() {
        return new Art(
                "HHH",
                "...",
                "...",
                "===");
    }

    @Test
    @DisplayName("a run of hoods over clear space reports its own dimensions")
    void sizesItself() {
        final GarageDoorFrame.Result r = GarageDoorFrame.detect(small(), 1, 0);
        assertTrue(r.ok(), "should form, got " + r.fault());
        assertEquals(0, r.left());
        assertEquals(2, r.right());
        assertEquals(3, r.width());
        assertEquals(2, r.height());
        assertEquals(6, r.area());
        assertEquals(-1, r.topV());
        assertEquals(-2, r.bottomV());
        assertEquals(0, r.interiorMinH(), "the curtain starts under the first hood, not beside it");
        assertEquals(2, r.interiorMaxH());
    }

    @Test
    @DisplayName("every hood in the run reports the same door")
    void agreesFromAnyHood() {
        final GarageDoorFrame.Result from1 = GarageDoorFrame.detect(small(), 1, 0);
        for (int h = 0; h <= 2; h++) {
            final GarageDoorFrame.Result r = GarageDoorFrame.detect(small(), h, 0);
            assertTrue(r.ok(), "h=" + h + " failed with " + r.fault());
            assertEquals(from1.left(), r.left(), "h=" + h + " disagreed about the left end");
            assertEquals(from1.width(), r.width(), "h=" + h + " disagreed about the width");
            assertEquals(from1.height(), r.height(), "h=" + h + " disagreed about the drop");
        }
    }

    @Test
    @DisplayName("one hood on its own is a one wide door")
    void oneHoodIsADoor() {
        final GarageDoorFrame.Result r = GarageDoorFrame.detect(new Art("H", ".", ".", "="), 0, 0);
        assertTrue(r.ok(), "should form, got " + r.fault());
        assertEquals(1, r.width());
        assertEquals(2, r.height());
    }

    @Test
    @DisplayName("something under one corner shortens the whole door")
    void dropIsTheShallowestColumn() {
        final GarageDoorFrame.Result r = GarageDoorFrame.detect(new Art(
                "HHH",
                "...",
                "..X",
                "..."), 0, 0);
        assertTrue(r.ok(), "should form, got " + r.fault());
        assertEquals(3, r.width());
        assertEquals(1, r.height(), "the crate under the right column shortens all three");
    }

    @Test
    @DisplayName("the drop stops at the cap rather than running to bedrock")
    void dropStopsAtTheCap() {
        final String[] rows = new String[GarageDoorFrame.MAX_DROP + 8];
        rows[0] = "HH";
        for (int i = 1; i < rows.length; i++) {
            rows[i] = "..";
        }
        final GarageDoorFrame.Result r = GarageDoorFrame.detect(new Art(rows), 0, 0);
        assertTrue(r.ok(), "should form, got " + r.fault());
        assertEquals(GarageDoorFrame.MAX_DROP, r.height());
    }

    @Test
    @DisplayName("the tallest door still winds into its own drum")
    void theCapIsTheOneThatFits() {
        assertTrue(GarageDoorCurl.fitsHood(GarageDoorFrame.MAX_DROP),
                "a " + GarageDoorFrame.MAX_DROP + " block curtain must fit the hood");
        assertFalse(GarageDoorCurl.fitsHood(GarageDoorFrame.MAX_DROP + 1),
                "the cap is not the last one that fits, so it was chosen rather than derived");
    }

    @Test
    @DisplayName("nothing clear under the run is not a door")
    void noRoomIsNamed() {
        final GarageDoorFrame.Result r = GarageDoorFrame.detect(new Art("HHH", "XXX"), 1, 0);
        assertEquals(GarageDoorFrame.Fault.NO_ROOM, r.fault());
    }

    @Test
    @DisplayName("a run longer than one door says so rather than making half of one")
    void tooWideIsNamed() {
        final StringBuilder run = new StringBuilder();
        for (int i = 0; i < GarageDoorFrame.MAX_WIDTH + 3; i++) {
            run.append('H');
        }
        final String clear = run.toString().replace('H', '.');
        final GarageDoorFrame.Result r =
                GarageDoorFrame.detect(new Art(run.toString(), clear, clear), 2, 0);
        assertEquals(GarageDoorFrame.Fault.TOO_WIDE, r.fault());
    }

    @Test
    @DisplayName("a run of exactly the cap still forms")
    void theCapItselfForms() {
        final StringBuilder run = new StringBuilder();
        for (int i = 0; i < GarageDoorFrame.MAX_WIDTH; i++) {
            run.append('H');
        }
        final String clear = run.toString().replace('H', '.');
        final GarageDoorFrame.Result r =
                GarageDoorFrame.detect(new Art(run.toString(), clear, clear), 0, 0);
        assertTrue(r.ok(), "the cap itself should form, got " + r.fault());
        assertEquals(GarageDoorFrame.MAX_WIDTH, r.width());
    }

    @Test
    @DisplayName("two bays in one wall resolve independently")
    void twoBaysResolveIndependently() {
        final Art art = new Art(
                "HHH HH",
                "... ..",
                "... ..");
        final GarageDoorFrame.Result left = GarageDoorFrame.detect(art, 1, 0);
        assertTrue(left.ok(), "left failed with " + left.fault());
        assertEquals(0, left.left());
        assertEquals(3, left.width());

        final GarageDoorFrame.Result right = GarageDoorFrame.detect(art, 5, 0);
        assertTrue(right.ok(), "right failed with " + right.fault());
        assertEquals(4, right.left());
        assertEquals(2, right.width());
    }

    @Test
    @DisplayName("a block that is not a hood is not a door")
    void notAHoodIsNamed() {
        assertEquals(GarageDoorFrame.Fault.NOT_A_HOOD,
                GarageDoorFrame.detect(small(), 1, -1).fault());
    }

    @Test
    @DisplayName("re-sizing a door that is already there gives the same answer")
    void sizingIsIdempotent() {
        final GarageDoorFrame.Result first = GarageDoorFrame.detect(small(), 1, 0);
        final GarageDoorFrame.Result again = GarageDoorFrame.detect(small(), 1, 0);
        assertEquals(first, again);
    }
}
