package com.createstats.timeswitch;

import com.createstats.timeswitch.TimeSwitchDial.Aim;
import com.createstats.timeswitch.TimeSwitchDial.Kind;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSwitchDialTest {
    private static final double R_PIN = (TimeSwitchDial.OUT_R0 + TimeSwitchDial.SLOT_R1) / 2.0;

    private static double[] at(double viewDegrees, double r) {
        final double a = Math.toRadians(viewDegrees);

        return new double[] {TimeSwitchDial.CU - r * Math.sin(a), TimeSwitchDial.CV + r * Math.cos(a)};
    }

    private static int pinAtView(double viewDegrees, double hourF) {
        final double[] p = at(viewDegrees, R_PIN);
        return TimeSwitchDial.pinAt(p[0], p[1], TimeSwitchDial.dialDegrees(hourF));
    }

    @Test
    void atMidnightTheDialIsUnturnedAndMidnightIsAtTheIndex() {
        assertEquals(0.0, TimeSwitchDial.dialDegrees(0.0), 1e-12);
        assertEquals(0, pinAtView(0.5, 0.0), "just clockwise of the index is the 00:00 pin");
        assertEquals(23, pinAtView(-0.5, 0.0), "just counterclockwise is the 23:00 pin");
    }

    @Test
    void thePinUnderTheIndexIsAlwaysTheHourItIsNow() {
        for (int h = 0; h < 24; h++) {
            for (double f : new double[] {0.0, 0.01, 0.25, 0.5, 0.75, 0.99}) {
                assertEquals(h, pinAtView(0.001, h + f), "hour " + (h + f));
            }
        }
    }

    @Test
    void theNextHourIsClockwiseOfTheIndexAndTheLastOneCounterclockwise() {
        for (int h = 0; h < 24; h++) {
            final double dial = TimeSwitchDial.dialDegrees(h + 0.5);
            assertEquals(0.0, TimeSwitchDial.pinViewAngle(h, dial), 1e-9, "pin " + h + " centred at half past");
            assertEquals(15.0, TimeSwitchDial.pinViewAngle(h + 1, dial), 1e-9);
            assertEquals(345.0, TimeSwitchDial.pinViewAngle(h - 1, dial), 1e-9);
            assertEquals((h + 1) % 24, pinAtView(15.0, h + 0.5));
            assertEquals((h + 23) % 24, pinAtView(-15.0, h + 0.5));
        }
    }

    @Test
    void theDialTurnsCounterclockwiseAWholeTurnADay() {
        assertTrue(TimeSwitchDial.dialDegrees(1.0) < TimeSwitchDial.dialDegrees(0.0));
        assertEquals(-90.0, TimeSwitchDial.dialDegrees(6.0), 1e-12);
        assertEquals(-360.0, TimeSwitchDial.dialDegrees(24.0), 1e-12);
    }

    @Test
    void pointsOffTheSlotRingAreNoPin() {
        final double dial = TimeSwitchDial.dialDegrees(12.0);
        final double[] inner = at(10.0, TimeSwitchDial.SLOT_R0 - 0.5);
        final double[] outer = at(10.0, TimeSwitchDial.SLOT_R1 + 0.5);
        assertEquals(-1, TimeSwitchDial.pinAt(inner[0], inner[1], dial));
        assertEquals(-1, TimeSwitchDial.pinAt(outer[0], outer[1], dial));
        assertEquals(-1, TimeSwitchDial.pinAt(TimeSwitchDial.CU, TimeSwitchDial.CV, dial));
    }

    @Test
    void aSweepTakesEveryPinBetweenTheShortWayRound() {
        final int midnight = TimeSwitchDial.sweep(22, 2);
        assertEquals(TimeSwitchDial.sweep(2, 22), midnight, "either direction, the same pins");
        for (int h : new int[] {22, 23, 0, 1, 2}) {
            assertTrue(TimeSwitchProgram.isIn(midnight, h), "pin " + h);
        }
        assertEquals(5, TimeSwitchProgram.count(midnight));
        assertEquals(TimeSwitchProgram.bit(5), TimeSwitchDial.sweep(5, 5));
        assertEquals(TimeSwitchProgram.bit(5) | TimeSwitchProgram.bit(6), TimeSwitchDial.sweep(5, 6));
    }

    @Test
    void halfATurnApartIsATieBrokenTowardRisingHours() {
        final int m = TimeSwitchDial.sweep(0, 12);
        assertEquals(13, TimeSwitchProgram.count(m));
        assertTrue(TimeSwitchProgram.isIn(m, 6) && !TimeSwitchProgram.isIn(m, 18));
        final int back = TimeSwitchDial.sweep(12, 0);
        assertTrue(TimeSwitchProgram.isIn(back, 18) && !TimeSwitchProgram.isIn(back, 6));
    }

    @Test
    void theSlotSplitsHalfwayBetweenDetents() {
        assertEquals(Mode.ON, TimeSwitchDial.modeAt(TimeSwitchDial.detentV(Mode.ON)));
        assertEquals(Mode.AUTO, TimeSwitchDial.modeAt(TimeSwitchDial.detentV(Mode.AUTO)));
        assertEquals(Mode.OFF, TimeSwitchDial.modeAt(TimeSwitchDial.detentV(Mode.OFF)));
        final double onAuto = (TimeSwitchDial.detentV(Mode.ON) + TimeSwitchDial.detentV(Mode.AUTO)) / 2.0;
        assertEquals(Mode.ON, TimeSwitchDial.modeAt(onAuto));
        assertEquals(Mode.AUTO, TimeSwitchDial.modeAt(onAuto - 1e-6));
        assertTrue(TimeSwitchDial.detentV(Mode.ON) > TimeSwitchDial.detentV(Mode.AUTO)
                && TimeSwitchDial.detentV(Mode.AUTO) > TimeSwitchDial.detentV(Mode.OFF), "I at the top, 0 at the bottom");
    }

    private static Aim straightAt(double u, double v, double hourF, Mode mode) {
        return TimeSwitchDial.aim(u, 8.0, v, 0.0, -1.0, 0.0, TimeSwitchDial.dialDegrees(hourF), mode);
    }

    @Test
    void straightOnEachPartIsWhatIsUnderTheCrosshair() {
        for (int h = 0; h < 24; h++) {
            final double[] p = at(TimeSwitchDial.pinViewAngle(h, TimeSwitchDial.dialDegrees(20.3)), R_PIN);
            final Aim aim = straightAt(p[0], p[1], 20.3, Mode.AUTO);
            assertEquals(Kind.PIN, aim.kind(), "pin " + h);
            assertEquals(h, aim.index(), "pin " + h);
        }
        assertEquals(Kind.HUB, straightAt(TimeSwitchDial.CU, TimeSwitchDial.CV, 3.0, Mode.AUTO).kind());
        final double slotU = (TimeSwitchDial.SLIDE_U0 + TimeSwitchDial.SLIDE_U1) / 2.0;
        final Aim knob = straightAt(slotU, TimeSwitchDial.detentV(Mode.AUTO), 3.0, Mode.AUTO);
        assertEquals(Kind.MODE, knob.kind());
        assertEquals(Mode.AUTO.id(), knob.index(), "the knob answers for its own detent");
        final Aim on = straightAt(slotU, TimeSwitchDial.detentV(Mode.ON), 3.0, Mode.AUTO);
        assertEquals(Mode.ON.id(), on.index(), "the empty I end of the slot asks for On");
        final Aim off = straightAt(slotU, TimeSwitchDial.detentV(Mode.OFF), 3.0, Mode.AUTO);
        assertEquals(Mode.OFF.id(), off.index());
        assertEquals(Kind.NONE, straightAt(1.5, 1.5, 3.0, Mode.AUTO).kind(), "the plate's corner");
    }

    @Test
    void anObliqueRayLandsOnThePinItPointsAtWhereTheOutlineHitWouldNot() {
        final double hourF = 20.5;
        final double[] target = at(0.0, R_PIN);
        final double du = Math.sin(Math.toRadians(60.0));
        final double dh = -Math.cos(Math.toRadians(60.0));
        final double back = 10.0;
        final double ou = target[0] - du * back;
        final double oh = TimeSwitchDial.PIN_TOP_H - dh * back;
        final Aim aim = TimeSwitchDial.aim(ou, oh, target[1], du, dh, 0.0, TimeSwitchDial.dialDegrees(hourF), Mode.AUTO);
        assertEquals(Kind.PIN, aim.kind());
        assertEquals(20, aim.index());

        final double t = (2.8 - oh) / dh;
        final int naive = TimeSwitchDial.pinAt(ou + du * t, target[1], TimeSwitchDial.dialDegrees(hourF));
        assertNotEquals(20, naive, "the outline hit point should land on a neighbour");
    }

    @Test
    void aRayThatIsNotHeadingIntoTheWallMeetsNothing() {
        assertEquals(Kind.NONE, TimeSwitchDial.aim(8, 4, 8, 0, 1, 0, 0.0, Mode.AUTO).kind());
        assertEquals(Kind.NONE, TimeSwitchDial.aim(8, 4, 8, 1, 0, 0, 0.0, Mode.AUTO).kind());
    }

    @Test
    void theKnobStandingInFrontOfTheSlotTakesAGrazingRay() {
        final double slotU = (TimeSwitchDial.SLIDE_U0 + TimeSwitchDial.SLIDE_U1) / 2.0;
        final double dv = (9.5 - 10.5) / (TimeSwitchDial.SLIDE_FLOOR_H - TimeSwitchDial.KNOB_H1);
        final double back = 3.3;
        final Aim aim = TimeSwitchDial.aim(slotU, TimeSwitchDial.KNOB_H1 + back, 10.5 + dv * back,
                0.0, -1.0, -dv, 0.0, Mode.ON);
        assertEquals(Kind.MODE, aim.kind());
        assertEquals(Mode.ON.id(), aim.index(), "the knob, not the Auto zone behind it");
        assertEquals(Mode.AUTO, TimeSwitchDial.modeAt(9.5), "the zone the ray would have reached");
    }
}
