package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepellerShapeTest {
    @Test
    void theSphereIsASphereAndTheColumnIgnoresHeight() {
        final RepellerShape s = RepellerShape.SPHERE;
        assertTrue(s.contains(0.5, 64.5, 0.5, 16, 0.5, 64.5, 16.5));
        assertFalse(s.contains(0.5, 64.5, 0.5, 16, 0.5, 64.5, 16.6));
        assertFalse(s.contains(0.5, 64.5, 0.5, 16, 0.5, 82.0, 0.5), "17.5 above the centre is outside a 16 sphere");
        final RepellerShape c = RepellerShape.COLUMN;
        assertTrue(c.contains(0.5, 64.5, 0.5, 16, 0.5, 300.0, 16.5), "a phantom 235 blocks up is in the column");
        assertTrue(c.contains(0.5, 64.5, 0.5, 16, 0.5, -60.0, 0.5));
        assertFalse(c.contains(0.5, 64.5, 0.5, 16, 17.0, 64.5, 0.5));
        assertFalse(s.contains(0, 0, 0, 0, 0, 0, 0), "a radius of nothing holds nothing");
    }

    @Test
    void chunkKeysMatchVanillasPackingAndFloorNegativesCorrectly() {
        assertEquals(0L, RepellerShape.chunkKey(0, 0));
        assertEquals(RepellerShape.chunkKey(-1, -1), RepellerShape.chunkKeyAt(-0.01, -15.99));
        assertEquals(RepellerShape.chunkKey(-1, 0), RepellerShape.chunkKeyAt(-16.0, 15.99));
        assertEquals(RepellerShape.chunkKey(-2, 0), RepellerShape.chunkKeyAt(-16.01, 0.0));

        assertEquals(0x0000000300000002L, RepellerShape.chunkKey(2, 3));
        assertEquals(0xFFFFFFFFFFFFFFFFL, RepellerShape.chunkKey(-1, -1));
    }

    @Test
    void everyChunkColumnTheDiscReachesIsListedAndNoOther() {
        final double[][] cases = {{0.5, 0.5, 8}, {7.5, -3.5, 16}, {-100.5, 250.5, 64}, {15.99, 15.99, 12},
                {-0.5, -0.5, 33}, {8.0, 8.0, 0.25}};
        for (double[] c : cases) {
            final double cx = c[0];
            final double cz = c[1];
            final double r = c[2];
            final Set<Long> listed = new HashSet<>();
            for (long k : RepellerShape.chunksTouched(cx, cz, r)) {
                assertTrue(listed.add(k), "listed twice");
            }

            final Set<Long> needed = new HashSet<>();
            for (double x = Math.floor(cx - r) - 1; x <= cx + r + 1; x += 0.25) {
                for (double z = Math.floor(cz - r) - 1; z <= cz + r + 1; z += 0.25) {
                    final double dx = x - cx;
                    final double dz = z - cz;
                    if (dx * dx + dz * dz <= r * r) {
                        needed.add(RepellerShape.chunkKeyAt(x, z));
                    }
                }
            }
            assertTrue(listed.containsAll(needed), "a column holding part of the disc was left out: r=" + r);

            for (long k : listed) {
                final int x = (int) k;
                final int z = (int) (k >> 32);
                assertTrue(RepellerShape.discTouchesChunk(cx, cz, r, x, z));
            }
        }
    }

    @Test
    void aRadiusSixtyFourVolumeTouchesSixtyFourToSixtyNineColumns() {
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                final int n = RepellerShape.chunksTouched(0.5 + i, 0.5 + j, 64).length;
                min = Math.min(min, n);
                max = Math.max(max, n);
            }
        }
        assertEquals(64, min);
        assertEquals(69, max);
        assertEquals(0, RepellerShape.chunksTouched(0, 0, 0).length);
    }
}
