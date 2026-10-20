package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.Control;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirLightTest {
    private static AirLight.Signals flying(long t) {
        return new AirLight.Signals(true, true, false, false, false, false, 0.0, false, false,
                new double[Control.values().length], t);
    }

    private static AirLight.Signals with(boolean flying, boolean low, boolean night, double climb, boolean alarm, boolean beam,
                                         boolean moored) {
        return new AirLight.Signals(flying, false, moored, low, night, false, climb, alarm, beam,
                new double[Control.values().length], 0L);
    }

    @Test
    void theBeaconAndTheStrobeFlashInTimeAndOnlyInFlight() {
        int beacon = 0;
        int strobe = 0;
        for (long t = 0; t < 24 * 26; t++) {
            if (AirLight.BEACON.level(flying(t), false) > 0) beacon++;
            if (AirLight.STROBE.level(flying(t), false) > 0) strobe++;
        }
        assertEquals(26 * 4, beacon, "four ticks in every twenty-four");
        assertEquals(24 * 4, strobe, "two flashes of two ticks in every twenty-six");
        AirLight.Signals parked = with(false, false, false, 0.0, false, false, true);
        assertEquals(0, AirLight.BEACON.level(parked, false));
        assertEquals(0, AirLight.POSITION.level(parked, false));
        assertEquals(15, AirLight.MOORED.level(parked, false));
    }

    @Test
    void anAmberLampIsGivenItsBeaconSteadyAndFlashesItself() {
        for (long t = 0; t < 30; t++) assertEquals(AirLight.FULL, AirLight.BEACON.strength(flying(t), false, AirLight.AMBER));
        assertEquals(0, AirLight.BEACON.strength(with(false, false, false, 0, false, false, false), false, AirLight.AMBER));
    }

    @Test
    void aWhiteSternLightIsTheDippedBeamARedOneFull() {
        assertEquals(AirLight.LOW, AirLight.POSITION.strength(flying(0), false, AirLight.WHITE));
        assertEquals(AirLight.FULL, AirLight.POSITION.strength(flying(0), false, AirLight.RED));
        assertEquals(AirLight.FULL, AirLight.POSITION.strength(flying(0), false, AirLight.RECEIVER));
    }

    @Test
    void eachJobAnswersItsOwnQuestion() {
        assertEquals(15, AirLight.LANDING.level(with(true, true, false, 0, false, false, false), false), "low: landing lights on");
        assertEquals(15, AirLight.LANDING.level(with(true, false, false, 0, false, true, false), false), "the full beam key");
        assertEquals(0, AirLight.LANDING.level(with(true, false, false, 0, false, false, false), false));
        assertEquals(15, AirLight.FLOOD.level(with(true, false, true, 0, false, false, false), false));
        assertEquals(0, AirLight.FLOOD.level(with(true, false, false, 0, false, false, false), false));
        assertEquals(15, AirLight.CLIMB.level(with(true, false, false, 1.5, false, false, false), false));
        assertEquals(0, AirLight.CLIMB.level(with(true, false, false, 0.5, false, false, false), false));
        assertEquals(15, AirLight.SINK.level(with(true, false, false, -1.5, false, false, false), false));
        assertEquals(15, AirLight.ALARM.level(with(false, false, false, 0, true, false, false), false), "an alarm rings on the ground too");
        assertEquals(15, AirLight.AUX_A_PLUS.level(flying(0), true));
        assertEquals(0, AirLight.AUX_A_PLUS.level(flying(0), false));
    }

    @Test
    void aKeyJobIsHeldOrToggledAndOnlyKeyJobsToggle() {
        double[] controls = new double[Control.values().length];
        controls[Control.AUX_B.ordinal()] = -1.0;
        AirLight.Signals s = new AirLight.Signals(true, true, false, false, false, false, 0.0, false, false, controls, 0L);
        assertTrue(AirLight.AUX_B_MINUS.keyHeld(s));
        assertFalse(AirLight.AUX_B_PLUS.keyHeld(s));
        AirLight.Latch latch = new AirLight.Latch();
        assertTrue(latch.step(true, true), "first press: on");
        assertTrue(latch.step(false, true), "stays on when let go");
        assertFalse(latch.step(true, true), "second press: off");
        assertFalse(latch.step(false, true));
        assertTrue(latch.step(true, false), "not a toggle: lit while held");
        assertFalse(latch.step(false, false));
        assertTrue(AirLight.AUX_D_PLUS.keyed());
        assertFalse(AirLight.LANDING.keyed(), "the landing lights answer the beam key but are not a switch");
        assertFalse(AirLight.BEACON.keyed());
    }

    @Test
    void aLampIsProposedItsJobByColourAndAim() {
        assertEquals(AirLight.LANDING, AirLight.propose(AirLight.WHITE, -1.0, 0.0), "white, pointing down");
        assertEquals(AirLight.LANDING, AirLight.propose(AirLight.WHITE, 0.0, 1.0), "white, pointing ahead: a searchlight");
        assertEquals(AirLight.POSITION, AirLight.propose(AirLight.WHITE, 0.0, -1.0), "white, pointing back: the stern light");
        assertEquals(AirLight.FLOOD, AirLight.propose(AirLight.WHITE, 1.0, 0.0), "white, pointing up at the envelope");
        assertEquals(AirLight.FLOOD, AirLight.propose(AirLight.WHITE, 0.0, 0.0), "white, pointing sideways");
        assertEquals(AirLight.BEACON, AirLight.propose(AirLight.RED, 1.0, 0.0), "red on top");
        assertEquals(AirLight.BEACON, AirLight.propose(AirLight.RED, -1.0, 0.0), "red on the belly");
        assertEquals(AirLight.POSITION, AirLight.propose(AirLight.RED, 0.0, 0.0), "red on a side");
        assertEquals(AirLight.BEACON, AirLight.propose(AirLight.AMBER, 0.0, 0.0));
        assertEquals(AirLight.OFF, AirLight.propose(AirLight.RECEIVER, 0.0, 0.0), "a receiver waits for the player");
        for (AirLight j : AirLight.values()) {
            assertEquals(j, AirLight.byKey(j.key));
            assertEquals(j, AirLight.byOrdinal(j.ordinal()));
        }
    }
}
