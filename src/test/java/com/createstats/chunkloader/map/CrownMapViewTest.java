package com.createstats.chunkloader.map;

import com.createstats.chunkloader.core.CrownRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownMapViewTest {
    private static long c(int x, int z) {
        return CrownRegion.pack(x, z);
    }

    @Test
    @DisplayName("853 x 512 opens on the region at one block a pixel, 170 blocks of surround on the left (v11 mid)")
    void opensLikeTheMock() {
        final CrownMapView v = new CrownMapView(0, -1, 853, 512);
        assertEquals(16, v.zoom());
        assertEquals(1.0, v.scale());
        assertEquals(-170.0, v.originX(), "mapgrid.region_view: rx0 * 16 + 256 - 853 // 2");
        assertEquals(-512.0, v.originZ());
        assertArrayEquals(new int[] {170, 0, 682, 512}, v.regionRect());
        assertEquals(-16, v.windowChunkX());
        assertEquals(-48, v.windowChunkZ());
    }

    @Test
    @DisplayName("640 x 339 opens with the region's middle in the middle (v11 small)")
    void opensNarrowLikeTheMock() {
        final CrownMapView v = new CrownMapView(0, -1, 640, 339);
        assertEquals(-64.0, v.originX(), "256 - 640 // 2");
        assertEquals(-512.0 + 87, v.originZ(), "256 - 339 // 2");
    }

    @Test
    @DisplayName("zoom steps in powers of two, 8 to 64 pixels a chunk, and stops at both ends")
    void zoomSteps() {
        final CrownMapView v = new CrownMapView(0, 0, 853, 512);
        assertTrue(v.zoomAbout(400, 200, 1));
        assertEquals(32, v.zoom());
        assertTrue(v.zoomAbout(400, 200, 1));
        assertEquals(64, v.zoom());
        assertFalse(v.zoomAbout(400, 200, 1), "no zoom past 64");
        assertEquals(64, v.zoom());
        assertTrue(v.zoomAbout(400, 200, -5));
        assertEquals(8, v.zoom(), "a big step clamps to the smallest zoom");
        assertFalse(v.zoomAbout(400, 200, -1));
        for (int z : CrownMapView.ZOOMS) {
            assertEquals(0, z & (z - 1), "a power of two: " + z);
        }
    }

    @Test
    @DisplayName("zooming keeps the block under the pointer under it, when the clamp allows")
    void zoomAboutThePointer() {
        final CrownMapView v = new CrownMapView(0, 0, 853, 512);
        final int bx = v.blockAtX(500);
        final int bz = v.blockAtZ(300);
        v.zoomAbout(500, 300, 1);
        assertEquals(bx, v.blockAtX(500));
        assertEquals(bz, v.blockAtZ(300));
        v.zoomAbout(500, 300, 1);
        assertEquals(bx, v.blockAtX(500));
        assertEquals(bz, v.blockAtZ(300));
    }

    @Test
    @DisplayName("the chunk under every pixel of a chunk is that chunk, at every zoom, and its edges are whole pixels")
    void chunkUnderThePointer() {
        for (int zoom : CrownMapView.ZOOMS) {
            final CrownMapView v = new CrownMapView(-1, 2, 853, 512);
            while (v.zoom() != zoom) {
                v.zoomAbout(426, 256, v.zoom() < zoom ? 1 : -1);
            }
            v.pan(37, -53);
            for (int cx = -40; cx < 0; cx++) {
                final int x0 = v.chunkPixelX(cx);
                assertEquals(zoom, v.chunkPixelX(cx + 1) - x0, "a chunk is " + zoom + " pixels at zoom " + zoom);
                for (int k = 0; k < zoom; k++) {
                    assertEquals(cx, CrownRegion.x(v.chunkAt(x0 + k + 0.5, 100)), "zoom " + zoom + " pixel " + k);
                }
                assertEquals(cx, CrownRegion.x(v.chunkAt(x0, 100)), "the west edge pixel is the chunk's own");
            }
            final double s = v.scale();
            assertEquals(Math.rint(v.originX() * s), v.originX() * s, 1e-9, "on the pixel grid at zoom " + zoom);
        }
    }

    @Test
    @DisplayName("the pan is clamped to the region and its surround, and a view wider than that is centred")
    void panClamp() {
        final CrownMapView v = new CrownMapView(2, -3, 853, 512);
        final double w0 = (2 * 32 - 16) * 16.0;
        final double z0 = (-3 * 32 - 16) * 16.0;
        v.pan(100_000, 100_000);
        assertEquals(w0, v.originX(), "dragged far right: the window's west edge stops at the map's");
        assertEquals(z0, v.originZ());
        v.pan(-100_000, -100_000);
        assertEquals(w0 + 1024 - 853, v.originX());
        assertEquals(z0 + 1024 - 512, v.originZ());
        v.zoomAbout(400, 200, -1);
        assertEquals(8, v.zoom());
        assertEquals(Math.round((w0 - (1706 - 1024) / 2.0) * 0.5) / 0.5, v.originX(),
                "1,706 blocks of view on a 1,024 block window: centred, then onto the half-pixel grid");
        assertEquals(z0, v.originZ(), "512 pixels at half a pixel a block is exactly the window");
        assertEquals(0, ((long) v.originX()) % 2, "half a pixel a block: an even block, or half a pixel is a place");
    }

    @Test
    @DisplayName("the region's rectangle and the window follow negative regions by floor, not truncation")
    void negativeRegions() {
        final CrownMapView v = new CrownMapView(-1, -1, 853, 512);
        assertEquals(-48, v.windowChunkX());
        assertTrue(v.inWindow(-48, -48));
        assertTrue(v.inWindow(15, 15));
        assertFalse(v.inWindow(16, 0));
        assertFalse(v.inWindow(-49, 0));
        final int[] r = v.regionRect();
        assertEquals(512, r[2] - r[0]);
        assertEquals(512, r[3] - r[1]);
    }

    @Test
    @DisplayName("a straight drag paints every chunk it crosses, ends included, in drag order")
    void line() {
        assertEquals(List.of(c(12, -25), c(12, -24), c(12, -23)), CrownMapView.line(12, -25, 12, -23),
                "v11 paint: three chunks down column 12");
        assertEquals(List.of(c(0, 0)), CrownMapView.line(0, 0, 0, 0));
        final List<Long> diag = CrownMapView.line(0, 0, 3, -3);
        assertEquals(c(0, 0), diag.get(0));
        assertEquals(c(3, -3), diag.get(diag.size() - 1));
        for (int i = 1; i < diag.size(); i++) {
            final int dx = Math.abs(CrownRegion.x(diag.get(i)) - CrownRegion.x(diag.get(i - 1)));
            final int dz = Math.abs(CrownRegion.z(diag.get(i)) - CrownRegion.z(diag.get(i - 1)));
            assertTrue(dx <= 1 && dz <= 1, "no gaps");
        }
    }

    @Test
    @DisplayName("a rectangle paint is every chunk between the corners, row by row from the start corner")
    void rectangle() {
        assertEquals(List.of(c(5, 5), c(4, 5), c(5, 4), c(4, 4)), CrownMapView.rectangle(5, 5, 4, 4));
        assertEquals(12, CrownMapView.rectangle(0, 0, 3, 2).size());
        assertEquals(List.of(c(7, 7)), CrownMapView.rectangle(7, 7, 7, 7));
    }

    @Test
    @DisplayName("a path paint fills the jumps of a fast drag and takes each chunk once, first crossing first")
    void path() {
        final List<Long> p = CrownMapView.path(List.of(c(0, 0), c(3, 0), c(3, 0), c(2, 0)));
        assertEquals(List.of(c(0, 0), c(1, 0), c(2, 0), c(3, 0)), p);
    }
}
