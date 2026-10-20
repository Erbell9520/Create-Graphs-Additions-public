package com.createstats.baking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvenThermalTest {
    @Test
    @DisplayName("every setpoint has a bake-time percentage")
    void theDialsTwoArraysAgree() {
        assertEquals(OvenThermal.SETPOINTS.length, OvenThermal.TIME_PERCENT.length,
                "SETPOINTS and TIME_PERCENT are indexed by the same dial position, so a setting "
                        + "present in one and missing from the other is an index out of bounds the "
                        + "first time a player turns the knob that far");
        assertEquals(3, OvenThermal.settings());
    }

    @Test
    @DisplayName("a cold cavity draws the full element rating")
    void coldDrawsFull() {
        final OvenThermal oven = new OvenThermal();
        for (int setting = 0; setting < OvenThermal.settings(); setting++) {
            assertEquals(OvenThermal.DRAW_ELEMENT, oven.elementDraw(OvenThermal.setpoint(setting)),
                    "setting " + setting);
        }
    }

    @Test
    @DisplayName("holding a hotter setpoint costs strictly more")
    void holdCostRisesWithTemperature() {
        int previous = 0;
        for (int setting = 0; setting < OvenThermal.settings(); setting++) {
            final int setpoint = OvenThermal.setpoint(setting);
            final OvenThermal oven = new OvenThermal();
            oven.setHeat(setpoint);
            final int hold = oven.elementDraw(setpoint);
            assertTrue(hold > previous,
                    "holding " + OvenThermal.setpointCelsius(setting) + " C costs " + hold
                            + " FE/t, which is not more than the cooler setting's " + previous);
            assertTrue(hold < OvenThermal.DRAW_ELEMENT,
                    "holding temperature must be cheaper than preheating, or the dial's whole point "
                            + "is gone; got " + hold + " against " + OvenThermal.DRAW_ELEMENT);
            previous = hold;
        }
    }

    @Test
    @DisplayName("180 C holds at 40 FE/t and preheats in under half a minute")
    void theMiddleSettingIsTheOneQuoted() {
        final int setpoint = OvenThermal.setpoint(1);
        assertEquals(180_000, setpoint);
        final OvenThermal oven = new OvenThermal();
        oven.setHeat(setpoint);
        assertEquals(40, oven.elementDraw(setpoint));

        final int ticks = OvenThermal.preheatTicks(OvenThermal.AMBIENT, setpoint);
        assertTrue(ticks > 0 && ticks < 20 * 30,
                "a preheat from cold took " + ticks + " ticks; the block quotes this figure to the "
                        + "player and a number in minutes would make the machine feel broken");
    }

    @Test
    @DisplayName("paying the asked draw holds the cavity inside the bake band indefinitely")
    void theHoldActuallyHolds() {
        for (int setting = 0; setting < OvenThermal.settings(); setting++) {
            final int setpoint = OvenThermal.setpoint(setting);
            final OvenThermal oven = new OvenThermal();
            oven.setHeat(setpoint);
            for (int t = 0; t < 20 * 60 * 5; t++) {
                oven.step(oven.elementDraw(setpoint));
                assertTrue(oven.atTemperature(setpoint),
                        "setting " + setting + " fell out of the bake band at tick " + t
                                + ", heat " + oven.heat());
                assertTrue(oven.heat() <= setpoint + OvenThermal.GAIN_PER_FE * OvenThermal.DRAW_ELEMENT,
                        "setting " + setting + " ran away past its setpoint at tick " + t
                                + ", heat " + oven.heat());
            }
        }
    }

    @Test
    @DisplayName("an unpowered cavity reaches ambient exactly and never goes below it")
    void coolingLandsOnAmbient() {
        final OvenThermal oven = new OvenThermal();
        oven.setHeat(OvenThermal.setpoint(2));
        for (int t = 0; t < 20 * 60 * 30; t++) {
            oven.step(0);
        }
        assertEquals(OvenThermal.AMBIENT, oven.heat(),
                "a cavity left to cool has to land on the room, not a fraction of a degree above it: "
                        + "a readout stuck at 21 C reads as a broken block rather than a cold one");
    }

    @Test
    @DisplayName("cooling is gradual, so a second tin straight after the first is quicker")
    void coolingIsGradual() {
        final OvenThermal oven = new OvenThermal();
        final int setpoint = OvenThermal.setpoint(1);
        oven.setHeat(setpoint);
        for (int t = 0; t < 20 * 10; t++) {
            oven.step(0);
        }
        assertTrue(oven.atTemperature(setpoint) || oven.heat() > setpoint - 40_000,
                "ten seconds off the elements dropped the cavity to " + oven.celsius()
                        + " C; a bake that ends and starts again should not be a cold start");

        final int warmAgain = OvenThermal.preheatTicks(oven.heat(), setpoint);
        final int fromCold = OvenThermal.preheatTicks(OvenThermal.AMBIENT, setpoint);
        assertTrue(warmAgain < fromCold,
                "reheating a warm cavity (" + warmAgain + " ticks) has to beat a cold start ("
                        + fromCold + "), or the whole preheat mechanism buys nothing");
    }

    @Test
    @DisplayName("the bake band is what a bake is gated on, not the setpoint itself")
    void theBandGatesTheBake() {
        final int setpoint = OvenThermal.setpoint(1);
        final OvenThermal oven = new OvenThermal();
        oven.setHeat(setpoint - OvenThermal.BAKE_BAND - 1);
        assertFalse(oven.atTemperature(setpoint));
        oven.setHeat(setpoint - OvenThermal.BAKE_BAND);
        assertTrue(oven.atTemperature(setpoint));
    }

    @Test
    @DisplayName("a settled oven never needs full elements again")
    void aSettledOvenNeverGoesToFullPower() {
        final int setpoint = OvenThermal.setpoint(1);
        final OvenThermal oven = new OvenThermal();
        oven.setHeat(setpoint);
        int peak = 0;
        for (int t = 0; t < 20 * 60; t++) {
            final int draw = oven.elementDraw(setpoint);
            peak = Math.max(peak, draw);
            oven.step(draw);
        }
        assertTrue(peak < OvenThermal.DRAW_ELEMENT,
                "a settled oven asked for " + peak + " FE/t, the full element rating. That means it "
                        + "is sawtoothing across the setpoint rather than holding at it, and the "
                        + "goggle's draw figure would swing every tick.");
    }

    @Test
    @DisplayName("loss rounds up, so the last fraction of a degree really does leave")
    void lossRoundsUp() {
        assertEquals(0, OvenThermal.lossPerTick(0));
        assertEquals(1, OvenThermal.lossPerTick(1));
        assertEquals(1, OvenThermal.lossPerTick(OvenThermal.LOSS_DIVISOR));
        assertEquals(2, OvenThermal.lossPerTick(OvenThermal.LOSS_DIVISOR + 1));
        assertEquals(160, OvenThermal.lossPerTick(160_000));
    }

    @Test
    @DisplayName("the FE needed for a heat rounds up")
    void feForHeatRoundsUp() {
        assertEquals(0, OvenThermal.feForHeat(0));
        assertEquals(1, OvenThermal.feForHeat(1));
        assertEquals(1, OvenThermal.feForHeat(OvenThermal.GAIN_PER_FE));
        assertEquals(2, OvenThermal.feForHeat(OvenThermal.GAIN_PER_FE + 1));
    }

    @Test
    @DisplayName("a brownout costs heat at the room's rate and no faster")
    void starvingIsJustCooling() {
        final OvenThermal starved = new OvenThermal();
        final OvenThermal off = new OvenThermal();
        starved.setHeat(OvenThermal.setpoint(1));
        off.setHeat(OvenThermal.setpoint(1));
        for (int t = 0; t < 400; t++) {
            starved.step(0);
            off.step(0);
        }
        assertEquals(off.heat(), starved.heat());
    }

    @Test
    @DisplayName("a cooler setting bakes for longer, and no setting bakes in zero ticks")
    void bakeTimeScalesWithTheDial() {
        int previous = Integer.MAX_VALUE;
        for (int setting = 0; setting < OvenThermal.settings(); setting++) {
            final int ticks = OvenThermal.bakeTicks(300, setting);
            assertTrue(ticks < previous,
                    "setting " + setting + " bakes in " + ticks + " ticks, which is not quicker than "
                            + "the cooler setting's " + previous);
            previous = ticks;
        }
        assertEquals(1, OvenThermal.bakeTicks(1, OvenThermal.settings() - 1),
                "a one tick recipe on the fastest setting must still take a tick, not zero");
    }

    @Test
    @DisplayName("an out of range dial position wraps")
    void theDialWraps() {
        assertEquals(OvenThermal.setpoint(0), OvenThermal.setpoint(OvenThermal.settings()));
        assertEquals(OvenThermal.setpoint(OvenThermal.settings() - 1), OvenThermal.setpoint(-1));
        assertEquals(OvenThermal.bakeTicks(200, 0), OvenThermal.bakeTicks(200, OvenThermal.settings()));
    }

    @Test
    @DisplayName("a loaded heat is clamped into the range the model can hold")
    void loadedHeatIsClamped() {
        final OvenThermal oven = new OvenThermal();
        oven.setHeat(Integer.MIN_VALUE);
        assertEquals(OvenThermal.AMBIENT, oven.heat());
        oven.setHeat(Integer.MAX_VALUE);
        assertTrue(oven.heat() <= OvenThermal.SETPOINTS[OvenThermal.SETPOINTS.length - 1] + 50_000);
    }
}
