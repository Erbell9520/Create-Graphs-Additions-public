package com.createstats.stormcoil;

import com.createstats.power.WireTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilLawTest {
    @Test
    @DisplayName("the four stages: height, reach, catch radius, arc cost")
    void stages() {
        final int[][] expected = {{1, 6, 32, 20_000}, {2, 10, 64, 40_000}, {3, 16, 96, 80_000}, {4, 24, 128, 160_000}};
        for (CoilStage s : CoilStage.values()) {
            final int[] e = expected[s.ordinal()];
            assertEquals(e[0], s.height);
            assertEquals(e[0], s.number());
            assertEquals(e[1], s.reach);
            assertEquals(e[2], s.catchRadius);
            assertEquals(e[3], s.zapCost);
            assertEquals(e[0], s.targets(), "a stage arcs to as many mobs at once as its number");
            assertEquals(8 * e[3] * e[0], s.capacitorSize(), "the capacitor holds eight full volleys");
        }
        assertEquals(128, CoilStage.FOUR.catchRadius, "vanilla's rod reach, never beyond it");
        assertTrue(CoilStage.FOUR.hasGroundRods() && !CoilStage.THREE.hasGroundRods());
        assertEquals(CoilStage.ONE, CoilStage.of(0));
        assertEquals(CoilStage.FOUR, CoilStage.of(9), "there is no stage past 4");
    }

    @Test
    @DisplayName("IN the lightest wire for a full volley, OUT one HV Line, a strike one Copper-line minute, ten held")
    void fe() {
        final int base = CoilLaw.DEFAULT_BASE;
        final int[] in = {8_192, 8_192, 16_384, 32_768};
        final WireTier[] wire = {WireTier.COPPER, WireTier.COPPER, WireTier.INSULATED, WireTier.SHIELDED};
        final int[] volley = {1_000, 4_000, 12_000, 32_000};
        for (CoilStage s : CoilStage.values()) {
            assertEquals(in[s.ordinal()], CoilLaw.inRate(s, base), s + ": IN");
            assertEquals(wire[s.ordinal()], CoilLaw.inWire(s, base), s + ": the wire the goggles name");
            assertEquals(volley[s.ordinal()], CoilLaw.volleyRate(s.zapCost, s.targets()), s + ": a volley a second");
            assertTrue((long) CoilLaw.inRate(s, base) * CoilLaw.ZAP_INTERVAL >= (long) s.zapCost * s.targets(),
                    s + ": IN must sustain a full volley a second");
        }
        assertEquals(8_192, CoilLaw.inRate(CoilStage.FOUR, 1024), "a thin pack base stops at one HV Line");
        assertEquals(com.createstats.power.ConnectorTier.POWER, CoilLaw.inConnector(CoilStage.THREE, base));
        assertEquals(com.createstats.power.ConnectorTier.ADVANCED, CoilLaw.inConnector(CoilStage.FOUR, base),
                "a plain Power Connector's 16,384 would hold a stage 4 coil to half its volleys");
        assertEquals(base * WireTier.HV.multiplier(), CoilLaw.outRate(base));
        assertEquals(65_536, CoilLaw.outRate(base));
        assertEquals(9_830_400, CoilLaw.strikeFe(base));
        assertEquals(98_304_000, CoilLaw.bankSize(base));
        assertEquals(Integer.MAX_VALUE, CoilLaw.bankSize(10_000_000), "saturates, never wraps (INV-14)");
    }
}
