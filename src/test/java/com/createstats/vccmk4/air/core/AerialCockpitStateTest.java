package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialCockpitStateTest {
    private static AerialCockpitState flying() {
        return AerialCockpitState.of(true, true, true, AerialPilot.Mode.HELM, LeaveMode.KEEP_COURSE, 0.25, 0.2, -0.6,
                0.45, -0.3, 142.375, 150.0, -1.25, 6.75, 271.5, 90.0, 3.25, -8.5, -4.0, 8.0);
    }

    @Test
    void everyFieldSurvivesTheWire() {
        AerialCockpitState s = flying();
        byte[] wire = s.encode();
        assertEquals(AerialCockpitState.BYTES, wire.length, "the length the decoder insists on");
        AerialCockpitState back = AerialCockpitState.decode(wire);
        assertEquals(s, back, "every quantised field, the flags included");
        assertTrue(back.live() && back.piloted() && back.onCraft() && back.caution());
        assertEquals(AerialPilot.Mode.HELM, back.modeValue());
        assertEquals(LeaveMode.KEEP_COURSE, back.leaveValue());
        assertEquals(0.25, back.order(), 1.0e-12, "a telegraph notch lands on a whole step");
        assertEquals(0.2, back.engine(), 1.0 / 240.0);
        assertEquals(-0.6, back.turn(), 1.0 / 254.0);
        assertEquals(0.45, back.climb(), 1.0 / 254.0);
        assertEquals(-0.3, back.trim(), 1.0 / 254.0);
        assertEquals(142.375, back.altitude(), 1.0e-12, "eighths of a block");
        assertEquals(150.0, back.held(), 1.0e-12);
        assertEquals(-1.25, back.climbRate(), 1.0e-12, "sixteenths of a block a second");
        assertEquals(6.75, back.air(), 1.0e-12);
        assertEquals(271.5, back.heading(), 1.0e-12, "hundredths of a degree");
        assertEquals(90.0, back.heldHeading(), 1.0e-12);
        assertEquals(3.25, back.pitch(), 1.0e-12, "quarter degrees");
        assertEquals(-8.5, back.roll(), 1.0e-12);
        assertEquals(-4.0, back.yawRate(), 1.0e-12);
        assertEquals(8, back.feelTurn());
    }

    @Test
    void everyNotchOfTheTelegraphIsExactOnTheWire() {
        for (double notch : new double[]{-1.0, -0.5, -0.25, 0.0, 0.25, 0.5, 1.0}) {
            AerialCockpitState s = AerialCockpitState.of(false, true, false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, notch,
                    notch, 0, 0, 0, 64, Double.NaN, 0, 0, 0, Double.NaN, 0, 0, 0, 8);
            assertEquals(notch, AerialCockpitState.decode(s.encode()).order(), 0.0, "notch " + notch);
        }
    }

    @Test
    void aReadingThatIsNoNumberIsSentAsItsRestAndNothingHeldIsSentAsNothingHeld() {
        AerialCockpitState s = AerialCockpitState.of(false, false, false, AerialPilot.Mode.OFF, LeaveMode.ANCHOR,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        AerialCockpitState back = AerialCockpitState.decode(s.encode());
        assertEquals(0.0, back.order());
        assertEquals(0.0, back.altitude());
        assertTrue(Double.isNaN(back.held()), "no height held: the altimeter's bug stays where it was");
        assertFalse(back.has(AerialCockpitState.HELD_ALT));
        assertTrue(Double.isNaN(back.heldHeading()), "no heading held: the heading bug is not drawn");
        assertEquals(8, back.feelTurn(), "an unknown Feel reads as the standard one");
    }

    @Test
    void aHeadingWrapsOntoTheCompassAndANegativeHeightStaysNegative() {
        AerialCockpitState west = AerialCockpitState.of(false, true, false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0,
                0, 0, 0, -59.5, -40.0, 0, 0, -10.0, 370.0, 0, 0, 0, 8);
        AerialCockpitState back = AerialCockpitState.decode(west.encode());
        assertEquals(350.0, back.heading(), 1.0e-9);
        assertEquals(10.0, back.heldHeading(), 1.0e-9);
        assertEquals(-59.5, back.altitude(), 1.0e-9, "a ship under sea level in a deep world");
        assertEquals(-40.0, back.held(), 1.0e-9);
        AerialCockpitState north = AerialCockpitState.of(false, true, false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 0, 0,
                0, 0, 0, 0, Double.NaN, 0, 0, 359.999, Double.NaN, 0, 0, 0, 8);
        assertEquals(0.0, AerialCockpitState.decode(north.encode()).heading(), 1.0e-9, "359.999 rounds to north, not 360");
    }

    @Test
    void readingsPastTheirRangeStopAtItsEndInsteadOfWrapping() {
        AerialCockpitState s = AerialCockpitState.of(false, true, false, AerialPilot.Mode.HELM, LeaveMode.ANCHOR, 3.0, -3.0,
                9.0, -9.0, 2.0, 99999.0, -99999.0, 40.0, -40.0, 0, Double.NaN, 90.0, -90.0, 200.0, 500.0);
        AerialCockpitState back = AerialCockpitState.decode(s.encode());
        assertEquals(1.0, back.order(), 0.0);
        assertEquals(-1.0, back.engine(), 0.0);
        assertEquals(1.0, back.turn(), 0.0);
        assertEquals(-1.0, back.climb(), 0.0);
        assertEquals(1.0, back.trim(), 0.0);
        assertTrue(back.altitude() > 4000.0, "pinned at the top, not wrapped round to a negative height");
        assertTrue(back.held() < -4000.0);
        assertTrue(back.climbRate() > 7.0 && back.air() < -15.0);
        assertTrue(back.pitch() > 31.0 && back.roll() < -31.0 && back.yawRate() > 31.0);
        assertEquals(127, back.feelTurn());
    }

    @Test
    void aShipAtRestWhoseReadingsJitterSendsNothing() {
        AerialCockpitState a = AerialCockpitState.of(false, true, false, AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR, 0, 0,
                0.0001, 0.0002, 0, 120.0001, 120.0, 0.0003, 0.0004, 45.0001, Double.NaN, 0.01, -0.01, 0.02, 8);
        AerialCockpitState b = AerialCockpitState.of(false, true, false, AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR, 0, 0,
                -0.0001, 0.0, 0, 119.9999, 120.0, -0.0002, 0.0, 44.9999, Double.NaN, -0.01, 0.01, -0.02, 8);
        assertEquals(a, b, "a step smaller than the wire's is no change, so the server sends nothing");
        AerialCockpitState c = AerialCockpitState.of(false, true, false, AerialPilot.Mode.ANCHOR, LeaveMode.ANCHOR, 0, 0,
                0, 0, 0, 120.25, 120.0, 0, 0, 45.0, Double.NaN, 0, 0, 0, 8);
        assertNotEquals(a, c, "a quarter of a block is");
    }

    @Test
    void aShortOrMissingStateIsAnIdleConsoleNeverAnException() {
        assertSame(AerialCockpitState.IDLE, AerialCockpitState.decode(null));
        assertSame(AerialCockpitState.IDLE, AerialCockpitState.decode(new byte[3]));
        assertFalse(AerialCockpitState.IDLE.live(), "an idle console has heard nothing: no lamp lights on it");
        byte[] junk = new byte[AerialCockpitState.BYTES];
        java.util.Arrays.fill(junk, (byte) 0xFF);
        AerialCockpitState j = AerialCockpitState.decode(junk);
        assertTrue(j.mode() < AerialPilot.Mode.values().length && j.leave() < LeaveMode.values().length,
                "a mode or leave mode past the last one is clamped, so modeValue() cannot throw");
        assertTrue(j.heading() < 360.0);
    }

    @Test
    void theModeAndTheLeaveModeShareOneByte() {
        assertTrue(AerialPilot.Mode.values().length <= 16, "the mode is the byte's low four bits");
        assertTrue(LeaveMode.values().length <= 16, "the leave mode its high four");
        for (AerialPilot.Mode m : AerialPilot.Mode.values()) {
            for (LeaveMode l : LeaveMode.values()) {
                AerialCockpitState s = AerialCockpitState.of(false, true, false, m, l, 0, 0, 0, 0, 0, 0, Double.NaN, 0,
                        0, 0, Double.NaN, 0, 0, 0, 8);
                AerialCockpitState back = AerialCockpitState.decode(s.encode());
                assertEquals(m, back.modeValue());
                assertEquals(l, back.leaveValue());
            }
        }
    }
}
