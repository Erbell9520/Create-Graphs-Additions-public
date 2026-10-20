package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LampSpotTest {
    private static long p(int x, int z) {
        return PlanPainter.pack(x, z);
    }

    @Test
    @DisplayName("a dark corridor cut by a wall: the lamp goes on the longer side and lights only that side")
    void wallsStopLight() {
        final Set<Long> dark = new HashSet<>();
        for (int x = 0; x < 10; x++) {
            if (x != 3) {
                dark.add(p(x, 0));
            }
        }
        final LampSpot.Spot s = LampSpot.best(dark, c -> PlanPainter.unpackZ(c) == 0 && PlanPainter.unpackX(c) >= 0
                && PlanPainter.unpackX(c) < 10 && PlanPainter.unpackX(c) != 3, c -> PlanPainter.unpackX(c) != 3
                && PlanPainter.unpackZ(c) == 0, LampSpot.REACH);
        assertNotNull(s);
        assertTrue(s.x() >= 4, "on the longer side, past the wall: " + s.x());
        assertEquals(6, s.lit(), "the six dark columns east of the wall, none west of it");
        assertEquals(3, LampSpot.lights(p(1, 0), dark, c -> PlanPainter.unpackX(c) != 3 && PlanPainter.unpackZ(c) == 0,
                LampSpot.REACH), "a lamp west of the wall lights the three there");
    }

    @Test
    @DisplayName("reach is the game's: fourteen steps lit, the fifteenth not")
    void reachIsFourteen() {
        final Set<Long> dark = new HashSet<>();
        for (int x = 0; x <= 20; x++) {
            dark.add(p(x, 0));
        }
        assertEquals(15, LampSpot.lights(p(0, 0), dark, c -> PlanPainter.unpackZ(c) == 0, LampSpot.REACH),
                "itself and fourteen more");
    }

    @Test
    @DisplayName("the lamp must stand on floor near the dark: a wall column is never the spot, nothing dark is no spot")
    void standsOnFloor() {
        final Set<Long> dark = Set.of(p(5, 5), p(6, 5));
        final LampSpot.Spot s = LampSpot.best(dark, c -> c == p(4, 5), c -> true, LampSpot.REACH);
        assertNotNull(s);
        assertEquals(4, s.x());
        assertEquals(2, s.lit());
        assertNull(LampSpot.best(Set.of(), c -> true, c -> true, LampSpot.REACH));
        assertNull(LampSpot.best(dark, c -> false, c -> true, LampSpot.REACH), "no floor to stand on");
    }

    @Test
    @DisplayName("between two equal choices the nearer to the dark's middle wins, and the answer never jumps")
    void stable() {
        final Set<Long> dark = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            dark.add(p(x, 0));
        }
        final LampSpot.Spot a = LampSpot.best(dark, c -> PlanPainter.unpackZ(c) == 0, c -> true, LampSpot.REACH);
        final LampSpot.Spot b = LampSpot.best(new HashSet<>(dark), c -> PlanPainter.unpackZ(c) == 0, c -> true, LampSpot.REACH);
        assertEquals(a, b);
        assertEquals(2, a.x(), "the middle of five");
    }
}
