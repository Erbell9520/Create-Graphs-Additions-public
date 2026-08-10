package com.createstats.antenna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** [1] */
class AntennaRangeTest {

    private static final double BASE = 10.0;
    private static final double EPS = 1.0e-9;

    // localRange: tier scaling

    @Test
    void localRangeScalesByTierInTheOverworld() {
        assertEquals(BASE, AntennaRange.localRange(BASE, AntennaTier.NONE, false), EPS);
        assertEquals(BASE * 4, AntennaRange.localRange(BASE, AntennaTier.SMALL, false), EPS);
        assertEquals(BASE * 12, AntennaRange.localRange(BASE, AntennaTier.ADVANCED, false), EPS);
        assertEquals(BASE * 32, AntennaRange.localRange(BASE, AntennaTier.TRT, false), EPS);
    }

    @Test
    void trtIsCompressedToAQuarterInsideTheNether() {
        // 32x Overworld / 8 = 4x Nether (same physical coverage, fewer local blocks).
        assertEquals(BASE * 4, AntennaRange.localRange(BASE, AntennaTier.TRT, true), EPS);
    }

    @Test
    void shorterTiersAreNotCompressedInTheNether() {
        assertEquals(BASE * 4, AntennaRange.localRange(BASE, AntennaTier.SMALL, true), EPS);
        assertEquals(BASE * 12, AntennaRange.localRange(BASE, AntennaTier.ADVANCED, true), EPS);
        assertEquals(BASE, AntennaRange.localRange(BASE, AntennaTier.NONE, true), EPS);
    }

    @Test
    void nullTierIsTreatedAsNone() {
        assertEquals(BASE, AntennaRange.localRange(BASE, null, false), EPS);
    }

    @Test
    void zeroBaseRangeStaysZero() {
        assertEquals(0.0, AntennaRange.localRange(0.0, AntennaTier.TRT, false), EPS);
        assertEquals(0.0, AntennaRange.localRange(0.0, AntennaTier.TRT, true), EPS);
    }

    // withinRangeSq: same-dimension boundaries

    @Test
    void withinRangeSqRespectsTheExactBoundary() {
        double range = AntennaRange.localRange(BASE, AntennaTier.SMALL, false); // 40
        double atBoundary = range * range;
        assertTrue(AntennaRange.withinRangeSq(atBoundary, BASE, AntennaTier.SMALL, false));
        assertFalse(AntennaRange.withinRangeSq(atBoundary + 1.0, BASE, AntennaTier.SMALL, false));
    }

    @Test
    void withinRangeSqUsesTheCompressedNetherRangeForTrt() {
        double overworldEdge = (BASE * 32) * (BASE * 32); // 320^2
        double netherEdge = (BASE * 4) * (BASE * 4);       // 40^2
        // A distance reachable in the Overworld is NOT reachable from the compressed Nether range.
        assertTrue(AntennaRange.withinRangeSq(overworldEdge, BASE, AntennaTier.TRT, false));
        assertFalse(AntennaRange.withinRangeSq(overworldEdge, BASE, AntennaTier.TRT, true));
        assertTrue(AntennaRange.withinRangeSq(netherEdge, BASE, AntennaTier.TRT, true));
    }

    @Test
    void nonPositiveBaseRangeMeansUnbounded() {
        assertTrue(AntennaRange.withinRangeSq(1_000_000.0, 0.0, AntennaTier.SMALL, false));
        assertTrue(AntennaRange.withinRangeSq(1_000_000.0, -5.0, AntennaTier.NONE, false));
    }

    // crossDimEligible

    @Test
    void onlyTrtCanCrossWhenTheDimensionsArePaired() {
        assertTrue(AntennaRange.crossDimEligible(AntennaTier.TRT, true));
        assertFalse(AntennaRange.crossDimEligible(AntennaTier.TRT, false));
        assertFalse(AntennaRange.crossDimEligible(AntennaTier.ADVANCED, true));
        assertFalse(AntennaRange.crossDimEligible(AntennaTier.SMALL, true));
        assertFalse(AntennaRange.crossDimEligible(AntennaTier.NONE, true));
        assertFalse(AntennaRange.crossDimEligible(null, true));
    }

    // scale / cappedScanRadius / directRange

    @Test
    void scaleMirrorsTheTierMultiplierWithABaseFloor() {
        assertEquals(320, AntennaRange.scale(10, AntennaTier.TRT));
        assertEquals(40, AntennaRange.scale(10, AntennaTier.SMALL));
        assertEquals(5, AntennaRange.scale(5, AntennaTier.NONE));
        assertEquals(10, AntennaRange.scale(10, null));
    }

    @Test
    void cappedScanRadiusClampsBetweenBaseAndMax() {
        assertEquals(16, AntennaRange.cappedScanRadius(2, AntennaTier.TRT, 16));   // 64 clamped to 16
        assertEquals(8, AntennaRange.cappedScanRadius(2, AntennaTier.SMALL, 16));  // 8 within range
        assertEquals(2, AntennaRange.cappedScanRadius(2, AntennaTier.NONE, 16));   // stays at base
        assertEquals(20, AntennaRange.cappedScanRadius(20, AntennaTier.SMALL, 16)); // base floor wins
    }

    @Test
    void directRangeDoubleOverloadUsesOverworldScaling() {
        assertEquals(BASE * 32, AntennaRange.directRange(BASE, AntennaTier.TRT), EPS);
        assertEquals(BASE * 4, AntennaRange.directRange(BASE, AntennaTier.SMALL), EPS);
    }

    // isOverworldNetherPair (dimension ids)

    @Test
    void overworldNetherPairIsRecognisedInEitherOrder() {
        ResourceLocation overworld = ResourceLocation.withDefaultNamespace("overworld");
        ResourceLocation nether = ResourceLocation.withDefaultNamespace("the_nether");
        assertTrue(AntennaRange.isOverworldNetherPair(overworld, nether));
        assertTrue(AntennaRange.isOverworldNetherPair(nether, overworld));
    }

    @Test
    void otherDimensionCombinationsAreNotAPair() {
        ResourceLocation overworld = ResourceLocation.withDefaultNamespace("overworld");
        ResourceLocation nether = ResourceLocation.withDefaultNamespace("the_nether");
        ResourceLocation end = ResourceLocation.withDefaultNamespace("the_end");
        assertFalse(AntennaRange.isOverworldNetherPair(overworld, end));
        assertFalse(AntennaRange.isOverworldNetherPair(nether, end));
        assertFalse(AntennaRange.isOverworldNetherPair(overworld, overworld));
        assertFalse(AntennaRange.isOverworldNetherPair(null, nether));
        assertFalse(AntennaRange.isOverworldNetherPair(overworld, null));
    }
}
