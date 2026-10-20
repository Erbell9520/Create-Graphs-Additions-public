package com.createstats.dynlight;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamShapeTest {
    private static final double EPS = 1.0e-9;

    @Test
    void aBeamHoldsFullThenTapersThenGivesOut() {
        assertEquals(12, BeamShape.levelAt(0.0, 12, 10.0, 30.0, 2, 40.0));
        assertEquals(12, BeamShape.levelAt(10.0, 12, 10.0, 30.0, 2, 40.0));
        assertEquals(7, BeamShape.levelAt(20.0, 12, 10.0, 30.0, 2, 40.0), "halfway down the taper");
        assertEquals(2, BeamShape.levelAt(30.0, 12, 10.0, 30.0, 2, 40.0));
        assertEquals(2, BeamShape.levelAt(39.9, 12, 10.0, 30.0, 2, 40.0), "the far level holds to the reach");
        assertEquals(0, BeamShape.levelAt(40.1, 12, 10.0, 30.0, 2, 40.0), "nothing past the reach");
        assertEquals(0, BeamShape.levelAt(-1.0, 12, 10.0, 30.0, 2, 40.0));
        assertEquals(0, BeamShape.levelAt(Double.NaN, 12, 10.0, 30.0, 2, 40.0));
    }

    @Test
    void theTaperOnlyEverDims() {
        int last = Integer.MAX_VALUE;
        for (double d = 0.0; d <= 40.0; d += 0.25) {
            final int level = BeamShape.levelAt(d, 15, 8.0, 32.0, 1, 40.0);
            assertTrue(level <= last, "no brightening at " + d);
            last = level;
        }
    }

    @Test
    void noBeamIsBrighterThanFifteenOrThanItsOwnLevel() {
        assertEquals(15, BeamShape.levelAt(1.0, 40, 10.0, 30.0, 2, 40.0));
        assertEquals(6, BeamShape.levelAt(35.0, 6, 10.0, 30.0, 12, 40.0), "a far level above the level is the level");
        assertEquals(0, BeamShape.levelAt(1.0, 0, 10.0, 30.0, 0, 40.0), "a beam that is off lights nothing");
    }

    @Test
    void thePoolWidensWithDistanceThenStops() {
        assertEquals(0.5, BeamShape.poolRadius(0.0, 0.5, 0.2, 4.0), EPS);
        assertEquals(2.5, BeamShape.poolRadius(10.0, 0.5, 0.2, 4.0), EPS);
        assertEquals(4.0, BeamShape.poolRadius(100.0, 0.5, 0.2, 4.0), EPS);
        assertEquals(0.5, BeamShape.poolRadius(-5.0, 0.5, 0.2, 4.0), EPS, "behind the lens is at the lens");
    }

    @Test
    void theRingGrowsWithThePool() {
        assertEquals(0, BeamShape.ringRays(0.0));
        assertEquals(0, BeamShape.ringRays(2.49));
        assertEquals(6, BeamShape.ringRays(2.5));
        assertEquals(6, BeamShape.ringRays(5.99));
        assertEquals(8, BeamShape.ringRays(6.0));
        assertEquals(8, BeamShape.ringRays(16.0));
        assertEquals(0, BeamShape.ringRays(Double.NaN));
    }

    @Test
    void theRingLandsSixTenthsOfTheWayOut() {
        final double half = BeamShape.ringHalfAngle(5.0, 20.0);
        assertEquals(0.6 * 5.0, Math.tan(half) * 20.0, EPS);
        assertEquals(Math.atan2(3.0, 1.0), BeamShape.ringHalfAngle(5.0, 0.2), EPS,
                "a pool against the lens is measured as a block away, so the ring does not fan out sideways");
    }

    @Test
    void everyRingRayIsUnitLengthAndExactlyTheHalfAngleOffItsAxis() {
        final double[][] axes = {
                {0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {0, 0, -1}, {1, 1, 1}, {0.3, -0.95, 0.05}, {-2, 0.5, 7}};
        final double half = Math.toRadians(11.0);
        for (double[] axis : axes) {
            final double len = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
            for (int count : new int[] {6, 8}) {
                final double[][] ring = BeamShape.ring(axis[0], axis[1], axis[2], half, count);
                assertEquals(count, ring.length);
                for (double[] r : ring) {
                    assertEquals(1.0, Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]), EPS);
                    final double cos = (r[0] * axis[0] + r[1] * axis[1] + r[2] * axis[2]) / len;
                    assertEquals(Math.cos(half), cos, EPS, "off-axis angle for axis " + axis[0] + "," + axis[1]
                            + "," + axis[2]);
                }
            }
        }
    }

    @Test
    void theRingIsEvenlySpacedSoItAveragesBackToTheAxis() {
        final double[] axis = {0.2, -0.9, 0.4};
        final double len = Math.sqrt(0.04 + 0.81 + 0.16);
        final double half = Math.toRadians(20.0);
        final double[][] ring = BeamShape.ring(axis[0], axis[1], axis[2], half, 8);
        double sx = 0;
        double sy = 0;
        double sz = 0;
        for (double[] r : ring) {
            sx += r[0];
            sy += r[1];
            sz += r[2];
        }
        final double scale = 8 * Math.cos(half) / len;
        assertEquals(axis[0] * scale, sx, 1.0e-9);
        assertEquals(axis[1] * scale, sy, 1.0e-9);
        assertEquals(axis[2] * scale, sz, 1.0e-9);
        final double gap01 = dot(ring[0], ring[1]);
        for (int k = 1; k < ring.length; k++) {
            assertEquals(gap01, dot(ring[k], ring[(k + 1) % ring.length]), 1.0e-9, "even spacing at " + k);
        }
    }

    @Test
    void aZeroOrBrokenAxisHasNoRing() {
        assertEquals(0, BeamShape.ring(0, 0, 0, 0.2, 6).length);
        assertEquals(0, BeamShape.ring(Double.NaN, 1, 0, 0.2, 6).length);
        assertEquals(0, BeamShape.ring(0, -1, 0, 0.2, 0).length);
    }

    @Test
    void aBuiltBeamIsNormalisedAndFillsItsDefaultsFromItsReach() {
        final BeamLight beam = BeamLight.from(new Vec3(1, 2, 3), new Vec3(0, -3, 4)).level(13).reach(30).build();
        assertEquals(0.0, beam.dx(), EPS);
        assertEquals(-0.6, beam.dy(), EPS);
        assertEquals(0.8, beam.dz(), EPS);
        assertEquals(15.0, beam.fullDistance(), EPS, "full to half the reach by default");
        assertEquals(30.0, beam.fadeDistance(), EPS, "fading by the reach by default");
        assertEquals(13, beam.levelAt(4.0));
        assertEquals(0, beam.levelAt(31.0));
        assertTrue(beam.isValid());
    }

    @Test
    void aBeamWithNoDirectionOrNoPlaceIsNotALight() {
        assertFalse(BeamLight.from(0, 64, 0, 0, 0, 0).build().isValid());
        assertFalse(BeamLight.from(Double.NaN, 64, 0, 0, -1, 0).build().isValid());
        assertFalse(new PointLight(0, Double.POSITIVE_INFINITY, 0, 10).isValid());
        assertEquals(15, new PointLight(0, 64, 0, 99).level());
        assertEquals(0, new PointLight(0, 64, 0, -2).level());
    }

    @Test
    void aFarLevelCannotOutshineTheBeam() {
        final BeamLight beam = BeamLight.from(0, 64, 0, 1, 0, 0).level(5).fadeTo(10, 12).build();
        assertEquals(5, beam.farLevel());
    }

    private static final double LENS_H = 1.5;
    private static final double TIP = Math.atan(LENS_H / 10.0);

    @Test
    void aGrazingCutGetsASpineFromItsNearEdgeToTheReachEveryFourBlocks() {
        final double ax = 0.0;
        final double ay = -Math.sin(TIP);
        final double az = -Math.cos(TIP);
        final double centre = LENS_H / Math.sin(TIP);
        final double radius = BeamShape.poolRadius(centre, 1.0, 0.14, 3.5);
        final double[][] spine = BeamShape.spine(0.0, LENS_H, 0.0, ax, ay, az, centre, 0, 1, 0, radius, 28.0,
                BeamShape.SPINE_SPACING);

        final double theta = Math.atan2(radius, centre);
        final double near = LENS_H / Math.tan(TIP + theta);

        final double far = Math.sqrt(28.0 * 28.0 - LENS_H * LENS_H) - BeamShape.SPINE_INSET;
        assertTrue(spine.length >= 2, "a spine");
        assertEquals(near, -spine[0][2], 1.0e-9, "starts at the cone's near edge");
        assertEquals(far, -spine[spine.length - 1][2], 1.0e-9, "ends half a block inside the reach");
        assertEquals((int) Math.ceil((far - near) / 4.0) + 1, spine.length, "a point every four blocks at most");
        for (int i = 0; i < spine.length; i++) {
            assertEquals(0.0, spine[i][0], 1.0e-9, "on the beam's own line");
            assertEquals(0.0, spine[i][1], 1.0e-9, "on the road");
            if (i > 0) {
                final double gap = spine[i - 1][2] - spine[i][2];
                assertTrue(gap > 0.0 && gap <= 4.0 + 1.0e-9, "forward, at most four apart: " + gap);
                assertEquals(spine[1][2] - spine[0][2], spine[i][2] - spine[i - 1][2], 1.0e-9, "evenly");
            }
        }
    }

    @Test
    void theSpineEndsWhereTheUpperEdgeLandsWhenItDoes() {
        final double tip = Math.toRadians(30.0);
        final double centre = 3.0 / Math.sin(tip);
        final double radius = 1.0;
        final double[][] spine = BeamShape.spine(0.0, 3.0, 0.0, 0.0, -Math.sin(tip), -Math.cos(tip), centre,
                0, 1, 0, radius, 40.0, 2.0);
        final double theta = Math.atan2(radius, centre);
        assertTrue(spine.length >= 2);
        assertEquals(3.0 / Math.tan(tip + theta), -spine[0][2], 1.0e-9);
        assertEquals(3.0 / Math.tan(tip - theta), -spine[spine.length - 1][2], 1.0e-9, "the upper edge's landing");
    }

    @Test
    void aBeamIntoAWallOrSquareOntoAFloorKeepsNoSpine() {
        assertEquals(0, BeamShape.spine(0.5, 65.5, 10.0, 0, 0, -1, 6.0, 0, 0, 1, 2.0, 20.0, 4.0).length);

        assertEquals(0, BeamShape.spine(6.5, 69.5, 11.5, 0, -1, 0, 4.5, 0, 1, 0, 1.7, 32.0, 4.0).length);

        final double c = 3.0 * Math.sqrt(2.0);
        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, -1, -1, c, 0, 1, 0, 1.0, 40.0, 4.0).length);

        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, 0, -1, 5.0, 0, 1, 0, 1.0, 40.0, 4.0).length);
        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, -0.3, -1, 5.0, 0, -1, 0, 1.0, 40.0, 4.0).length);

        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, -0.3, -1, 0.0, 0, 1, 0, 1.0, 40.0, 4.0).length);
        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, -0.3, -1, 5.0, 0, 1, 0, 0.0, 40.0, 4.0).length);
        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, -0.3, -1, 5.0, 0, 1, 0, 1.0, 5.0, 4.0).length);
        assertEquals(0, BeamShape.spine(0.0, 3.0, 0.0, 0, -0.3, -1, 5.0, 0, 1, 0, 1.0, 40.0, 0.0).length);
    }

    @Test
    void theSpineRunsAlongAWallTheBeamGrazes() {
        final double in = Math.toRadians(10.0);
        final double ax = Math.sin(in);
        final double az = -Math.cos(in);
        final double centre = 5.0 / ax;
        final double[][] spine = BeamShape.spine(0.0, 65.0, 0.0, ax, 0.0, az, centre, -1, 0, 0, 1.5, 40.0, 4.0);
        assertTrue(spine.length >= 4, "a long cut along the wall: " + spine.length);
        final double theta = Math.atan2(1.5, centre);
        assertEquals(-5.0 / Math.tan(in + theta), spine[0][2], 1.0e-9, "from where the near edge meets the wall");
        assertEquals(-(Math.sqrt(40.0 * 40.0 - 25.0) - BeamShape.SPINE_INSET), spine[spine.length - 1][2], 1.0e-9,
                "to where the wall leaves the reach");
        for (double[] p : spine) {
            assertEquals(5.0, p[0], 1.0e-9, "on the wall");
            assertEquals(65.0, p[1], 1.0e-9, "level, as the beam is");
            assertTrue(p[2] < 0.0, "ahead of the lens");
            final double d = Math.sqrt(p[0] * p[0] + (p[1] - 65.0) * (p[1] - 65.0) + p[2] * p[2]);
            assertTrue(d <= 40.0, "inside the reach: " + d);
        }
    }

    @Test
    void theSpineDoesNotCareHowLongTheAxisOrNormalAre() {
        final double ay = -Math.sin(TIP);
        final double az = -Math.cos(TIP);
        final double centre = LENS_H / Math.sin(TIP);
        final double[][] unit = BeamShape.spine(0.0, LENS_H, 0.0, 0, ay, az, centre, 0, 1, 0, 2.4, 28.0, 4.0);
        final double[][] scaled = BeamShape.spine(0.0, LENS_H, 0.0, 0, 7 * ay, 7 * az, centre, 0, 3, 0, 2.4, 28.0, 4.0);
        assertEquals(unit.length, scaled.length);
        for (int i = 0; i < unit.length; i++) {
            for (int k = 0; k < 3; k++) {
                assertEquals(unit[i][k], scaled[i][k], 1.0e-9);
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
