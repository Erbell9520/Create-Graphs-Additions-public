package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapFitTest {
    @Test
    @DisplayName("a 24 by 18 house fills a 189 px box at 7 px a block, centred")
    void fitsTheHouse() {
        final MapFit f = MapFit.fit(189, 1, 1, 24, 18, 0, 0, 0);
        assertEquals(6, f.scale(), "28 blocks with margin do not fit at 7 px (196), so 6");
        assertTrue(f.visible(1, 1));
        assertTrue(f.visible(24, 18));
        final int left = f.px(1);
        final int right = 189 - (f.px(24) + f.scale());
        assertTrue(Math.abs(left - right) <= f.scale(), "centred to within a block: " + left + " vs " + right);
    }

    @Test
    @DisplayName("every block is the same whole number of pixels, at every zoom")
    void wholePixels() {
        for (int zoom = -3; zoom <= 5; zoom++) {
            final MapFit f = MapFit.fit(189, -5, -5, 30, 20, zoom, 0, 0);
            assertEquals(f.scale(), f.px(11) - f.px(10));
            assertEquals(f.scale(), f.pz(-2) - f.pz(-3));
        }
    }

    @Test
    @DisplayName("pixel to block and back agree")
    void roundTrip() {
        final MapFit f = MapFit.fit(189, 100, -40, 130, -10, 1, 3, -2);
        for (int x = 100; x <= 130; x += 7) {
            assertEquals(x, f.blockX(f.px(x)));
            assertEquals(x, f.blockX(f.px(x) + f.scale() - 1));
        }
        for (int z = -40; z <= -10; z += 5) {
            assertEquals(z, f.blockZ(f.pz(z)));
        }
    }

    @Test
    @DisplayName("one lamp is not a map of one block: the fit never shows fewer than the minimum span")
    void minimumSpan() {
        final MapFit f = MapFit.fit(189, 0, 0, 0, 0, 0, 0, 0);
        assertTrue(f.lastX() - f.firstX() + 1 >= MapFit.MIN_SPAN);
    }

    @Test
    @DisplayName("a fixture far across the estate does not shrink the house to a speck")
    void maximumSpan() {
        final MapFit f = MapFit.fit(189, 0, 0, 400, 10, 5, 5, 0, 0, 0);
        assertEquals(MapFit.MIN_FIT_SCALE, f.scale(), "never below the scale a mark needs");
        assertTrue(f.visible(5, 5), "the view is round the panel, where the player stands");
        assertTrue(f.visible(0, 0) && f.visible(10, 10), "the house itself is on the map");
        assertFalse(f.visible(400, 5), "the far fixture is off the map, to be pinned to its edge");
        assertEquals(188 - 3, f.clampInside(5000, 3));
        assertEquals(3, f.clampInside(-50, 3));
    }

    @Test
    @DisplayName("marks never overlap at the fitted scale: a big house is not squeezed below four pixels a block")
    void neverBelowAMark() {
        final MapFit f = MapFit.fit(189, 0, 0, 90, 60, 45, 30, 0, 0, 0);
        assertEquals(MapFit.MIN_FIT_SCALE, f.scale());

        assertEquals(6, MapFit.fit(189, 1, 1, 24, 18, 12, 9, 0, 0, 0).scale());
    }

    @Test
    @DisplayName("zoom steps are clamped to the scale table at both ends")
    void zoomClamps() {
        assertEquals(MapFit.SCALES[MapFit.SCALES.length - 1], MapFit.fit(189, 0, 0, 20, 20, 99, 0, 0).scale());
        assertEquals(MapFit.SCALES[0], MapFit.fit(189, 0, 0, 20, 20, -99, 0, 0).scale());
    }

    @Test
    @DisplayName("a zoom step keeps the block under the cursor under the cursor, to within a block, in and out")
    void zoomAboutTheCursor() {
        final int box = 242;
        final int cx = 200;
        final int cz = 220;
        int panX = 0;
        int panZ = 0;
        for (int zoom = 0; zoom < 4; zoom++) {
            final MapFit before = MapFit.fit(box, 0, 0, 60, 40, 30, 20, zoom, panX, panZ);
            final int bx = before.blockX(cx);
            final int bz = before.blockZ(cz);
            final int[] pan = MapFit.panToKeep(box, 0, 0, 60, 40, 30, 20, zoom, zoom + 1, panX, panZ, cx, cz);
            final MapFit after = MapFit.fit(box, 0, 0, 60, 40, 30, 20, zoom + 1, pan[0], pan[1]);
            assertTrue(Math.abs(after.blockX(cx) - bx) <= 1 && Math.abs(after.blockZ(cz) - bz) <= 1,
                    "zoom " + zoom + " to " + (zoom + 1) + ": the block under the cursor stayed near it");

            final MapFit naive = MapFit.fit(box, 0, 0, 60, 40, 30, 20, zoom + 1, panX, panZ);
            assertTrue(Math.abs(naive.blockX(cx) - bx) > 1 || Math.abs(naive.blockZ(cz) - bz) > 1,
                    "zoom " + zoom + ": zooming about the centre moves the corner");
            panX = pan[0];
            panZ = pan[1];
        }

        for (int zoom = 4; zoom > 0; zoom--) {
            final MapFit before = MapFit.fit(box, 0, 0, 60, 40, 30, 20, zoom, panX, panZ);
            final int bx = before.blockX(cx);
            final int bz = before.blockZ(cz);
            final int[] pan = MapFit.panToKeep(box, 0, 0, 60, 40, 30, 20, zoom, zoom - 1, panX, panZ, cx, cz);
            final MapFit after = MapFit.fit(box, 0, 0, 60, 40, 30, 20, zoom - 1, pan[0], pan[1]);
            assertTrue(Math.abs(after.blockX(cx) - bx) <= 1 && Math.abs(after.blockZ(cz) - bz) <= 1, "out at " + zoom);
            panX = pan[0];
            panZ = pan[1];
        }
        assertTrue(Math.abs(panX) <= 1 && Math.abs(panZ) <= 1, "back where it began: " + panX + "," + panZ);
    }

    @Test
    @DisplayName("a pan moves the view by whole blocks")
    void pan() {
        final MapFit a = MapFit.fit(189, 0, 0, 20, 20, 0, 0, 0);
        final MapFit b = MapFit.fit(189, 0, 0, 20, 20, 0, 4, -2);
        assertEquals(a.px(10) - 4 * a.scale(), b.px(10));
        assertEquals(a.pz(10) + 2 * a.scale(), b.pz(10));
    }
}
