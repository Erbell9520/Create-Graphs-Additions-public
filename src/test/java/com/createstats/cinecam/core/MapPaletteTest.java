package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPaletteTest {
    private static final int[] PALETTE = {0x000000, 0x404040, 0x808080, 0xFFFFFF, 0x7FB238, 0x4040FF};
    private static final byte[] IDS = {4, 5, 6, 7, 8, 9};

    @Test
    void aPaletteColourMapsToItsOwnId() {
        int n = MapPalette.SIZE * MapPalette.SIZE;
        for (int k = 0; k < PALETTE.length; k++) {
            int[] img = new int[n];
            java.util.Arrays.fill(img, PALETTE[k]);
            byte[] out = MapPalette.quantize(img, PALETTE, IDS, true);
            for (byte b : out) assertEquals(IDS[k], b);
        }
    }

    @Test
    void theMiddleSquareOfAWideFrameIsKept() {
        int w = 512, h = 256;
        int[] frame = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                frame[y * w + x] = (x < 128 || x >= 384) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        int[] sq = MapPalette.squareDown(frame, w, h);
        for (int v : sq) assertEquals(0xFFFFFF, v);
    }

    @Test
    void ditheringKeepsAGreyThatNoMapColourHas() {
        int n = MapPalette.SIZE * MapPalette.SIZE;
        int[] img = new int[n];
        java.util.Arrays.fill(img, 0x606060);
        byte[] out = MapPalette.quantize(img, PALETTE, IDS, true);
        long sum = 0;
        for (byte b : out) {
            int k = b - 4;
            sum += PALETTE[k] & 0xFF;
        }
        double mean = sum / (double) n;

        assertTrue(Math.abs(mean - 0x60) < 4.0, "mean " + mean);
    }

    @Test
    void aPrintAcrossFourMapsIsDitheredWhole() {
        int size = 2 * MapPalette.SIZE;
        int w = 1024, h = 640;
        int[] frame = new int[w * h];
        java.util.Arrays.fill(frame, 0xFF606060);
        int[] sq = MapPalette.squareDown(frame, w, h, size);
        assertEquals(size * size, sq.length);
        byte[] out = MapPalette.quantize(sq, size, PALETTE, IDS, true);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                byte[] tile = PhotoPrint.tile(out, 2, row, col);
                long sum = 0;
                for (byte b : tile) sum += PALETTE[b - 4] & 0xFF;
                double mean = sum / (double) tile.length;
                assertTrue(Math.abs(mean - 0x60) < 6.0, "map " + row + ", " + col + " mean " + mean);
            }
        }
    }
}
