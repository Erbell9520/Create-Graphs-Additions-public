package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainRolesTest {
    private static TrainRoles.Facts body(int parent, boolean wheels) {
        return new TrainRoles.Facts(parent, wheels, false, false, false);
    }

    @Test
    void everyKindOfBodyGetsItsRole() {
        List<TrainRoles.Facts> train = List.of(
                body(-1, true),
                new TrainRoles.Facts(0, true, false, false, true),
                body(0, true),
                new TrainRoles.Facts(2, false, true, false, false),
                new TrainRoles.Facts(2, true, false, true, false),
                body(0, false));
        assertArrayEquals(new BodyRole[]{BodyRole.PART, BodyRole.PART, BodyRole.TRAILER, BodyRole.LOAD, BodyRole.TOWED, BodyRole.PART},
                TrainRoles.classify(train));
    }

    @Test
    void aPartAnswersToTheNearestBodyAheadThatIsNotAPart() {
        List<TrainRoles.Facts> train = List.of(
                body(-1, true),
                body(0, false),
                body(0, true),
                body(2, false),
                body(3, false),
                body(2, true));
        BodyRole[] roles = TrainRoles.classify(train);
        assertArrayEquals(new int[]{0, 0, 2, 2, 2, 5}, TrainRoles.owners(train, roles));
    }

    @Test
    void aLoopThatASaneScanNeverMakesAnswersToTheTractor() {
        List<TrainRoles.Facts> train = List.of(body(-1, true), body(2, false), body(1, false));
        BodyRole[] roles = TrainRoles.classify(train);
        assertArrayEquals(new int[]{0, 0, 0}, TrainRoles.owners(train, roles));
    }

    @Test
    void aBodyTurnsWithTheFirstBodyAheadWhoseLinkSwings() {
        int[] parent = {-1, 0, 1, 0, 3, 4, 5};
        boolean[] rigid = {true, true, true, false, true, true, false};
        assertArrayEquals(new int[]{0, 0, 0, 3, 3, 3, 6}, TrainRoles.rigidRoots(parent, rigid));
    }

    @Test
    void aBrokenChainTurnsOnItsOwnRatherThanWithTheTractor() {
        assertArrayEquals(new int[]{0, 1, 2}, TrainRoles.rigidRoots(new int[]{-1, 2, 1}, new boolean[]{true, true, true}),
                "a loop");
        assertArrayEquals(new int[]{0, 1}, TrainRoles.rigidRoots(new int[]{-1, -1}, new boolean[]{true, true}),
                "a missing parent");
        assertArrayEquals(new int[]{0, 1, 2}, TrainRoles.rigidRoots(new int[]{-1, 0, 7}, new boolean[]{true, false, true}),
                "a parent past the end");
    }

    @Test
    void onlyTrailersAndTowedVehiclesTakeAMode() {
        assertTrue(BodyRole.TRAILER.takesMode());
        assertTrue(BodyRole.TOWED.takesMode());
        assertFalse(BodyRole.PART.takesMode());
        assertTrue(BodyRole.SEPARATE.neverTouched());
        assertTrue(BodyRole.LOAD.neverTouched());
        assertFalse(BodyRole.TRAILER.neverTouched());
        assertEquals(BodyRole.PART, BodyRole.byOrdinal(99));
    }
}
