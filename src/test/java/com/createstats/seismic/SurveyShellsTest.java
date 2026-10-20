package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyShellsTest {
    @Test
    @DisplayName("the list is the bowl: every block below the plate within 64 blocks, once, nothing else")
    void theListIsTheBowl() {
        int expected = 0;
        for (int dy = -1; dy >= -64; dy--) {
            for (int dx = -64; dx <= 64; dx++) {
                for (int dz = -64; dz <= 64; dz++) {
                    if ((float) SurveyShells.distanceOf(dx, dy, dz) <= 64f) {
                        expected++;
                    }
                }
            }
        }
        assertEquals(expected, SurveyShells.size());
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < SurveyShells.size(); i++) {
            final int p = SurveyShells.packed(i);
            assertTrue(seen.add(p), "an offset is listed twice: " + p);
            assertTrue(SurveyShells.dy(p) <= -1, "an offset above the plate is listed");
            if (i > 0) {
                assertTrue(SurveyShells.distance(i) >= SurveyShells.distance(i - 1), "out of distance order at " + i);
            }
            assertEquals((float) SurveyShells.distanceOf(SurveyShells.dx(p), SurveyShells.dy(p), SurveyShells.dz(p)),
                    SurveyShells.distance(i), 0f);
        }

        assertTrue(Math.abs(expected - 2.0 / 3.0 * Math.PI * Math.pow(64, 3)) < 0.01 * expected, "size " + expected);
    }

    @Test
    @DisplayName("for every radius the ticks' shells partition the bowl: each block once, in order, nothing past r")
    void shellsPartitionEveryRadius() {
        for (int r = 1; r <= 64; r++) {
            int next = 0;
            for (int t = 1; t <= SeismicWave.scanTicks(r); t++) {
                final int from = SurveyShells.from(t, r);
                final int to = SurveyShells.to(t, r);
                assertEquals(next, from, "radius " + r + " tick " + t + " does not start where the last ended");
                assertTrue(to >= from);
                for (int i = from; i < to; i++) {
                    assertTrue(SurveyShells.distance(i) <= Math.min(r, SeismicWave.frontAt(t)) + 1e-6);
                }
                next = to;
            }
            assertEquals(SurveyShells.countWithin(r), next, "radius " + r + " stops short of its bowl");
            assertTrue(next == SurveyShells.size() || SurveyShells.distance(next) > r, "radius " + r + " scans past r");
        }
    }

    @Test
    @DisplayName("the largest shell of a radius-64 survey, the most a single tick reads, is about 41,000 blocks")
    void theWorstTick() {
        int worst = 0;
        for (int t = 1; t <= SeismicWave.scanTicks(64); t++) {
            worst = Math.max(worst, SurveyShells.to(t, 64) - SurveyShells.from(t, 64));
        }
        assertTrue(worst > 35_000 && worst < 45_000, "worst tick reads " + worst);
    }

    @Test
    @DisplayName("an offset packs and unpacks to itself across the whole range a survey uses")
    void packing() {
        for (int dx = -64; dx <= 64; dx += 3) {
            for (int dy = -65; dy <= 0; dy += 5) {
                for (int dz = -64; dz <= 64; dz += 7) {
                    final int p = SurveyShells.pack(dx, dy, dz);
                    assertTrue(p > 0, "a packed offset must never be zero: the vein book keys on it");
                    assertEquals(dx, SurveyShells.dx(p));
                    assertEquals(dy, SurveyShells.dy(p));
                    assertEquals(dz, SurveyShells.dz(p));
                }
            }
        }
        assertTrue(SurveyShells.inBowl(0, -1, 0, 0.5));
        assertTrue(!SurveyShells.inBowl(0, 0, 0, 64.0), "the rig's own layer is not ground under the plate");
    }
}
