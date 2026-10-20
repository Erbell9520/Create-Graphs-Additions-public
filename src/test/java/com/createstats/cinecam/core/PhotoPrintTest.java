package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoPrintTest {
    @Test
    void theTargetsSpendAndSaveWhatTheySay() {
        assertTrue(PhotoPrint.Target.MAP.maps() && !PhotoPrint.Target.MAP.file());
        assertTrue(!PhotoPrint.Target.FILE.maps() && PhotoPrint.Target.FILE.file(), "a file spends no maps");
        assertTrue(PhotoPrint.Target.BOTH.maps() && PhotoPrint.Target.BOTH.file());
        assertEquals(PhotoPrint.Target.MAP, PhotoPrint.Target.byKey("nonsense"), "an unknown save reads as the default");
    }

    @Test
    void aSideOfMapsIsItsSquareAndItsPixels() {
        assertEquals(1, PhotoPrint.mapsFor(1));
        assertEquals(4, PhotoPrint.mapsFor(2));
        assertEquals(16, PhotoPrint.mapsFor(4));
        assertEquals(16, PhotoPrint.mapsFor(9), "clamped to four a side");
        assertEquals(1, PhotoPrint.mapsFor(0), "and to one");
        assertEquals(256, PhotoPrint.pixelsFor(2));
        assertEquals(512, PhotoPrint.pixelsFor(4));
    }

    @Test
    void theSettingsSurviveThePacket() {
        for (PhotoPrint.Target t : PhotoPrint.Target.values()) {
            for (int side = PhotoPrint.MIN_SIDE; side <= PhotoPrint.MAX_SIDE; side++) {
                int p = PhotoPrint.pack(t, side);
                assertEquals(t, PhotoPrint.targetOf(p));
                assertEquals(side, PhotoPrint.sideOf(p));
            }
        }
        assertEquals(PhotoPrint.Target.BOTH, PhotoPrint.targetOf(0xF), "a bad packet reads as the nearest setting");
        assertEquals(PhotoPrint.MAX_SIDE, PhotoPrint.sideOf(0xF0));
        assertEquals(PhotoPrint.MIN_SIDE, PhotoPrint.sideOf(0));
    }

    @Test
    void everyPixelLandsOnItsMapAtItsPlace() {
        int side = 3;
        int w = side * PhotoPrint.MAP_PIXELS;
        byte[] print = new byte[w * w];

        for (int y = 0; y < w; y++) {
            for (int x = 0; x < w; x++) {
                print[y * w + x] = (byte) ((y / PhotoPrint.MAP_PIXELS) * side + x / PhotoPrint.MAP_PIXELS);
            }
        }
        for (int row = 0; row < side; row++) {
            for (int col = 0; col < side; col++) {
                byte[] tile = PhotoPrint.tile(print, side, row, col);
                assertEquals(PhotoPrint.MAP_PIXELS * PhotoPrint.MAP_PIXELS, tile.length);
                for (byte b : tile) assertEquals(row * side + col, b, "map " + row + ", " + col);
            }
        }

        print[PhotoPrint.MAP_PIXELS * w + 2 * PhotoPrint.MAP_PIXELS - 1] = 99;
        assertEquals(99, PhotoPrint.tile(print, side, 1, 1)[PhotoPrint.MAP_PIXELS - 1], "top right of the middle map");
        assertThrows(IllegalArgumentException.class, () -> PhotoPrint.tile(new byte[10], 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PhotoPrint.tile(print, side, 3, 0));
        assertFalse(PhotoPrint.Target.FILE.maps());
        assertArrayEquals(PhotoPrint.tile(print, side, 0, 0), PhotoPrint.tile(print, side, 0, 0));
    }
}
