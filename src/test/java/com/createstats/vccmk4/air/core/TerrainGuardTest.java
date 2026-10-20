package com.createstats.vccmk4.air.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainGuardTest {
    private static final Feel FEEL = Feel.STANDARD;

    private static final double Y = 80.0;
    private static final double DROP = 3.0;
    private static final double NO_CEILING = Double.NaN;

    private static List<TerrainGuard.Sample> strip(double at, double ridge) {
        List<TerrainGuard.Sample> out = new ArrayList<>();
        for (double d = 0.0; d <= 160.0; d += TerrainGuard.STEP) {
            out.add(new TerrainGuard.Sample(d, Math.abs(d - at) < 1.0e-9 ? ridge : 60.0));
        }
        return out;
    }

    @Test
    void flatGroundFarBelowAsksNothing() {
        TerrainGuard.Result r = TerrainGuard.assess(strip(-1.0, 0.0), Y, Y, DROP, 6.0, FEEL, NO_CEILING);
        assertEquals(TerrainGuard.Verdict.CLEAR, r.verdict());
        assertEquals(Double.NEGATIVE_INFINITY, r.floor());
        assertEquals(Double.POSITIVE_INFINITY, r.speedCap());
        assertEquals(Y, r.fly(Y));
        assertEquals(60.0, r.peakY());
        assertNull(r.note());
    }

    @Test
    void aRidgeIsClimbedForOnceItIsInsideTheClimbTime() {
        double need = 93.0 + TerrainGuard.CLEARANCE + DROP;
        double climb = TerrainGuard.climbTime(need - Y, FEEL);
        assertEquals(1.5 + 7.5 + (20.0 - 11.25) / 3.0, climb, 1.0e-9, "the gas's lag, speeding up, then the climb rate");
        double armAt = TerrainGuard.SAFETY * climb + TerrainGuard.ARM_PAD_S;
        double speed = 5.0;

        TerrainGuard.Result far = TerrainGuard.assess(strip(152.0, 93.0), Y, Y, DROP, speed, FEEL, NO_CEILING);
        assertTrue(152.0 / speed > armAt);
        assertEquals(TerrainGuard.Verdict.CLEAR, far.verdict(), "thirty seconds out: nothing to do yet");
        assertEquals(93.0, far.peakY(), "but it is seen");

        TerrainGuard.Result near = TerrainGuard.assess(strip(100.0, 93.0), Y, Y, DROP, speed, FEEL, NO_CEILING);
        assertTrue(100.0 / speed < armAt);
        assertEquals(TerrainGuard.Verdict.CLIMB, near.verdict());
        assertEquals(need, near.floor(), 1.0e-9);
        assertEquals(need, near.fly(Y), 1.0e-9, "flown over it");
        assertEquals(Y + 30.0, near.fly(Y + 30.0), 1.0e-9, "a ship already higher keeps its own height");
        assertTrue(near.note().startsWith("Terrain ahead: climbing to y 100"), near.note());
        assertEquals("Terrain ahead", AerialPilot.headline(near.note()));
    }

    @Test
    void aRidgeTooCloseToClimbAtThisSpeedSlowsTheShip() {
        double speed = 5.0;
        double need = 93.0 + TerrainGuard.CLEARANCE + DROP;
        double needT = TerrainGuard.SAFETY * TerrainGuard.climbTime(need - Y, FEEL);
        TerrainGuard.Result r = TerrainGuard.assess(strip(80.0, 93.0), Y, Y, DROP, speed, FEEL, NO_CEILING);
        assertEquals(TerrainGuard.Verdict.SLOW, r.verdict());
        assertEquals(80.0 / needT, r.speedCap(), 1.0e-9, "slow enough to make the climb in the distance left");
        assertEquals(need, r.floor(), 1.0e-9, "and climbing meanwhile");

        TerrainGuard.Result higher = TerrainGuard.assess(strip(80.0, 93.0), 96.0, Y, DROP, speed, FEEL, NO_CEILING);
        assertTrue(higher.speedCap() > r.speedCap());
    }

    @Test
    void aShipAlmostClearOfARidgeIsLiftedNotStopped() {
        double need = 93.0 + TerrainGuard.CLEARANCE + DROP;
        TerrainGuard.Result r = TerrainGuard.assess(strip(8.0, 93.0), need - 1.5, Y, DROP, 5.0, FEEL, NO_CEILING);
        assertEquals(TerrainGuard.Verdict.CLIMB, r.verdict());
        assertEquals(Double.POSITIVE_INFINITY, r.speedCap());
    }

    @Test
    void groundAboveTheCeilingIsStoppedShortOf() {
        double ceiling = 90.0;
        TerrainGuard.Result r = TerrainGuard.assess(strip(52.0, 93.0), Y, Y, DROP, 5.0, FEEL, ceiling);
        assertEquals(TerrainGuard.Verdict.WALL, r.verdict());
        assertEquals(TerrainGuard.stopSpeed(52.0 - TerrainGuard.STOP_MARGIN, FEEL), r.speedCap(), 1.0e-9);
        assertEquals(Double.NEGATIVE_INFINITY, r.floor(), "no floor above the ceiling: it cannot be flown");
        assertTrue(r.note().contains("above the ceiling"));

        TerrainGuard.Result at = TerrainGuard.assess(strip(8.0, 93.0), Y, Y, DROP, 0.5, FEEL, ceiling);
        assertEquals(0.0, at.speedCap(), 1.0e-9);
    }

    @Test
    void groundItCannotSeeIsNotAssumedFlat() {
        List<TerrainGuard.Sample> s = new ArrayList<>();
        for (double d = 0.0; d <= 160.0; d += TerrainGuard.STEP) {
            s.add(new TerrainGuard.Sample(d, d <= 40.0 ? 60.0 : d == 100.0 ? 200.0 : Double.NaN));
        }
        TerrainGuard.Result r = TerrainGuard.assess(s, Y, Y, DROP, 5.0, FEEL, NO_CEILING);
        assertEquals(TerrainGuard.Verdict.BLIND, r.verdict());
        assertEquals(44.0, r.seen(), 1.0e-9, "the first unknown sample");
        assertEquals(TerrainGuard.stopSpeed(44.0 - TerrainGuard.STOP_MARGIN, FEEL), r.speedCap(), 1.0e-9);
        assertEquals(60.0, r.peakY(), "nothing past the unknown is read");
        assertNull(r.helmNote(), "a pilot can see the world: no warning for the heightmap's edge");
    }

    @Test
    void theGroundUnderTheShipLiftsItAtOnce() {
        List<TerrainGuard.Sample> s = List.of(new TerrainGuard.Sample(-5.0, 76.0), new TerrainGuard.Sample(0.0, 60.0));
        TerrainGuard.Result r = TerrainGuard.assess(s, Y, Y, DROP, 0.0, FEEL, NO_CEILING);
        assertEquals(76.0 + TerrainGuard.CLEARANCE + DROP, r.floor(), 1.0e-9);
        assertEquals(Double.POSITIVE_INFINITY, r.speedCap());
    }

    @Test
    void theHeldHeightIsThePilotsAndTheHelmIsOnlyWarned() {
        TerrainGuard.Result r = TerrainGuard.assess(strip(100.0, 93.0), Y, Y, DROP, 5.0, FEEL, NO_CEILING);
        assertTrue(r.helmNote().startsWith("Terrain ahead: the ground at y 93"), r.helmNote());

        TerrainGuard.Result clear = TerrainGuard.assess(strip(100.0, 93.0), Y, 101.0, DROP, 5.0, FEEL, NO_CEILING);
        assertFalse(clear.acting());
    }

    @Test
    void theProfileMovesWithTheShip() {
        List<TerrainGuard.Sample> s = List.of(new TerrainGuard.Sample(-10.0, 60.0), new TerrainGuard.Sample(0.0, 61.0),
                new TerrainGuard.Sample(10.0, 62.0), new TerrainGuard.Sample(20.0, 63.0));
        TerrainGuard.Profile p = new TerrainGuard.Profile(0.0, 0.0, 1.0, 0.0, DROP, 10.0, s);
        List<TerrainGuard.Sample> ahead = p.ahead(5.0, 0.0);
        assertEquals(3, ahead.size(), "the first sample is behind the stern now");
        assertEquals(-15.0, ahead.get(0).along(), 1.0e-9, "under the hull: centre-of-mass distance less the bow's reach");
        assertEquals(5.0, ahead.get(2).along(), 1.0e-9);
        assertEquals(4.0, p.speedAlong(4.0, 3.0), 1.0e-9);
        assertEquals(0.0, p.speedAlong(-4.0, 0.0), "backing along the track is no speed along it");
    }

    @Test
    void theGroundIsReadThreeAbreastAndTheUnknownKeptUnknown() {
        TerrainGuard.Profile p = TerrainGuard.read(0.0, 0.0, 1.0, 0.0, DROP, 8.0, 3.0, 64.0,
                (x, z) -> z < -2.0 && x > 40.0 ? Double.NaN : z > 2.0 && x >= 20.0 ? 90.0 : 60.0);
        List<TerrainGuard.Sample> s = p.samples();
        assertEquals(-8.0, s.get(0).along(), 1.0e-9, "from the stern");
        assertEquals(TerrainGuard.STEP, s.get(1).along() - s.get(0).along(), 1.0e-9);
        assertEquals(64.0 + 8.0, s.get(s.size() - 1).along(), 1.0e-9, "to the reach past the bow");
        for (TerrainGuard.Sample x : s) {
            if (x.along() > 40.0) {
                assertTrue(Double.isNaN(x.ground()), "one column unknown: the sample is unknown at " + x.along());
            } else {
                assertEquals(x.along() >= 20.0 ? 90.0 : 60.0, x.ground(), 1.0e-9, "the highest of three at " + x.along());
            }
        }
    }

    @Test
    void theTrackIsGoTosBearingElseTheWayItMovesElseTheNose() {
        double[] goTo = TerrainGuard.track(true, new double[] {10.0, 0.0}, 0.0, 0.0, 0.0, 5.0, 0.0);
        assertEquals(1.0, goTo[0], 1.0e-9);
        double[] moving = TerrainGuard.track(false, null, 0.0, 0.0, 0.0, 5.0, 90.0);
        assertEquals(1.0, moving[1], 1.0e-9, "moving south: the way it goes, not its nose");
        double[] still = TerrainGuard.track(false, null, 0.0, 0.0, 0.1, 0.0, 90.0);
        assertEquals(1.0, still[0], 1.0e-9, "at rest: its nose, east");
        assertEquals(TerrainGuard.MIN_REACH, TerrainGuard.reach(0.0));
        assertEquals(TerrainGuard.MAX_REACH, TerrainGuard.reach(40.0));
        assertEquals(96.0, TerrainGuard.reach(6.0), 1.0e-9);
    }
}
