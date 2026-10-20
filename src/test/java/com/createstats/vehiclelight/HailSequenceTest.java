package com.createstats.vehiclelight;

import com.createstats.vccmk4.core.LinkSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HailSequenceTest {
    private static final HailSequence.Place MIDDLE = HailSequence.Place.MIDDLE;
    private static final HailSequence.Place EDGE = new HailSequence.Place(1.0, 1.0);

    private static double lens(HailSequence.Role role, double t, HailSequence.Place place) {
        return HailSequence.lens(role, t, place, 0.55, false);
    }

    @Test
    void theShowLinesUpWithTheDriverHelmetsBoot() {
        assertEquals(com.createstats.driverhelmet.HudBoot.START, HailSequence.DARK, 1e-9);
        assertEquals(com.createstats.driverhelmet.HudBoot.START + com.createstats.driverhelmet.HudBoot.SELF_TEST,
                HailSequence.CHECK_END, 1e-9);
        assertEquals(com.createstats.driverhelmet.HudBoot.WELCOME_MAX_SPEED, HailSequence.MAX_SPEED, 1e-9,
                "the same standing still as the helmet's welcome");
        assertTrue(HailSequence.END < 3.0, "a greeting, not a wait: " + HailSequence.END);
    }

    @Test
    void everyLampIsDarkThenLightsFromTheMiddleOutThenHolds() {
        for (HailSequence.Role role : HailSequence.Role.values()) {
            assertEquals(0.0, lens(role, 0.1, MIDDLE), 1e-12, role + " is dark while the helm is taken");
        }
        final double early = HailSequence.DARK + 0.05;
        assertEquals(1.0, lens(HailSequence.Role.TAIL, early, MIDDLE), 1e-12, "the middle lamp lights first");
        assertEquals(0.0, lens(HailSequence.Role.TAIL, early, EDGE), 1e-12, "the farthest one not yet");
        assertEquals(1.0, lens(HailSequence.Role.TAIL, HailSequence.DARK + HailSequence.CHECK_SPREAD + 0.01, EDGE), 1e-12,
                "and by the end of the wave every lamp is lit");
        assertEquals(1.0, lens(HailSequence.Role.INDICATOR, 0.6, EDGE), 1e-12);
        assertEquals(1.0, lens(HailSequence.Role.REVERSE, 0.6, EDGE), 1e-12);
        assertEquals(0.55, lens(HailSequence.Role.HEADLIGHT, 0.6, EDGE), 1e-12, "a headlight comes on at its own beam");
    }

    @Test
    void theIndicatorsBlinkTwiceTogetherThenAreTheirOwnAgain() {
        int flashes = 0;
        boolean on = false;
        for (double t = HailSequence.CHECK_END; t < HailSequence.END; t += 0.01) {
            final double v = lens(HailSequence.Role.INDICATOR, t, EDGE);
            final boolean now = !Double.isNaN(v) && v > 0.5;
            if (now && !on) {
                flashes++;
            }
            on = now;
        }
        assertEquals(2, flashes, "an unlock's double blink");
        assertTrue(Double.isNaN(lens(HailSequence.Role.INDICATOR, 1.5, EDGE)), "then the indicator is its own again");
    }

    @Test
    void theTailLightsFallFromBrakeToTailThenAScannerRunsLeftToRightAndBack() {
        assertEquals(1.0, lens(HailSequence.Role.TAIL, HailSequence.CHECK_END, MIDDLE), 1e-9, "brake brightness first");
        assertEquals(HailSequence.TAIL_LEVEL, lens(HailSequence.Role.TAIL, HailSequence.TAIL_FALL_END - 1e-9, MIDDLE), 1e-6,
                "down to the tail light");
        final HailSequence.Place left = new HailSequence.Place(1.0, -1.0);
        final HailSequence.Place right = new HailSequence.Place(1.0, 1.0);
        final double start = HailSequence.TAIL_FALL_END + 0.01;
        final double middle = (HailSequence.TAIL_FALL_END + HailSequence.RUN_END) / 2.0;
        assertTrue(lens(HailSequence.Role.TAIL, start, left) > lens(HailSequence.Role.TAIL, start, right), "it starts on the left");
        assertTrue(lens(HailSequence.Role.TAIL, middle, right) > lens(HailSequence.Role.TAIL, middle, left), "reaches the right");
        assertEquals(1.0, lens(HailSequence.Role.TAIL, middle, right), 1e-9);
        final double end = HailSequence.RUN_END - 0.01;
        assertTrue(lens(HailSequence.Role.TAIL, end, left) > lens(HailSequence.Role.TAIL, end, right), "and comes back");
        for (double t = HailSequence.TAIL_FALL_END; t < HailSequence.END; t += 0.01) {
            assertTrue(lens(HailSequence.Role.TAIL, t, MIDDLE) >= HailSequence.TAIL_LEVEL - 1e-9, "never below the tail light");
        }

        assertEquals(com.createstats.driverhelmet.LedBar.SWEEP_PASS, (HailSequence.RUN_END - HailSequence.TAIL_FALL_END) / 2.0, 1e-9);
    }

    @Test
    void theHeadlightsRunDownUpLeftRightAndSettleOntoTheirLevelledTilt() {
        assertEquals(HailSequence.Aim.LEVELLED, HailSequence.aim(Double.NaN), "no show: levelled");
        assertEquals(0.0, HailSequence.aim(0.5).settle(), 1e-12, "the bulb check is at the resting tip");
        assertEquals(HailSequence.RUN_DOWN, HailSequence.aim(0.95).down(), 1e-9, "down");
        assertEquals(-HailSequence.RUN_UP, HailSequence.aim(1.25).down(), 1e-9, "up");
        assertEquals(HailSequence.RUN_SIDE, HailSequence.aim(1.55).left(), 1e-9, "left");
        assertEquals(-HailSequence.RUN_SIDE, HailSequence.aim(1.95).left(), 1e-9, "right");
        final HailSequence.Aim back = HailSequence.aim(HailSequence.RUN_END);
        assertEquals(0.0, back.down(), 1e-9);
        assertEquals(0.0, back.left(), 1e-9, "back in the middle");
        double most = 0.0;
        HailSequence.Aim last = HailSequence.aim(0.0);
        for (double t = 0.0; t < HailSequence.END; t += 0.005) {
            final HailSequence.Aim a = HailSequence.aim(t);

            assertTrue(Math.abs(a.down() - last.down()) < 1.0 && Math.abs(a.left() - last.left()) < 1.5
                    && Math.abs(a.settle() - last.settle()) < 0.05, "smooth at " + t);
            most = Math.max(most, a.settle());
            last = a;
        }
        assertEquals(1.0 + HailSequence.OVERSHOOT, most, 0.01, "a servo's little overshoot");
        assertEquals(1.0, HailSequence.aim(HailSequence.END - 1e-6).settle(), 1e-3, "and it comes to rest on the level");
        assertEquals(HailSequence.Aim.LEVELLED, HailSequence.aim(HailSequence.END));
    }

    @Test
    void aRealSignalAlwaysShowsThroughExceptTheBrakeLightOfAVehicleHeldAtRest() {
        final double t = HailSequence.DARK + 0.3;
        assertTrue(Double.isNaN(HailSequence.lens(HailSequence.Role.INDICATOR, t, EDGE, 1.0, true)), "an indicator in use");
        assertTrue(Double.isNaN(HailSequence.lens(HailSequence.Role.REVERSE, t, EDGE, 0.55, true)), "a reverse light lit");
        assertEquals(1.0, HailSequence.lens(HailSequence.Role.TAIL, t, EDGE, 1.0, true), 1e-12,
                "a vehicle at rest holds its brakes: its brake light is not a signal to anyone standing still");
        assertTrue(HailSequence.signalling(HailSequence.Role.INDICATOR, VehicleLightGlow.DIM), "a signal held on a craft is on");
        assertFalse(HailSequence.signalling(HailSequence.Role.INDICATOR, VehicleLightGlow.OFF));
        assertFalse(HailSequence.signalling(HailSequence.Role.TAIL, VehicleLightGlow.BRIGHT));
        assertFalse(HailSequence.signalling(HailSequence.Role.HEADLIGHT, VehicleLightGlow.BRIGHT));
    }

    @Test
    void eachLampsPartComesFromItsJobAndALampSetOffHasNone() {
        assertEquals(HailSequence.Role.HEADLIGHT, HailSequence.Role.of(LinkSource.HEADLIGHT));
        assertEquals(HailSequence.Role.HEADLIGHT, HailSequence.Role.of(LinkSource.DRIVING));
        assertEquals(HailSequence.Role.REVERSE, HailSequence.Role.of(LinkSource.REVERSE));
        assertEquals(HailSequence.Role.TAIL, HailSequence.Role.of(LinkSource.TAIL_BRAKE));
        assertEquals(HailSequence.Role.INDICATOR, HailSequence.Role.of(LinkSource.RIGHT));
        assertEquals(HailSequence.Role.OTHER, HailSequence.Role.of(LinkSource.AUX_B_PLUS));
        assertNull(HailSequence.Role.of(LinkSource.OFF), "Off: the console does not drive it, nor greet with it");
        assertNull(HailSequence.Role.byOrdinal(99));
    }

    @Test
    void placesAreMeasuredFromTheMiddleOfTheLamps() {
        final HailSequence.Place[] p = HailSequence.place(new double[] {-1, 1, -1, 1, 0}, new double[] {3, 3, -3, -3, -3});
        assertEquals(-1.0, p[0].lateral(), 1e-12);
        assertEquals(1.0, p[1].lateral(), 1e-12);
        assertEquals(0.0, p[4].lateral(), 1e-12);
        assertEquals(1.0, p[0].radial(), 1e-12, "a corner is the farthest");
        assertEquals(3.0 / Math.hypot(1, 3), p[4].radial(), 1e-12);
        final HailSequence.Place[] one = HailSequence.place(new double[] {5}, new double[] {-2});
        assertEquals(HailSequence.Place.MIDDLE, one[0], "a lone lamp is the middle");
    }

    @Test
    void theLensIsTheLampsOwnOutsideTheShowAndAnAmberSignalOnACraftFlashes() {
        final HailSequence.Hail hail = new HailSequence.Hail(1000L, HailSequence.Role.TAIL, MIDDLE);
        final LampShow.Lens before = LampShow.lens(VehicleLightKind.RED, VehicleLightGlow.DIM, 990L, 0.0f, hail, false);
        assertFalse(before.hailing());
        assertEquals(VehicleLightGlow.DIM.level(), before.level(), 1e-9, "before the show: the tail light");
        final LampShow.Lens dark = LampShow.lens(VehicleLightKind.RED, VehicleLightGlow.BRIGHT, 1002L, 0.0f, hail, false);
        assertTrue(dark.hailing());
        assertEquals(0.0, dark.level(), 1e-9, "the show's dark beat");
        assertFalse(dark.lit());
        final LampShow.Lens cut = LampShow.lens(VehicleLightKind.RED, VehicleLightGlow.BRIGHT, 1002L, 0.0f, hail, true);
        assertEquals(1.0, cut.level(), 1e-9, "cut short: the lamp's own again at once");
        final LampShow.Lens after = LampShow.lens(VehicleLightKind.RED, VehicleLightGlow.BRIGHT, 1000L + 60L, 0.0f, hail, false);
        assertFalse(after.hailing(), "three seconds on the show is over");
        assertEquals(8, after.emission(VehicleLightKind.RED), "the brake light's own light");

        int on = 0;
        for (long t = 0; t < 32; t++) {
            final LampShow.Lens signal = LampShow.lens(VehicleLightKind.AMBER, VehicleLightGlow.DIM, t, 0.0f, null, false);
            if (signal.lit()) {
                on++;
                assertEquals(7, signal.emission(VehicleLightKind.AMBER), "a flash's light");
            } else {
                assertEquals(0, signal.emission(VehicleLightKind.AMBER));
            }
        }
        assertEquals(16, on, "held on a craft, flashed half the time by the client");
    }
}
