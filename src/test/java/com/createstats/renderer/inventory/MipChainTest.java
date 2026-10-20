package com.createstats.renderer.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MipChainTest {
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @Test
    void levelZeroIsTheInputAndEachLevelHalves() {
        final int[] img = new int[16 * 8];
        final int[][] levels = MipChain.build(img, 16, 8, 3);
        assertEquals(4, levels.length);
        assertSame(img, levels[0]);
        assertEquals(8 * 4, levels[1].length);
        assertEquals(4 * 2, levels[2].length);
        assertEquals(2, levels[3].length);
    }

    @Test
    void aCheckerAveragesToGrey() {
        final int[] img = {BLACK, WHITE, WHITE, BLACK};
        final int[][] levels = MipChain.build(img, 2, 2, 1);
        final int grey = levels[1][0];
        assertEquals(255, grey >>> 24);
        assertEquals(128, (grey >> 16) & 255);
        assertEquals(128, (grey >> 8) & 255);
        assertEquals(128, grey & 255);
    }

    @Test
    void transparentTexelsDoNotDarkenTheColour() {
        final int amber = 0xFFFFB040;
        final int[] img = {amber, 0, 0, 0};
        final int texel = MipChain.build(img, 2, 2, 1)[1][0];
        assertEquals(64, texel >>> 24);
        assertEquals(0xFFB040, texel & 0xFFFFFF);
    }

    @Test
    void allTransparentStaysTransparent() {
        assertEquals(0, MipChain.average(0, 0, 0x00FFFFFF, 0));
    }

    @Test
    void theGlassHalvesNeverMixAtAnyLevel() {
        final int w = 224;
        final int h = 384;
        final int[] img = new int[w * h];
        for (int i = 0; i < w * 192; i++) {
            img[i] = 0xFF0B1410;
        }
        final int[][] levels = MipChain.build(img, w, h, 4);
        for (int level = 1; level <= 4; level++) {
            final int lw = w >> level;
            final int seam = 192 >> level;
            final int[] px = levels[level];
            for (int x = 0; x < lw; x++) {
                assertEquals(0xFF0B1410, px[(seam - 1) * lw + x], "BASE's last row at level " + level);
                assertEquals(0, px[seam * lw + x], "OVER's first row at level " + level);
            }
        }
    }

    @Test
    void sidesThatDoNotDivideAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> MipChain.build(new int[138 * 70], 138, 70, 2));
        assertThrows(IllegalArgumentException.class, () -> MipChain.build(new int[3], 2, 2, 1));
    }

    @Test
    void paddingRepeatsTheLastColumnAndRow() {
        final int[] src = {1, 2, 3, 4, 5, 6};
        final int[] out = MipChain.pad(src, 3, 2, 4, 3);
        assertArrayEquals(new int[]{1, 2, 3, 3, 4, 5, 6, 6, 4, 5, 6, 6}, out);
    }

    @Test
    void blitPlacesARectangle() {
        final int[] dst = new int[4 * 3];
        MipChain.blit(new int[]{7, 8, 9, 10}, 2, 2, dst, 4, 1, 1);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0, 7, 8, 0, 0, 9, 10, 0}, dst);
    }
}
