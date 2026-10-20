package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoZoomTest {
    private static double share(double distance, double radius, double zoom, double tanHalf) {
        return radius * zoom / (distance * tanHalf);
    }

    @Test
    void theSubjectIsHeldAtItsFraming() {
        double tanHalf = AutoZoom.tanHalf(70, 16.0 / 10.0, 0.0);

        for (double d = 20; d <= 80; d += 10) {
            double z = AutoZoom.zoomFor(d, 0.9, 0.5, tanHalf, 8.0);
            if (z < 8.0 && z > 1.0) assertEquals(0.5, share(d, 0.9, z, tanHalf), 1e-9, "at " + d + " blocks");
        }

        assertEquals(2.0, AutoZoom.zoomFor(20, 0.9, 0.25, tanHalf, 8.0) / AutoZoom.zoomFor(10, 0.9, 0.25, tanHalf, 8.0), 1e-9);
    }

    @Test
    void withinTheLensesRange() {
        double tanHalf = AutoZoom.tanHalf(70, 16.0 / 9.0, 0.0);
        assertEquals(1.0, AutoZoom.zoomFor(2, 0.9, 0.5, tanHalf, 8.0), 0.0, "too close to frame that tight: as wide as the lens goes");
        assertEquals(8.0, AutoZoom.zoomFor(5000, 0.9, 0.5, tanHalf, 8.0), 0.0, "too far: as long as it goes");
        assertEquals(1.0, AutoZoom.zoomFor(0, 0.9, 0.5, tanHalf, 8.0), 0.0, "nothing to frame");
        assertEquals(1.0, AutoZoom.zoomFor(40, 0.0, 0.5, tanHalf, 8.0), 0.0, "no size");
        assertEquals(1.0, AutoZoom.zoomFor(Double.NaN, 0.9, 0.5, tanHalf, 8.0), 0.0);
    }

    @Test
    void theLetterboxIsThePicture() {
        double window = 16.0 / 10.0;
        double t = Math.tan(Math.toRadians(70) / 2.0);
        assertEquals(t, AutoZoom.tanHalf(70, window, 0.0), 1e-12, "no bars: the window's height");
        assertEquals(t * window / 2.39, AutoZoom.tanHalf(70, window, 2.39), 1e-12, "2.39: its share of the height");
        assertEquals(t, AutoZoom.tanHalf(70, 2.5, 2.39), 1e-12, "a window wider than the frame shows no bars across");
        double inFilm = AutoZoom.zoomFor(15, 0.9, 0.5, AutoZoom.tanHalf(70, window, 2.39), 8.0);
        double bare = AutoZoom.zoomFor(15, 0.9, 0.5, AutoZoom.tanHalf(70, window, 0.0), 8.0);
        assertTrue(bare < 8.0, "inside the lens's range, so the comparison means something");
        assertTrue(inFilm < bare, "a shorter picture needs less zoom to hold the same share of it");
    }

    @Test
    void theWheelNudgesTheFramingWithinLimits() {
        assertEquals(0.5 * 1.25, AutoZoom.nudge(0.5, 1), 1e-12);
        assertEquals(0.5 / 1.25, AutoZoom.nudge(0.5, -1), 1e-12);
        assertEquals(AutoZoom.MAX_SHARE, AutoZoom.nudge(0.9, 5), 0.0, "never more than the picture");
        assertEquals(AutoZoom.MIN_SHARE, AutoZoom.nudge(0.1, -20), 0.0, "never nothing");
    }

    @Test
    void theFramingsAreKeptAndRead() {
        assertEquals(AutoZoom.Framing.OFF, AutoZoom.DEFAULT, "off unless chosen: a camera from before films as it did");
        assertFalse(AutoZoom.Framing.OFF.on());
        double last = 0.0;
        for (AutoZoom.Framing f : AutoZoom.Framing.values()) {
            assertEquals(f, AutoZoom.Framing.byKey(f.key()), "saved and read back");
            assertEquals(f, AutoZoom.Framing.byIndex(f.ordinal()), "sent and read back");
            if (f.on()) {
                assertTrue(f.share() > last && f.share() < 1.0, "tighter in turn: " + f);
                last = f.share();
            }
        }
        assertEquals(AutoZoom.Framing.OFF, AutoZoom.Framing.byKey(""), "nothing saved");
        assertEquals(AutoZoom.Framing.OFF, AutoZoom.Framing.byIndex(99), "a bad packet");
        assertEquals(AutoZoom.Framing.OFF, AutoZoom.Framing.CLOSE.next(), "the menu goes round");
        assertTrue(AutoZoom.TAU > FocusPull.TAU_ZOOM, "it rides the zoom slower than a hand turns it");
    }
}
