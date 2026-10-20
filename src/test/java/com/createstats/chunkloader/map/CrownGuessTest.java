package com.createstats.chunkloader.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrownGuessTest {
    private static int faint(int argb) {
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final double c = (argb >> shift) & 0xFF;
            final double p = (CrownGuess.PAPER >> shift) & 0xFF;
            out |= ((int) Math.round(c * 0.65 + p * 0.35) & 0xFF) << shift;
        }
        return out;
    }

    @Test
    @DisplayName("the classes: water from the biome or a surface under the sea, then snow, sand, grass")
    void classify() {
        assertEquals(CrownGuess.Kind.WATER, CrownGuess.classify(true, false, false, 70, 63), "an ocean biome");
        assertEquals(CrownGuess.Kind.WATER, CrownGuess.classify(false, false, false, 58, 63), "sunk under the sea");
        assertEquals(CrownGuess.Kind.GRASS, CrownGuess.classify(false, false, false, 61, 63), "the shore: land");
        assertEquals(CrownGuess.Kind.SNOW, CrownGuess.classify(false, true, true, 80, 63));
        assertEquals(CrownGuess.Kind.SAND, CrownGuess.classify(false, true, false, 66, 63));
        assertEquals(CrownGuess.Kind.GRASS, CrownGuess.classify(false, false, false, 90, 63));
    }

    @Test
    @DisplayName("shading: shallow water bright and deep dark; land lit rising past two blocks, shaded falling")
    void shade() {
        assertEquals(CrownSampleShade.HIGH, CrownGuess.shade(CrownGuess.Kind.WATER, 60, 0, 63));
        assertEquals(CrownSampleShade.NORMAL, CrownGuess.shade(CrownGuess.Kind.WATER, 50, 0, 63));
        assertEquals(CrownSampleShade.LOW, CrownGuess.shade(CrownGuess.Kind.WATER, 40, 0, 63));
        assertEquals(CrownSampleShade.HIGH, CrownGuess.shade(CrownGuess.Kind.GRASS, 72, 70, 63));
        assertEquals(CrownSampleShade.NORMAL, CrownGuess.shade(CrownGuess.Kind.GRASS, 71, 70, 63), "a block is noise");
        assertEquals(CrownSampleShade.LOW, CrownGuess.shade(CrownGuess.Kind.SAND, 66, 68, 63));
    }

    @Test
    @DisplayName("the wire byte round trips, 0 is no guess, and no guess draws the map paper")
    void encode() {
        for (CrownGuess.Kind k : CrownGuess.Kind.values()) {
            for (int s = 0; s < 3; s++) {
                final byte b = CrownGuess.encode(k, s);
                assertEquals(k, CrownGuess.kind(b));
                if (k != CrownGuess.Kind.NONE) {
                    assertEquals(s, CrownGuess.shadeOf(b));
                }
            }
        }
        assertEquals(0, CrownGuess.encode(CrownGuess.Kind.NONE, 2));
        assertEquals(CrownGuess.PAPER, CrownGuess.argb((byte) 0));
        assertEquals(0xFFD6BE96, CrownGuess.PAPER, "mapkit._paper: 214, 190, 150");
    }

    @Test
    @DisplayName("flat ground and a typical sea draw the mock's colours, mixed 35% toward the paper (v11 nojm)")
    void mockColours() {
        assertEquals(faint(0xFF308CBE), CrownGuess.argb(CrownGuess.encode(CrownGuess.Kind.WATER, CrownSampleShade.NORMAL)),
                "ocean 48, 140, 190");
        assertEquals(faint(0xFFDECE96), CrownGuess.argb(CrownGuess.encode(CrownGuess.Kind.SAND, CrownSampleShade.NORMAL)),
                "beach 222, 206, 150");
        assertEquals(faint(0xFF70A046), CrownGuess.argb(CrownGuess.encode(CrownGuess.Kind.GRASS, CrownSampleShade.NORMAL)),
                "grass 112, 160, 70");
        final int lit = CrownGuess.argb(CrownGuess.encode(CrownGuess.Kind.GRASS, CrownSampleShade.HIGH));
        final int dark = CrownGuess.argb(CrownGuess.encode(CrownGuess.Kind.GRASS, CrownSampleShade.LOW));
        assertEquals(true, (lit >> 8 & 0xFF) > (dark >> 8 & 0xFF), "lit is lighter than shaded");
    }

    @Test
    @DisplayName("the surface: where the depth field's line crosses zero; none when it does not change with height")
    void surface() {
        assertEquals(64, CrownGuess.surface(1.0, -1.0, 0, 128));
        assertEquals(48, CrownGuess.surface(0.375, -0.625, 0, 128), "the measured column (-1500, -2000): true 48");
        assertEquals(Integer.MIN_VALUE, CrownGuess.surface(0.5, 0.5, 0, 128), "flat field");
        assertEquals(Integer.MIN_VALUE, CrownGuess.surface(Double.NaN, 0.5, 0, 128));
        assertEquals(4096, CrownGuess.surface(1e9, 1e9 - 1e-6, 0, 128), "clamped, never an overflow");
    }
}
