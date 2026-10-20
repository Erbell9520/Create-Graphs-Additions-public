package com.createstats.vccmk4.air.core;

import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.ThrustCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JetRampTest {
    @Test
    void eachFeelMovesARedstoneStepAtItsOwnPace() {
        assertEquals(20, Feel.GENTLE.jetTicksPerStep, "a step a second");
        assertEquals(10, Feel.STANDARD.jetTicksPerStep, "the owner's half second, 10 ticks");
        assertEquals(5, Feel.SPORTY.jetTicksPerStep);
        for (Feel f : Feel.values()) {
            double level = 0.0;
            for (int t = 0; t < f.jetTicksPerStep; t++) level = JetRamp.toward(level, 1.0, f);
            assertEquals(1.0 / 15.0, level, 1.0e-9, f + ": one redstone step in " + f.jetTicksPerStep + " ticks");
        }
    }

    @Test
    void darkToFullTakesFifteenStepsAndBackTheSame() {
        for (Feel f : Feel.values()) {
            int full = 15 * f.jetTicksPerStep;
            assertEquals(full, JetRamp.ticks(0.0, 1.0, f));
            assertEquals(full, JetRamp.ticks(1.0, 0.0, f), "down is never faster than up");
            assertEquals(full / 20.0, f.jetSeconds(), 1.0e-9);
            double level = 0.0;
            int t = 0;
            while (level < 1.0 && t < 10_000) {
                double next = JetRamp.toward(level, 1.0, f);
                assertTrue(next - level <= f.jetStep() + 1.0e-12, "never more than a step a tick");
                level = next;
                t++;
            }
            assertEquals(full, t, 1, f + ": a whole sweep");
        }
    }

    @Test
    void theWindowIsWhereTheRampReachesThisTick() {
        Feel f = Feel.STANDARD;
        double s = f.jetStep();
        assertEquals(0.5 - s, JetRamp.low(0.5, f), 1.0e-12);
        assertEquals(0.5 + s, JetRamp.high(0.5, 1.0, f), 1.0e-12);
        assertEquals(0.0, JetRamp.low(0.0, f), 1.0e-12, "never below dark");
        assertEquals(1.0, JetRamp.high(1.0, 1.0, f), 1.0e-12, "never past full");

        assertEquals(0.8 - s, JetRamp.high(0.8, 0.25, f), 1.0e-12);
        assertEquals(0.8 - s, JetRamp.toward(0.8, 0.0, f), 1.0e-12, "cut to nothing, it winds down a step");
    }

    @Test
    void aGentleRampStillMovesUnderTheSixtyFourthsRounding() {
        Feel f = Feel.GENTLE;
        double level = 0.0;
        double written = 0.0;
        int changes = 0;
        for (int t = 0; t < 60; t++) {
            level = JetRamp.toward(level, 1.0, f);
            double w = ThrustCommand.forKind(JetKind.THRUSTER, level);
            if (w != written) {
                assertTrue(w - written <= 1.0 / 64.0 + 1.0e-12, "a sixty-fourth at a time");
                changes++;
            }
            written = w;
        }
        assertEquals(3.0 / 15.0, level, 1.0e-9, "three steps in three seconds");
        assertTrue(changes >= 10, "what is written moves too: " + changes);
    }

    @Test
    void settledIsWithinAStep() {
        Feel f = Feel.SPORTY;
        assertTrue(JetRamp.settled(0.4, 0.4 + f.jetStep() * 0.5, f));
        assertTrue(!JetRamp.settled(0.4, 0.6, f));
    }
}
