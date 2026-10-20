package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkLogicTest {
    private static LinkLogic.Signals sig(boolean braking, boolean reversing, double steer, double auxA, long tick) {
        return new LinkLogic.Signals(true, braking, reversing, false, steer, new double[]{auxA, 0, 0, 0}, tick);
    }

    @Test
    void statesLightTheirLamps() {
        assertTrue(LinkLogic.raw(LinkSource.BRAKE, sig(true, false, 0, 0, 0)));
        assertFalse(LinkLogic.raw(LinkSource.BRAKE, sig(false, false, 0, 0, 0)));
        assertTrue(LinkLogic.raw(LinkSource.REVERSE, sig(false, true, 0, 0, 0)));
        assertTrue(LinkLogic.raw(LinkSource.DRIVING, sig(false, false, 0, 0, 0)));
        assertFalse(LinkLogic.raw(LinkSource.OFF, sig(true, true, 1, 1, 0)));
    }

    @Test
    void indicatorsBlinkOnlyPastASmallCorrection() {
        int on = 0;
        for (long t = 0; t < 2 * LinkLogic.BLINK_TICKS; t++) if (LinkLogic.raw(LinkSource.LEFT, sig(false, false, -1, 0, t))) on++;
        assertEquals(LinkLogic.BLINK_TICKS, on, "half the time on");
        assertFalse(LinkLogic.raw(LinkSource.LEFT, sig(false, false, -0.1, 0, 0)), "a nudge is not a turn");
        assertFalse(LinkLogic.raw(LinkSource.LEFT, sig(false, false, 1, 0, 0)), "steering right does not flash the left");
        assertTrue(LinkLogic.raw(LinkSource.RIGHT, sig(false, false, 1, 0, 0)));
    }

    @Test
    void tailAndBrakeIsWeakWhileDrivingFullWhileBrakingAndDarkWhenParked() {
        LinkLogic.State red = new LinkLogic.State();
        assertEquals(LinkLogic.TAIL, red.step(LinkSource.TAIL_BRAKE, false, sig(false, false, 0, 0, 0)), "the tail light");
        assertEquals(LinkLogic.FULL, red.step(LinkSource.TAIL_BRAKE, false, sig(true, false, 0, 0, 1)), "the brake light");
        assertEquals(LinkLogic.TAIL, red.step(LinkSource.TAIL_BRAKE, false, sig(false, false, 0, 0, 2)), "back to the tail");
        assertEquals(0, red.step(LinkSource.TAIL_BRAKE, false, LinkLogic.Signals.NONE), "nobody driving, dark");
        assertEquals(0, red.step(LinkSource.TAIL_BRAKE, true, LinkLogic.Signals.NONE), "a toggle does not change that");
        assertTrue(LinkLogic.TAIL > 0 && LinkLogic.TAIL < LinkLogic.FULL, "weak but not nothing");
        assertEquals(Control.BRAKE, LinkSource.TAIL_BRAKE.shownUnder(), "listed under the brake on the console");
        assertEquals(15, LinkSource.TAIL_BRAKE.ordinal(),
                "appended in 1.14.67 and never moved: the snapshot and older saves index sources by ordinal and by key");
    }

    private static LinkLogic.Signals beam(boolean held, long tick) {
        return new LinkLogic.Signals(true, false, false, false, 0, new double[4], tick, held);
    }

    @Test
    void aHeadlightIsDippedWhileDrivingAndTheFullBeamKeyTogglesItsFullBeam() {
        LinkLogic.State lamp = new LinkLogic.State();
        assertEquals(LinkLogic.DIPPED, lamp.step(LinkSource.HEADLIGHT, true, beam(false, 0)), "dipped: nobody asked for more");
        assertEquals(LinkLogic.FULL, lamp.step(LinkSource.HEADLIGHT, true, beam(true, 1)), "the Full beam key: full");
        assertEquals(LinkLogic.FULL, lamp.step(LinkSource.HEADLIGHT, true, beam(false, 2)), "a toggle: it stays full");
        assertEquals(LinkLogic.DIPPED, lamp.step(LinkSource.HEADLIGHT, true, beam(true, 3)), "the next press dips it");
        assertEquals(0, lamp.step(LinkSource.HEADLIGHT, true, LinkLogic.Signals.NONE), "nobody drives: dark");
        assertEquals(LinkLogic.DIPPED, lamp.step(LinkSource.HEADLIGHT, true, beam(false, 5)),
                "and back dipped, not full, for the next driver");

        LinkLogic.State flash = new LinkLogic.State();
        assertEquals(LinkLogic.FULL, flash.step(LinkSource.HEADLIGHT, false, beam(true, 0)), "toggle off: full while held");
        assertEquals(LinkLogic.DIPPED, flash.step(LinkSource.HEADLIGHT, false, beam(false, 1)), "a flash, then dipped again");
        assertTrue(LinkLogic.DIPPED > 0 && LinkLogic.DIPPED < LinkLogic.FULL && LinkLogic.DIPPED != LinkLogic.TAIL,
                "weak, so the white lamp shows its dipped beam, and apart from the tail light on a comparator");
    }

    @Test
    void theHeadlightSourceIsAppendedAndListedUnderTheFullBeamKey() {
        assertEquals(LinkSource.values().length - 1, LinkSource.HEADLIGHT.ordinal(), "appended in 1.14.68");
        assertEquals(Control.BEAM, LinkSource.HEADLIGHT.shownUnder());
        assertTrue(LinkSource.HEADLIGHT.keyed(), "a key lights it, so it can be a toggle");
        assertEquals(LinkSource.HEADLIGHT, LinkSource.byKey("headlight"));
        assertTrue(LinkLogic.raw(LinkSource.HEADLIGHT, beam(true, 0)));
        assertFalse(LinkLogic.raw(LinkSource.HEADLIGHT, beam(false, 0)));
        assertFalse(LinkLogic.raw(LinkSource.AUX_A_PLUS, beam(true, 0)), "the Full beam key is not Aux A");
    }

    @Test
    void aWhiteReverseLightIsGivenTheDippedBeamAndNothingElseChanges() {
        assertEquals(LinkLogic.DIPPED, LinkLogic.forLamp(LinkSource.REVERSE, true, LinkLogic.FULL),
                "a reverse light lights the ground just behind, not the road twenty blocks back");
        assertEquals(0, LinkLogic.forLamp(LinkSource.REVERSE, true, 0), "off is off");
        assertEquals(LinkLogic.FULL, LinkLogic.forLamp(LinkSource.REVERSE, false, LinkLogic.FULL), "a red reverse light is bright");
        assertEquals(LinkLogic.FULL, LinkLogic.forLamp(LinkSource.DRIVING, true, LinkLogic.FULL), "a lamp put on Driving keeps full");
        assertEquals(LinkLogic.FULL, LinkLogic.forLamp(LinkSource.HEADLIGHT, true, LinkLogic.FULL));
        assertEquals(LinkLogic.TAIL, LinkLogic.forLamp(LinkSource.TAIL_BRAKE, false, LinkLogic.TAIL));
    }

    @Test
    void aLampThatFlashesByItselfIsGivenItsIndicatorSteady() {
        LinkLogic.State signal = new LinkLogic.State();
        for (long t = 0; t < 3 * LinkLogic.BLINK_TICKS; t++) {
            assertEquals(LinkLogic.FULL, signal.step(LinkSource.LEFT, false, sig(false, false, -1, 0, t), false, true),
                    "steady at tick " + t + ": the lamp times the flash");
        }
        assertEquals(0, signal.step(LinkSource.LEFT, false, sig(false, false, 1, 0, 30), false, true),
                "steering the other way is still off");
        assertEquals(0, signal.step(LinkSource.LEFT, false, sig(false, false, -1, 0, LinkLogic.BLINK_TICKS), false, false),
                "without steady the off half of the flash is off, as for a receiver");
    }

    @Test
    void auxKeysLightTheirOwnDirection() {
        assertTrue(LinkLogic.raw(LinkSource.AUX_A_PLUS, sig(false, false, 0, 1, 0)));
        assertFalse(LinkLogic.raw(LinkSource.AUX_A_MINUS, sig(false, false, 0, 1, 0)));
        assertTrue(LinkLogic.raw(LinkSource.AUX_A_MINUS, sig(false, false, 0, -1, 0)));
        assertFalse(LinkLogic.raw(LinkSource.AUX_B_PLUS, sig(false, false, 0, 1, 0)));
    }

    @Test
    void aHornSoundsWhileHeldAndHeadlightsToggle() {
        LinkLogic.State horn = new LinkLogic.State();
        assertEquals(15, horn.step(LinkSource.AUX_A_PLUS, false, sig(false, false, 0, 1, 0)));
        assertEquals(0, horn.step(LinkSource.AUX_A_PLUS, false, sig(false, false, 0, 0, 1)));
        LinkLogic.State lights = new LinkLogic.State();
        assertEquals(15, lights.step(LinkSource.AUX_A_PLUS, true, sig(false, false, 0, 1, 0)), "a press turns them on");
        assertEquals(15, lights.step(LinkSource.AUX_A_PLUS, true, sig(false, false, 0, 1, 1)), "holding is not a second press");
        assertEquals(15, lights.step(LinkSource.AUX_A_PLUS, true, sig(false, false, 0, 0, 2)), "and they stay on");
        assertEquals(0, lights.step(LinkSource.AUX_A_PLUS, true, sig(false, false, 0, 1, 3)), "the next press turns them off");
    }

    @Test
    void everythingGoesDarkWhenNobodyDrives() {
        LinkLogic.State lights = new LinkLogic.State();
        lights.step(LinkSource.AUX_A_PLUS, true, sig(false, false, 0, 1, 0));
        assertEquals(0, lights.step(LinkSource.AUX_A_PLUS, true, LinkLogic.Signals.NONE), "leave the seat, the lights go out");
        assertEquals(0, lights.step(LinkSource.AUX_A_PLUS, true, sig(false, false, 0, 0, 5)), "and do not come back on by themselves");
        assertEquals(0, new LinkLogic.State().step(LinkSource.DRIVING, false, LinkLogic.Signals.NONE));
    }

    @Test
    void sourcesRoundTripByKeyAndShowUnderTheirControl() {
        for (LinkSource s : LinkSource.values()) assertEquals(s, LinkSource.byKey(s.key()));
        assertEquals(Control.BRAKE, LinkSource.BRAKE.shownUnder());
        assertEquals(Control.STEER, LinkSource.LEFT.shownUnder());
        assertEquals(Control.AUX_C, LinkSource.AUX_C_MINUS.shownUnder());
        assertTrue(LinkSource.AUX_D_PLUS.keyed());
        assertFalse(LinkSource.REVERSE.keyed());
    }

    @Test
    void aCouplerHoldsItsToggleWhenTheDriverLeavesAndALampDoesNot() {
        LinkLogic.State coupler = new LinkLogic.State();
        LinkLogic.State lamp = new LinkLogic.State();
        coupler.step(LinkSource.AUX_B_PLUS, true, sig(false, false, 0, 0, 0), true);
        assertEquals(LinkLogic.FULL, coupler.step(LinkSource.AUX_B_PLUS, true, new LinkLogic.Signals(true, false, false, false, 0,
                new double[]{0, 1, 0, 0}, 1), true), "pressed: coupled");
        lamp.step(LinkSource.AUX_B_PLUS, true, new LinkLogic.Signals(true, false, false, false, 0, new double[]{0, 1, 0, 0}, 1), false);
        assertEquals(LinkLogic.FULL, coupler.step(LinkSource.AUX_B_PLUS, true, LinkLogic.Signals.NONE, true), "parked: still coupled");
        assertEquals(0, lamp.step(LinkSource.AUX_B_PLUS, true, LinkLogic.Signals.NONE, false), "a lamp goes dark");
        LinkLogic.State reloaded = new LinkLogic.State();
        reloaded.restore(true);
        assertEquals(LinkLogic.FULL, reloaded.step(LinkSource.AUX_B_PLUS, true, LinkLogic.Signals.NONE, true), "a reload keeps it coupled");
        assertEquals(LinkLogic.FULL, reloaded.step(LinkSource.AUX_B_PLUS, true, sig(false, false, 0, 0, 2), true));
        reloaded.step(LinkSource.AUX_B_PLUS, true, new LinkLogic.Signals(true, false, false, false, 0, new double[]{0, 1, 0, 0}, 3), true);
        assertEquals(0, reloaded.step(LinkSource.AUX_B_PLUS, true, sig(false, false, 0, 0, 4), true), "the next press uncouples");
    }
}
