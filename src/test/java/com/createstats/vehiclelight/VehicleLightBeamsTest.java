package com.createstats.vehiclelight;

import com.createstats.dynlight.BeamLight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleLightBeamsTest {
    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    @Test
    void aLevelHeadlightIsAimedALittleDownTheDippedBeamMoreThanTheFull() {
        final double[] dipped = VehicleLightBeams.aim(0, 0, -1, VehicleLightGlow.DIM);
        final double[] full = VehicleLightBeams.aim(0, 0, -1, VehicleLightGlow.BRIGHT);
        assertEquals(1.0, length(dipped), 1e-9);
        assertEquals(1.0, length(full), 1e-9);
        assertEquals(-Math.sin(Math.toRadians(VehicleLightBeams.DIPPED_DOWN)), dipped[1], 1e-9);
        assertEquals(-Math.sin(Math.toRadians(VehicleLightBeams.FULL_DOWN)), full[1], 1e-9);
        assertTrue(dipped[1] < full[1], "the dipped beam points lower");
        assertTrue(dipped[2] < 0 && full[2] < 0, "and both still forward");
        assertEquals(0.0, dipped[0], 1e-12, "no sideways drift");
    }

    @Test
    void aLampPointingUpOrDownIsNotTipped() {
        assertArrayEquals(new double[] {0, 1, 0}, VehicleLightBeams.aim(0, 1, 0, VehicleLightGlow.DIM), 1e-12);
        assertArrayEquals(new double[] {0, -1, 0}, VehicleLightBeams.aim(0, -1, 0, VehicleLightGlow.BRIGHT), 1e-12);
    }

    @Test
    void theLevelledTipLandsThePoolAtTheSameDistanceFromAnyHeight() {
        for (double h : new double[] {0.5, 1.0, 1.5, 2.5, 3.0}) {
            final double dipped = VehicleLightBeams.tipFor(h, VehicleLightGlow.DIM);
            final double full = VehicleLightBeams.tipFor(h, VehicleLightGlow.BRIGHT);
            assertEquals(VehicleLightBeams.DIPPED_LANDS, h / Math.tan(Math.toRadians(dipped)), 1e-9, "dipped from " + h);
            assertEquals(VehicleLightBeams.FULL_LANDS, h / Math.tan(Math.toRadians(full)), 1e-9, "full from " + h);
            assertTrue(dipped > full, "the dipped beam is always the steeper");
        }

        assertEquals(VehicleLightBeams.DIPPED_DOWN, VehicleLightBeams.tipFor(1.5, VehicleLightGlow.DIM), 0.5);
        assertEquals(VehicleLightBeams.FULL_DOWN, VehicleLightBeams.tipFor(1.5, VehicleLightGlow.BRIGHT), 0.1);
    }

    @Test
    void anUnknownHeightIsTheRestingTipAndNoHeightTipsTheBeamIntoTheGround() {
        assertEquals(VehicleLightBeams.DIPPED_DOWN, VehicleLightBeams.tipFor(Double.NaN, VehicleLightGlow.DIM), 1e-12);
        assertEquals(VehicleLightBeams.FULL_DOWN, VehicleLightBeams.tipFor(0.0, VehicleLightGlow.BRIGHT), 1e-12);
        assertEquals(VehicleLightBeams.MAX_TIP, VehicleLightBeams.tipFor(40.0, VehicleLightGlow.DIM), 1e-12);
        assertEquals(VehicleLightBeams.MIN_TIP, VehicleLightBeams.tipFor(0.01, VehicleLightGlow.BRIGHT), 1e-12);
    }

    @Test
    void leftIsAnticlockwiseSeenFromAboveWhicheverWayTheLampFaces() {
        assertArrayEquals(new double[] {-1, 0, 0}, VehicleLightBeams.aim(0, 0, -1, 0.0, 90.0), 1e-12);
        assertArrayEquals(new double[] {1, 0, 0}, VehicleLightBeams.aim(0, 0, 1, 0.0, 90.0), 1e-12);
        assertArrayEquals(new double[] {0, 0, -1}, VehicleLightBeams.aim(1, 0, 0, 0.0, 90.0), 1e-12);
        final double[] tipped = VehicleLightBeams.aim(0, 0, -1, 9.0, -15.0);
        assertEquals(1.0, length(tipped), 1e-12);
        assertEquals(-Math.sin(Math.toRadians(9.0)), tipped[1], 1e-12, "turning does not change the tip");
        assertTrue(tipped[0] > 0, "negative left is right: east, facing north");
        assertArrayEquals(VehicleLightBeams.aim(0, 0, -1, VehicleLightGlow.DIM),
                VehicleLightBeams.aim(0, 0, -1, VehicleLightBeams.DIPPED_DOWN, 0.0), 1e-12, "the old aim is the resting tip");
    }

    @Test
    void theAimsPartsAddUp() {
        final double[] rest = VehicleLightBeams.angles(9.0, 3.0, HailSequence.Aim.LEVELLED, 0.0);
        assertArrayEquals(new double[] {3.0, 0.0}, rest, 1e-12, "no show: the levelled tip, straight ahead");
        final double[] run = VehicleLightBeams.angles(9.0, 3.0, new HailSequence.Aim(6.0, 15.0, 0.0), 0.0);
        assertArrayEquals(new double[] {15.0, 15.0}, run, 1e-12, "the run swings from the resting tip");
        final double[] bend = VehicleLightBeams.angles(9.0, 3.0, HailSequence.Aim.LEVELLED, -12.0);
        assertArrayEquals(new double[] {3.0, -12.0}, bend, 1e-12, "a bend to the right");
    }

    @Test
    void theFullBeamReachesFurtherAndBrighterThanTheDipped() {
        final double[] pose = {0.5, 65.5, 0.5, 0, 0, -1};
        final BeamLight dipped = VehicleLightBeams.headlight(pose, VehicleLightGlow.DIM);
        final BeamLight full = VehicleLightBeams.headlight(pose, VehicleLightGlow.BRIGHT);
        assertTrue(dipped.isValid() && full.isValid());
        assertTrue(full.reach() > dipped.reach());
        assertTrue(full.level() > dipped.level());
        assertTrue(full.levelAt(12) >= dipped.levelAt(12), "at the full beam's pool it is at least as bright");
    }

    @Test
    void theBeamsReachTwentyEightAndFortyFourAndDimFourUnderTheDrawnLight() {
        final double[] pose = {0.5, 65.5, 0.5, 0, 0, -1};
        final BeamLight dipped = VehicleLightBeams.headlight(pose, VehicleLightGlow.DIM);
        final BeamLight full = VehicleLightBeams.headlight(pose, VehicleLightGlow.BRIGHT);
        assertEquals(28.0, dipped.reach(), 1e-12);
        assertEquals(10.0, dipped.fullDistance(), 1e-12);
        assertEquals(28.0, dipped.fadeDistance(), 1e-12);
        assertEquals(3, dipped.farLevel());
        assertEquals(12, dipped.level());
        assertEquals(44.0, full.reach(), 1e-12);
        assertEquals(18.0, full.fullDistance(), 1e-12);
        assertEquals(44.0, full.fadeDistance(), 1e-12);
        assertEquals(4, full.farLevel());
        assertEquals(14, full.level());
        assertEquals(3.5, dipped.maxRadius(), 1e-12, "the pool numbers did not move");
        assertEquals(5.0, full.maxRadius(), 1e-12);

        final BeamLight under = VehicleLightBeams.headlight(pose, VehicleLightGlow.DIM, VehicleLightBeams.UNDERLAY_DIM);
        final BeamLight underFull = VehicleLightBeams.headlight(pose, VehicleLightGlow.BRIGHT, VehicleLightBeams.UNDERLAY_DIM);
        assertEquals(8, under.level(), "four levels down");
        assertEquals(10, underFull.level());
        assertEquals(0, under.farLevel(), "the taper's far level goes down with it, to a floor of nothing");
        assertEquals(0, underFull.farLevel());
        assertEquals(dipped.reach(), under.reach(), 1e-12, "and ends where the drawn light ends");
        assertEquals(dipped.spill(), under.spill(), "the glow round the lens is not the beam");
        assertEquals(dipped.level(), VehicleLightBeams.headlight(pose, VehicleLightGlow.DIM, -3).level(),
                "a negative dim is no dim");
    }
}
