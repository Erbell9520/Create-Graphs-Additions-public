package com.createstats.vehiclelight;

import com.createstats.vccmk4.core.LinkLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleLightKindTest {
    @Test
    void redIsTheTailLightOnAWeakSignalAndTheBrakeLightOnAFullOne() {
        assertEquals(VehicleLightGlow.OFF, VehicleLightKind.RED.glow(0, 0));
        for (int s = 1; s < LinkLogic.FULL; s++) {
            assertEquals(VehicleLightGlow.DIM, VehicleLightKind.RED.glow(s, 0), "strength " + s + " is the tail light");
        }
        assertEquals(VehicleLightGlow.BRIGHT, VehicleLightKind.RED.glow(LinkLogic.FULL, 0), "full is the brake light");
        assertEquals(VehicleLightGlow.DIM, VehicleLightKind.RED.glow(LinkLogic.TAIL, 0),
                "the VCC's Tail and brake sends a strength the red light reads as its tail light");
        assertTrue(VehicleLightKind.RED.emission(VehicleLightGlow.BRIGHT) > VehicleLightKind.RED.emission(VehicleLightGlow.DIM),
                "braking is brighter than the tail light");
    }

    @Test
    void whiteIsTheDippedBeamOnAWeakSignalAndTheFullBeamOnAFullOne() {
        assertEquals(VehicleLightGlow.OFF, VehicleLightKind.WHITE.glow(0, 5));
        assertEquals(VehicleLightGlow.DIM, VehicleLightKind.WHITE.glow(7, 5));
        assertEquals(VehicleLightGlow.BRIGHT, VehicleLightKind.WHITE.glow(15, 5));
        assertEquals(14, VehicleLightKind.WHITE.emission(VehicleLightGlow.BRIGHT));
        assertEquals(0, VehicleLightKind.WHITE.emission(VehicleLightGlow.OFF));
    }

    @Test
    void amberFlashesOnItsOwnWhilePoweredInStepWithTheConsolesIndicators() {
        int lit = 0;
        for (long t = 0; t < 4L * LinkLogic.BLINK_TICKS; t++) {
            final VehicleLightGlow g = VehicleLightKind.AMBER.glow(1, t);
            if (g == VehicleLightGlow.BRIGHT) {
                lit++;
            } else {
                assertEquals(VehicleLightGlow.OFF, g, "a signal is on or off, never dim");
            }

            final boolean console = LinkLogic.raw(com.createstats.vccmk4.core.LinkSource.LEFT,
                    new LinkLogic.Signals(true, false, false, false, -1.0, new double[4], t));
            assertEquals(console, g == VehicleLightGlow.BRIGHT, "tick " + t);
        }
        assertEquals(2 * LinkLogic.BLINK_TICKS, lit, "half the time");
        for (long t = 0; t < 40; t++) {
            assertEquals(VehicleLightGlow.OFF, VehicleLightKind.AMBER.glow(0, t), "unpowered it stays dark");
        }
        assertEquals(VehicleLightGlow.BRIGHT, VehicleLightKind.AMBER.glow(15, 0));
    }

    @Test
    void onACraftASignalHoldsItsStateAndEachClientFlashesItInTheSamePhase() {
        for (long t = 0; t < 4L * LinkLogic.BLINK_TICKS; t++) {
            assertEquals(VehicleLightGlow.DIM, VehicleLightKind.AMBER.glow(15, t, true), "held at tick " + t);
            assertEquals(VehicleLightKind.AMBER.glow(15, t, false),
                    VehicleLightKind.AMBER.shown(VehicleLightGlow.DIM, t),
                    "the client's flash is the world lamp's flash, tick " + t);
        }
        assertEquals(VehicleLightGlow.OFF, VehicleLightKind.AMBER.glow(0, 3, true), "no input, no signal");
        assertEquals(0, VehicleLightKind.AMBER.emission(VehicleLightGlow.DIM),
                "signalling on a craft gives no steady light: the flash is the client's");
        assertEquals(7, VehicleLightKind.AMBER.emission(VehicleLightGlow.BRIGHT), "the world lamp's flash is unchanged");

        assertEquals(VehicleLightGlow.DIM, VehicleLightKind.RED.glow(LinkLogic.TAIL, 0, true));
        assertEquals(VehicleLightGlow.BRIGHT, VehicleLightKind.WHITE.glow(15, 0, true));
        assertEquals(VehicleLightGlow.DIM, VehicleLightKind.RED.shown(VehicleLightGlow.DIM, 9), "a tail light is not flashed");
    }

    @Test
    void theLensSitsInsideItsBezelOnEveryLamp() {
        for (VehicleLightKind k : VehicleLightKind.values()) {
            final float[] body = k.body();
            final float[] flange = k.flange();
            assertTrue(flange[0] <= body[0] && flange[1] <= body[1] && flange[2] >= body[2] && flange[3] >= body[3],
                    k + ": the flange is under the body");
            for (float[] box : k.lens()) {
                assertTrue(box[0] >= body[0] + 1 && box[2] >= body[1] + 1 && box[3] <= body[2] - 1 && box[5] <= body[3] - 1,
                        k + ": the lens is inside the one pixel bezel ring");
                assertTrue(box[1] >= k.bodyTop(), k + ": the lens stands on the body");
            }
            assertTrue(k.lensFront() > k.bodyTop() && k.lensFront() <= k.bezelTop() + 0.11f,
                    k + ": the lens front is between the body and the bezel's top, give or take a dome");
        }
    }

    @Test
    void theCommandIsAShortLease() {
        assertTrue(VehicleLightKind.LEASE_TICKS >= 2, "one late tick must not blink the lamp");
        assertTrue(VehicleLightKind.LEASE_TICKS <= 5, "a removed console's lights go out within a quarter second");
    }
}
