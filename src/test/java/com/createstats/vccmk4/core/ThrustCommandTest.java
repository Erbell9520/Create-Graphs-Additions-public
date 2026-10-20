package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrustCommandTest {
    @Test
    void aThrusterTakesOnlyThePositiveHalfOfTheMixersCommand() {
        assertEquals(0.75, ThrustCommand.forKind(JetKind.THRUSTER, 0.75), 1e-9);
        assertEquals(0.0, ThrustCommand.forKind(JetKind.THRUSTER, -0.75), 1e-9, "a rearward-facing thruster on forward Boost");
        assertEquals(1.0, ThrustCommand.forKind(JetKind.THRUSTER, 1.5), 1e-9);
        assertEquals(0.0, ThrustCommand.forKind(JetKind.THRUSTER, Double.NaN), 1e-9);
        assertEquals(-0.75, ThrustCommand.forKind(JetKind.PROPELLER, -0.75), 1e-9, "a propeller keeps both ways");
    }

    @Test
    void aBoosterIsOnFromHalfACommandUp() {
        assertEquals(1.0, ThrustCommand.forKind(JetKind.BOOSTER, 1.0), 1e-9);
        assertEquals(1.0, ThrustCommand.forKind(JetKind.BOOSTER, 0.5), 1e-9);
        assertEquals(0.0, ThrustCommand.forKind(JetKind.BOOSTER, 0.49), 1e-9, "a trailer's trim never fires it");
        assertEquals(0.0, ThrustCommand.forKind(JetKind.BOOSTER, -1.0), 1e-9);
    }

    @Test
    void commandsAreRoundedToASixtyFourth() {
        assertEquals(0.5, ThrustCommand.quantise(0.503), 1e-9);
        assertEquals(1.0 / 64.0, ThrustCommand.quantise(0.012), 1e-9);
        assertEquals(0.0, ThrustCommand.quantise(0.007), 1e-9, "a trim too small to matter costs no packet");
        assertEquals(ThrustCommand.quantise(0.296), ThrustCommand.quantise(0.303), 1e-12, "the same step, so the setter sees no change");
        assertEquals(0.0, ThrustCommand.quantise(Double.NaN), 1e-9);
    }

    @Test
    void theCeilingIsMeasuredWhileLitAndConfiguredWhileDark() {
        double ceiling = 533.0 * 8 * 1.25 * 1.3;
        double lit = Math.min(0.6, 0.5) * 1.0;
        assertEquals(ceiling, ThrustCommand.ceiling(ceiling * lit, lit, 533.0), 1e-6);
        assertEquals(533.0, ThrustCommand.ceiling(0.0, 0.0, 533.0), 1e-9, "dark: the configured figure");
        assertEquals(533.0, ThrustCommand.ceiling(3.0, 0.01, 533.0), 1e-9, "too dim to measure: the configured figure");
        assertEquals(0.0, ThrustCommand.ceiling(0.0, 0.0, Double.NaN), 1e-9);

        assertEquals(667.0, ThrustCommand.ceiling(667.0 * 0.4, 0.4, 100.0), 1e-9);
    }
}
