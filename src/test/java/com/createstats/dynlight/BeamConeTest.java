package com.createstats.dynlight;

import com.createstats.vehiclelight.VehicleLightBeams;
import com.createstats.vehiclelight.VehicleLightGlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamConeTest {
    private static final double EPS = 1.0e-9;

    private static final BeamCone PLAIN = new BeamCone(5.0, 12.0, 2.0, 0.3, 10.0, 30.0, false, 1.0, 0xFFFFFF);

    private static final BeamCone DIPPED = new BeamCone(5.0, 12.0, 2.0, 0.3, 10.0, 30.0, true, 1.0, 0xFFFFFF);

    private static double[] pointOn(double distance, double off, double side) {
        final double o = Math.toRadians(off);
        final double s = Math.toRadians(side);
        return new double[] {distance * Math.cos(o) * Math.sin(s), distance * Math.sin(o),
                -distance * Math.cos(o) * Math.cos(s)};
    }

    private static double facing(BeamCone.Aimed aimed, double[] p) {
        return aimed.at(p[0], p[1], p[2], 0, 0, 1);
    }

    @Test
    void theLightIsFullOnTheAxisAndGoneAtTheOuterEdge() {
        final BeamCone.Aimed aimed = PLAIN.aim(0, 0, 0, 0, 0, -1);
        assertEquals(1.0, facing(aimed, pointOn(5.0, 0.0, 0.0)), EPS, "on the axis, in the hot spot");
        assertEquals(0.0, facing(aimed, pointOn(5.0, 12.0, 0.0)), EPS, "at the outer edge");
        assertEquals(0.0, facing(aimed, pointOn(5.0, 20.0, 0.0)), EPS, "outside it");
        assertEquals(0.0, facing(aimed, pointOn(5.0, 180.0, 0.0)), EPS, "behind the lens");
        double last = 2.0;
        for (double off = 0.0; off <= 13.0; off += 0.25) {
            final double v = facing(aimed, pointOn(5.0, off, 0.0));
            assertTrue(v <= last + EPS, "no brightening away from the axis at " + off);
            last = v;
        }

        assertEquals((1.0 - 0.3) * Math.cos(Math.toRadians(5.0)), facing(aimed, pointOn(5.0, 5.0, 0.0)), EPS,
                "the inner edge is full less the hot spot");
        assertTrue(facing(aimed, pointOn(5.0, 1.0, 0.0)) > 0.99, "inside the hot spot it climbs to full");
    }

    @Test
    void theLightHoldsToFullToThenTapersToNothingAtTheReach() {
        final BeamCone.Aimed aimed = PLAIN.aim(0, 0, 0, 0, 0, -1);
        assertEquals(1.0, facing(aimed, pointOn(1.0, 0.0, 0.0)), EPS);
        assertEquals(1.0, facing(aimed, pointOn(10.0, 0.0, 0.0)), EPS, "full to fullTo");
        assertEquals(0.5, facing(aimed, pointOn(20.0, 0.0, 0.0)), EPS, "halfway down the taper");
        assertEquals(0.0, facing(aimed, pointOn(30.0, 0.0, 0.0)), EPS, "nothing at the reach");
        assertEquals(0.0, facing(aimed, pointOn(31.0, 0.0, 0.0)), EPS, "or past it");
        double last = 2.0;
        for (double d = 0.5; d <= 31.0; d += 0.25) {
            final double v = facing(aimed, pointOn(d, 0.0, 0.0));
            assertTrue(v <= last + EPS, "no brightening with distance at " + d);
            last = v;
        }
    }

    @Test
    void aSurfaceAtAGlanceIsDimmerAndOneFacingAwayIsDark() {
        final BeamCone.Aimed aimed = PLAIN.aim(0, 0, 0, 0, 0, -1);
        final double[] p = pointOn(5.0, 0.0, 0.0);
        assertEquals(1.0, aimed.at(p[0], p[1], p[2], 0, 0, 1), EPS, "square on");
        assertEquals(0.0, aimed.at(p[0], p[1], p[2], 0, 0, -1), EPS, "facing away");
        assertEquals(0.0, aimed.at(p[0], p[1], p[2], 0, 1, 0), EPS, "a floor the beam runs along, exactly level");

        final BeamCone.Aimed down = PLAIN.aim(0, 2, 0, 0, -2, -8);
        final double expected = 2.0 / Math.sqrt(68.0);
        assertEquals(expected, down.at(0, 0, -8, 0, 1, 0), 1.0e-9, "N . L on the road");
        assertEquals(BeamCone.PLANT_INCIDENCE, aimed.at(p[0], p[1], p[2], Double.NaN, Double.NaN, Double.NaN), EPS,
                "a plant's cross has no normal and gets the floor");
    }

    @Test
    void theDippedBeamIsCutOffAboveTheHorizonExceptForTheKickUpOnTheRight() {
        final BeamCone.Aimed dipped = DIPPED.aim(0, 0, 0, 0, 0, -1);
        final BeamCone.Aimed full = PLAIN.aim(0, 0, 0, 0, 0, -1);

        assertTrue(facing(dipped, pointOn(5.0, -3.0, 0.0)) > 0.9, "below the cut-off the dipped beam is whole");
        assertEquals(0.0, facing(dipped, pointOn(5.0, 2.0, 0.0)), EPS, "above the horizon it is dark");
        assertTrue(facing(full, pointOn(5.0, 2.0, 0.0)) > 0.9, "the full beam has no cut-off");
        assertEquals(0.0, facing(dipped, pointOn(5.0, 2.0, -8.0)), EPS, "and to the left it stays dark");

        assertTrue(facing(dipped, pointOn(5.0, 0.5, 8.0)) > 0.5, "the kick-up lights the right-hand roadside");
        assertEquals(0.0, facing(dipped, pointOn(5.0, 4.0, 8.0)), EPS, "but not above its own line");

        assertTrue(dipped.at(5.0 * Math.sin(Math.toRadians(8)), 5.0 * Math.sin(Math.toRadians(0.5)),
                -5.0 * Math.cos(Math.toRadians(8)), 0, 0, 1) > 0.5, "east of a north-facing lamp");
        assertEquals(0.0, dipped.at(-5.0 * Math.sin(Math.toRadians(8)), 5.0 * Math.sin(Math.toRadians(0.5)),
                -5.0 * Math.cos(Math.toRadians(8)), 0, 0, 1), EPS, "west of it, nothing");

        final BeamCone.Aimed east = DIPPED.aim(0, 0, 0, 1, 0, 0);
        assertTrue(east.at(5.0 * Math.cos(Math.toRadians(8)), 5.0 * Math.sin(Math.toRadians(0.5)),
                5.0 * Math.sin(Math.toRadians(8)), -1, 0, 0) > 0.5);
        assertEquals(0.0, east.at(5.0 * Math.cos(Math.toRadians(8)), 5.0 * Math.sin(Math.toRadians(0.5)),
                -5.0 * Math.sin(Math.toRadians(8)), -1, 0, 0), EPS);
    }

    @Test
    void theCutOffIsCrispButNotAliased() {
        final BeamCone.Aimed dipped = DIPPED.aim(0, 0, 0, 0, 0, -1);
        final BeamCone.Aimed plain = PLAIN.aim(0, 0, 0, 0, 0, -1);
        final double limit = -BeamCone.CUT_BELOW_HORIZON;
        final double[] under = pointOn(5.0, limit - BeamCone.CUT_SOFT, 0.0);
        final double[] over = pointOn(5.0, limit + BeamCone.CUT_SOFT, 0.0);
        final double[] on = pointOn(5.0, limit, 0.0);
        assertEquals(facing(plain, under), facing(dipped, under), 1.0e-9, "whole a degree under");
        assertTrue(facing(plain, over) > 0.9, "the plain cone lights the point a degree over");
        assertEquals(0.0, facing(dipped, over), 1.0e-9, "the dipped one is gone there");
        final double mid = facing(dipped, on) / facing(plain, on);
        assertTrue(mid > 0.4 && mid < 0.6, "half on the line: " + mid);
    }

    @Test
    void daylightSwallowsTheBeamAtNoonAndMidnightShowsMostOfIt() {
        assertEquals(0.0, BeamCone.daylight(0, 15, 1.0), EPS, "noon under the open sky");
        assertEquals(1.0, BeamCone.daylight(0, 0, 1.0), EPS, "a cave");
        assertEquals(1.0, BeamCone.daylight(0, 0, 0.2), EPS, "a cave at night");
        final double midnight = BeamCone.daylight(0, 15, 0.2);
        assertTrue(midnight > 0.7 && midnight < 0.8, "midnight under the open sky: " + midnight);
        assertEquals(0.0, BeamCone.daylight(15, 0, 0.2), EPS, "in full block light there is nothing left to add");

        assertEquals(1.0 - (14.0 / 15.0) / (4.0 - 3.0 * 14.0 / 15.0), BeamCone.daylight(14, 0, 0.2), EPS,
                "next to a torch only a fifth is left to add");
        assertTrue(BeamCone.daylight(8, 15, 0.2) < midnight, "the dimmed underlay's own cell takes a little");
        assertTrue(BeamCone.daylight(15, 15, 0.5) == 0.0 && BeamCone.daylight(-3, 99, 7.0) >= 0.0, "held to 0 to 1");
    }

    @Test
    void exposureSaturatesSoftlyAndTheGainsAreSetForTheRoad() {
        assertEquals(0.0, BeamCone.expose(0.0), EPS);
        assertEquals(0.0, BeamCone.expose(-1.0), EPS);
        assertEquals(1.0 - Math.exp(-1.0), BeamCone.expose(1.0), EPS);
        assertTrue(BeamCone.expose(5.0) > 0.99 && BeamCone.expose(5.0) < 1.0, "a soft shoulder, never over");
        assertEquals(1.0, BeamCone.expose(50.0), EPS, "and one in the limit");
        double last = -1.0;
        for (double k = 0.0; k <= 10.0; k += 0.1) {
            assertTrue(BeamCone.expose(k) > last, "monotonic at " + k);
            last = BeamCone.expose(k);
        }

        final BeamCone dipped = VehicleLightBeams.drawn(VehicleLightGlow.DIM);
        final double tip = VehicleLightBeams.tipFor(1.5, VehicleLightGlow.DIM);
        final double[] aim = VehicleLightBeams.aim(0, 0, -1, tip, 0.0);
        final BeamCone.Aimed aimed = dipped.aim(0, 1.5, 0, aim[0], aim[1], aim[2]);
        final double night = BeamCone.daylight(0, 15, 0.2);
        final double atTen = BeamCone.expose(aimed.at(0, 0, -10, 0, 1, 0) * dipped.gain() * night);
        final double atEight = BeamCone.expose(aimed.at(0, 0, -8, 0, 1, 0) * dipped.gain() * night);
        final double atEdge = BeamCone.expose(aimed.at(0, 0, -5, 0, 1, 0) * dipped.gain() * night);
        final double atFar = BeamCone.expose(aimed.at(0, 0, -25, 0, 1, 0) * dipped.gain() * night);
        assertTrue(atTen > 0.35 && atTen < 0.65, "the pool ten blocks out: " + atTen);
        assertTrue(atEight > atTen, "brightest a little short of the landing, where the road faces the lamp more "
                + "and the axis's hot core still covers it: " + atEight);
        assertTrue(atEdge > 0.1 && atEdge < atTen, "the near edge is the cone's soft rim, lit but not the peak: " + atEdge);
        assertTrue(atFar > 0.0 && atFar < 0.2, "a glow at the far end: " + atFar);
        assertTrue(VehicleLightBeams.drawn(VehicleLightGlow.BRIGHT).gain() > dipped.gain(), "the full beam burns brighter");
    }

    @Test
    void fogFadesTheBeamAsVanillaFadesEverythingElse() {
        assertEquals(1.0, BeamCone.fogFade(10.0, 32.0, 96.0), EPS, "inside the fog start");
        assertEquals(0.0, BeamCone.fogFade(96.0, 32.0, 96.0), EPS, "at the end");
        assertEquals(0.0, BeamCone.fogFade(200.0, 32.0, 96.0), EPS);
        assertEquals(0.5, BeamCone.fogFade(64.0, 32.0, 96.0), EPS, "halfway, smoothstep's middle");
        assertTrue(BeamCone.fogFade(50.0, 32.0, 96.0) > BeamCone.fogFade(80.0, 32.0, 96.0));
    }

    @Test
    void thePoolStartsWhereTheConesLowerEdgeMeetsTheRoadFromAnyHeight() {
        for (double height : new double[] {1.5, 3.0, 5.0}) {
            for (VehicleLightGlow step : new VehicleLightGlow[] {VehicleLightGlow.DIM, VehicleLightGlow.BRIGHT}) {
                final BeamCone cone = VehicleLightBeams.drawn(step);
                final double tip = VehicleLightBeams.tipFor(height, step);
                final double[] aim = VehicleLightBeams.aim(0, 0, -1, tip, 0.0);
                final BeamCone.Aimed aimed = cone.aim(0, height, 0, aim[0], aim[1], aim[2]);
                final double edge = cone.nearEdge(height, tip);
                double first = Double.NaN;
                for (double z = 0.0; z <= cone.reach(); z += 0.02) {
                    if (aimed.at(0, 0, -z, 0, 1, 0) > 0.0) {
                        first = z;
                        break;
                    }
                }
                assertTrue(!Double.isNaN(first), "the road is lit at all from " + height);
                assertEquals(edge, first, 1.0, "from " + height + " blocks up the " + step + " pool starts at the lower edge");
                assertTrue(aimed.at(0, 0, -(edge - 0.5), 0, 1, 0) == 0.0, "and the road before it is dark");
                assertTrue(aimed.at(0, 0, -(edge + 1.5), 0, 1, 0) > 0.0, "and lit just past it");
            }
        }
    }

    @Test
    void theHeadlightConesAreTheirOwnNumbers() {
        final BeamCone dipped = VehicleLightBeams.drawn(VehicleLightGlow.DIM);
        final BeamCone full = VehicleLightBeams.drawn(VehicleLightGlow.BRIGHT);
        assertTrue(dipped.dipped() && !full.dipped(), "only the dipped beam has the cut-off");
        assertTrue(full.reach() > dipped.reach() && full.outerDeg() > dipped.outerDeg(), "the full beam is longer and wider");
        assertEquals(28.0, dipped.reach(), EPS, "the block light underlay's own reach");
        assertEquals(44.0, full.reach(), EPS);
        assertEquals(10.0, dipped.fullTo(), EPS);
        assertEquals(18.0, full.fullTo(), EPS);
        assertEquals(VehicleLightBeams.BEAM_COLOUR, dipped.colour());
        assertEquals(0xF4 / 255.0, dipped.green(), EPS);
    }

    @Test
    void aConeIsMadeLegal() {
        final BeamCone odd = new BeamCone(10.0, 4.0, 20.0, 3.0, 50.0, 20.0, true, -1.0, 0);
        assertTrue(odd.outerDeg() > odd.innerDeg(), "the outer edge is outside the inner");
        assertTrue(odd.hotDeg() <= odd.innerDeg(), "the hot spot is inside the inner edge");
        assertEquals(1.0, odd.hotBoost(), EPS);
        assertTrue(odd.reach() > odd.fullTo());
        assertEquals(0.0, odd.gain(), EPS);
        assertEquals(0.0, PLAIN.aim(0, 0, 0, 0, 0, 0).at(0, 0, -5, 0, 0, 1), EPS, "no aim, no light");
    }
}
