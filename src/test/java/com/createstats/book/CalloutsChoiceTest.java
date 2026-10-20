package com.createstats.book;

import com.createstats.book.layout.Callouts;
import com.createstats.book.specimen.SpecimenAnatomy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalloutsChoiceTest {
    private static Callouts.Pin pin(String id, boolean left, int boxY, int ax, int ay) {
        return new Callouts.Pin(id, 1, left, left ? 0 : 200, boxY, ax, ay);
    }

    @Test
    void aLeaderMayCrossOnlyThePartItEndsIn() {
        final List<Callouts.Pin> pins = List.of(pin("a", true, 15, 100, 20));
        assertEquals(1, Callouts.faults(pins, 0, 0, 1, 1, List.of(new int[]{40, 0, 20, 40})));

        assertEquals(0, Callouts.faults(pins, 0, 0, 1, 1, List.of(new int[]{40, 0, 70, 40})));

        final List<Callouts.Pin> beside = List.of(pin("b", true, 15, 62, 20));
        assertEquals(1, Callouts.faults(beside, 0, 0, 1, 1, List.of(new int[]{40, 16, 20, 8})));
    }

    @Test
    void marksThatRunTogetherReadAsOne() {
        final List<Callouts.Pin> close = List.of(pin("a", true, 15, 100, 20), pin("b", true, 30, 100,
                20 + Callouts.MARKS_APART - 1));
        assertEquals(1, Callouts.faults(close, 0, 0, 1, 1, List.of()));
        final List<Callouts.Pin> apart = List.of(pin("a", true, 15, 100, 20), pin("b", true, 30, 100,
                20 + Callouts.MARKS_APART));
        assertEquals(0, Callouts.faults(apart, 0, 0, 1, 1, List.of()));
    }

    @Test
    void aLeaderThroughAnotherMarkReadsWrongly() {
        final List<Callouts.Pin> pins = List.of(pin("a", true, 15, 150, 19), pin("b", false, 60, 80, 19));
        assertTrue(Callouts.faults(pins, 0, 0, 1, 1, List.of()) >= 1);
    }

    @Test
    void aLeaderGrazingAPartsOutlineDoesNotCrossIt() {
        assertFalse(Callouts.crossesRect(0, 10, 100, 10, 20, 10, 40, 20), "along the top edge");
        assertTrue(Callouts.crossesRect(0, 12, 100, 12, 20, 10, 40, 20), "through the middle");
        assertFalse(Callouts.crossesRect(0, 0, 10, 0, 20, 10, 40, 20), "short of it");
    }

    @Test
    void theChooserTakesTheCopyThatReadsCleanly() {
        final int[] wall = {20, 0, 30, 100};
        final SpecimenAnatomy.Hot behind = new SpecimenAnatomy.Hot("x", 70, 45, 10, 10, 75, 50, true);
        final SpecimenAnatomy.Hot clear = new SpecimenAnatomy.Hot("x", 150, 45, 10, 10, 155, 50, true);
        for (final List<SpecimenAnatomy.Hot> order : List.of(List.of(behind, clear), List.of(clear, behind))) {
            final SpecimenAnatomy a = Callouts.cleanest("test", 200, 100, SpecimenAnatomy.Kind.SCREEN,
                    List.of(new Callouts.Choice("x", order)), List.of(wall));
            assertEquals(155, a.control("x").anchorX());
            assertEquals(0, Callouts.faults(a, List.of(wall)));
        }
    }

    @Test
    void twoPartsSpreadRatherThanCrowdOnePanel() {
        final SpecimenAnatomy.Hot a1 = new SpecimenAnatomy.Hot("a", 10, 40, 20, 4, 12, 42, true);
        final SpecimenAnatomy.Hot a2 = new SpecimenAnatomy.Hot("a", 10, 80, 20, 4, 12, 82, true);
        final SpecimenAnatomy.Hot b1 = new SpecimenAnatomy.Hot("b", 10, 42, 20, 4, 12, 44, true);
        final SpecimenAnatomy.Hot b2 = new SpecimenAnatomy.Hot("b", 10, 82, 20, 4, 12, 84, true);
        final SpecimenAnatomy s = Callouts.cleanest("test", 200, 120, SpecimenAnatomy.Kind.SCREEN,
                List.of(new Callouts.Choice("a", List.of(a1, a2)), new Callouts.Choice("b", List.of(b1, b2))),
                List.of());
        assertEquals(0, Callouts.faults(s, List.of()));
        assertTrue(Math.abs(s.control("a").anchorY() - s.control("b").anchorY()) > 20, "one on each panel");
    }
}
