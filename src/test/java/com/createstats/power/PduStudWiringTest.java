package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** [1] */
class PduStudWiringTest {

    private static final long PANEL = 1L;
    private static final long CONNECTOR = 2L;
    private static final int RANGE = 32;
    private static final int MAX = 4;

    private static PduStudWiring.Endpoint stud(int index) {
        return PduStudWiring.Endpoint.stud(PANEL, index);
    }

    private static PduStudWiring.Endpoint connector() {
        return PduStudWiring.Endpoint.connector(CONNECTOR);
    }

    private static PduStudWiring.Verdict check(PduStudWiring.Endpoint from, PduStudWiring.Endpoint to,
                                               double distSq, boolean linked, int fromLinks, int toLinks) {
        return PduStudWiring.check(from, to, distSq, RANGE, linked, fromLinks, toLinks, MAX);
    }

    @Test
    @DisplayName("a stud to a connector inside range is the ordinary case")
    void studToConnectorIsAllowed() {
        assertEquals(PduStudWiring.Verdict.OK, check(stud(3), connector(), 100.0, false, 0, 0));
    }

    @Test
    @DisplayName("the wire runs either way round, so a connector clicked first is the same span")
    void connectorToStudIsAllowed() {
        assertEquals(PduStudWiring.Verdict.OK, check(connector(), stud(0), 100.0, false, 1, 2));
    }

    @Test
    @DisplayName("clicking one stud twice cancels rather than refusing")
    void samePointIsItsOwnVerdict() {
        assertEquals(PduStudWiring.Verdict.SAME_POINT, check(stud(5), stud(5), 0.0, false, 0, 0));
    }

    @Test
    @DisplayName("two different studs on one panel are still stud to stud, not a span")
    void studToStudIsRefused() {
        assertEquals(PduStudWiring.Verdict.BOTH_STUDS, check(stud(1), stud(2), 0.0, false, 0, 0));
    }

    @Test
    @DisplayName("a stud index off the panel is refused before anything else is judged")
    void badStudOutranksEveryOtherComplaint() {
        // [2]
        assertEquals(PduStudWiring.Verdict.BAD_STUD,
                check(PduStudWiring.Endpoint.stud(PANEL, PduStuds.COUNT), connector(), 1.0e9, true, MAX, MAX));
        assertEquals(PduStudWiring.Verdict.BAD_STUD,
                check(PduStudWiring.Endpoint.stud(PANEL, -2), connector(), 4.0, false, 0, 0));
    }

    @Test
    @DisplayName("range is measured squared, and the boundary counts as in range")
    void rangeBoundaryIsInclusive() {
        assertEquals(PduStudWiring.Verdict.OK, check(stud(0), connector(), RANGE * (double) RANGE, false, 0, 0));
        assertEquals(PduStudWiring.Verdict.TOO_FAR,
                check(stud(0), connector(), RANGE * (double) RANGE + 1.0, false, 0, 0));
    }

    @Test
    @DisplayName("an existing span is named as such rather than as a full terminal")
    void alreadyLinkedOutranksFull() {
        assertEquals(PduStudWiring.Verdict.ALREADY_LINKED, check(stud(0), connector(), 4.0, true, MAX, MAX));
    }

    @Test
    @DisplayName("either end being full stops the span")
    void fullAtEitherEnd() {
        assertEquals(PduStudWiring.Verdict.FULL, check(stud(0), connector(), 4.0, false, MAX, 0));
        assertEquals(PduStudWiring.Verdict.FULL, check(stud(0), connector(), 4.0, false, 0, MAX));
        assertEquals(PduStudWiring.Verdict.OK, check(stud(0), connector(), 4.0, false, MAX - 1, MAX - 1));
    }

    @Test
    @DisplayName("only the eight real studs are stud indices")
    void studIndexBounds() {
        for (int i = 0; i < PduStuds.COUNT; i++) {
            assertEquals(true, PduStudWiring.isStudIndex(i));
        }
        assertEquals(false, PduStudWiring.isStudIndex(-1));
        assertEquals(false, PduStudWiring.isStudIndex(PduStuds.COUNT));
    }

    @Test
    @DisplayName("a leg with no wire on it carries nothing, not the stud's own rating")
    void emptyStudCarriesNothing() {
        assertEquals(0, PduStudWiring.ceiling(24576, 8192, null));
        assertNull(PduStudWiring.best(List.of()));
    }

    @Test
    @DisplayName("the wire caps the stud, and the stud caps the wire")
    void ceilingIsTheLowerOfTheTwo() {
        // Plain copper under a stud rated for three times it: the wire is the limit.
        assertEquals(8192, PduStudWiring.ceiling(24576, 8192, WireTier.COPPER));
        assertEquals(16384, PduStudWiring.ceiling(24576, 8192, WireTier.INSULATED));
        // Shielded matches the stud exactly, and nothing above the stud's own rating is reachable.
        assertEquals(24576, PduStudWiring.ceiling(24576, 8192, WireTier.SHIELDED));
        assertEquals(8192, PduStudWiring.ceiling(8192, 8192, WireTier.SHIELDED));
    }

    @Test
    @DisplayName("the best grade on a stud sets its rating, so a spare cheap wire never throttles it")
    void bestGradeWins() {
        assertEquals(WireTier.SHIELDED,
                PduStudWiring.best(List.of(WireTier.COPPER, WireTier.SHIELDED, WireTier.INSULATED)));
        assertEquals(WireTier.COPPER, PduStudWiring.best(List.of(WireTier.COPPER)));
    }
}
