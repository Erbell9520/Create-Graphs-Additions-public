package com.createstats.antenna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AntennaTierTest {
    @Test
    void multipliersMatchTheIntendedLadder() {
        assertEquals(1, AntennaTier.NONE.getRangeMultiplier());
        assertEquals(4, AntennaTier.SMALL.getRangeMultiplier());
        assertEquals(12, AntennaTier.ADVANCED.getRangeMultiplier());
        assertEquals(32, AntennaTier.TRT.getRangeMultiplier());
    }

    @Test
    void ladderStrictlyIncreasesFromNoneToTrt() {
        assertTrue(AntennaTier.NONE.getRangeMultiplier() < AntennaTier.SMALL.getRangeMultiplier());
        assertTrue(AntennaTier.SMALL.getRangeMultiplier() < AntennaTier.ADVANCED.getRangeMultiplier());
        assertTrue(AntennaTier.ADVANCED.getRangeMultiplier() < AntennaTier.TRT.getRangeMultiplier());
    }

    @Test
    void onlyTrtIsCrossDimensional() {
        assertFalse(AntennaTier.NONE.isCrossDimensional());
        assertFalse(AntennaTier.SMALL.isCrossDimensional());
        assertFalse(AntennaTier.ADVANCED.isCrossDimensional());
        assertTrue(AntennaTier.TRT.isCrossDimensional());
    }

    @Test
    void onlyNoneIsUnattached() {
        assertFalse(AntennaTier.NONE.isAttached());
        assertTrue(AntennaTier.SMALL.isAttached());
        assertTrue(AntennaTier.ADVANCED.isAttached());
        assertTrue(AntennaTier.TRT.isAttached());
    }

    @Test
    void byNameAcceptsSerializedAndEnumNamesCaseInsensitively() {
        assertEquals(AntennaTier.SMALL, AntennaTier.byName("small"));
        assertEquals(AntennaTier.SMALL, AntennaTier.byName("SMALL"));
        assertEquals(AntennaTier.ADVANCED, AntennaTier.byName("advanced"));
        assertEquals(AntennaTier.TRT, AntennaTier.byName("trt"));
        assertEquals(AntennaTier.TRT, AntennaTier.byName("TRT"));
    }

    @Test
    void byNameFallsBackToNoneForUnknownOrBlankInput() {
        assertEquals(AntennaTier.NONE, AntennaTier.byName(null));
        assertEquals(AntennaTier.NONE, AntennaTier.byName(""));
        assertEquals(AntennaTier.NONE, AntennaTier.byName("   "));
        assertEquals(AntennaTier.NONE, AntennaTier.byName("not_a_tier"));
    }

    @Test
    void serializedNamesAreStable() {
        assertEquals("none", AntennaTier.NONE.getSerializedName());
        assertEquals("small", AntennaTier.SMALL.getSerializedName());
        assertEquals("advanced", AntennaTier.ADVANCED.getSerializedName());
        assertEquals("trt", AntennaTier.TRT.getSerializedName());
    }
}
