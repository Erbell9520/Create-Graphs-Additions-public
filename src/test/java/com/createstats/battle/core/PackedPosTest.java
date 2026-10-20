package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackedPosTest {
    @Test
    void roundTripsTheWholeRange() {
        int[][] corners = {{-1024, -256, -1024}, {1023, 255, 1023}, {0, 0, 0}, {-1, -1, -1}, {517, -3, -900}};
        for (int[] c : corners) {
            int p = PackedPos.pack(c[0], c[1], c[2]);
            assertTrue(p >= 0, "bit 31 stays clear so packed values sort as ints");
            assertEquals(c[0], PackedPos.dx(p));
            assertEquals(c[1], PackedPos.dy(p));
            assertEquals(c[2], PackedPos.dz(p));
        }
    }

    @Test
    void refusesWhatDoesNotFit() {
        assertFalse(PackedPos.fits(1024, 0, 0));
        assertFalse(PackedPos.fits(0, 256, 0));
        assertFalse(PackedPos.fits(0, 0, -1025));
        assertThrows(IllegalArgumentException.class, () -> PackedPos.pack(0, -257, 0));
        assertEquals(-1, PackedPos.offset(PackedPos.pack(1023, 0, 0), 1, 0, 0));
    }

    @Test
    void sortsByXThenYThenZ() {
        assertTrue(PackedPos.pack(0, 255, 1023) < PackedPos.pack(1, -256, -1024));
        assertTrue(PackedPos.pack(5, 0, 1023) < PackedPos.pack(5, 1, -1024));
        assertTrue(PackedPos.pack(5, 1, 3) < PackedPos.pack(5, 1, 4));
    }

    @Test
    void chebyshevIsTheLargestAxisGap() {
        assertEquals(2, PackedPos.chebyshev(PackedPos.pack(0, 0, 0), PackedPos.pack(2, -1, 1)));
        assertEquals(0, PackedPos.chebyshev(PackedPos.pack(3, 3, 3), PackedPos.pack(3, 3, 3)));
    }
}
