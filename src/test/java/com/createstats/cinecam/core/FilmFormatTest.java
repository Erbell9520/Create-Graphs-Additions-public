package com.createstats.cinecam.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilmFormatTest {
    private static final int SW = 2560;
    private static final int SH = 1600;

    @Test
    void theResolutionsAreTheirWidthsAtTheFramesShape() {
        assertArrayEquals(new int[]{1920, 804}, FilmFormat.size(FilmFormat.Resolution.FULL_HD, 2.39, SW, SH), "1080p scope");
        assertArrayEquals(new int[]{1920, 1038}, FilmFormat.size(FilmFormat.Resolution.FULL_HD, 1.85, SW, SH), "1080p flat");
        assertArrayEquals(new int[]{1920, 1080}, FilmFormat.size(FilmFormat.Resolution.FULL_HD, 16.0 / 9.0, SW, SH), "1080p video");
        assertArrayEquals(new int[]{1280, 720}, FilmFormat.size(FilmFormat.Resolution.HD, 16.0 / 9.0, SW, SH));
        assertArrayEquals(new int[]{2560, 1072}, FilmFormat.size(FilmFormat.Resolution.QHD, 2.39, SW, SH));
        assertArrayEquals(new int[]{2560, 1072}, FilmFormat.size(FilmFormat.Resolution.NATIVE, 2.39, SW, SH), "the window's own");
        assertArrayEquals(new int[]{2560, 1440}, FilmFormat.size(FilmFormat.Resolution.NATIVE, 16.0 / 9.0, SW, SH));
    }

    @Test
    void nothingIsFilmedWiderThanTheWindowDraws() {
        assertFalse(FilmFormat.fits(FilmFormat.Resolution.UHD, 2.39, SW, SH), "4K on a 2560 window");
        assertArrayEquals(new int[]{2560, 1072}, FilmFormat.size(FilmFormat.Resolution.UHD, 2.39, SW, SH), "films at Native instead");
        assertTrue(FilmFormat.fits(FilmFormat.Resolution.UHD, 2.39, 3840, 2160), "a 4K window films 4K");
        assertArrayEquals(new int[]{3840, 1608}, FilmFormat.size(FilmFormat.Resolution.UHD, 2.39, 3840, 2160));
        assertFalse(FilmFormat.fits(FilmFormat.Resolution.FULL_HD, 16.0 / 9.0, 1366, 768), "1080p on a laptop");
        assertTrue(FilmFormat.fits(FilmFormat.Resolution.HD, 16.0 / 9.0, 1366, 768));
        assertTrue(FilmFormat.fits(FilmFormat.Resolution.FULL_HD, 2.39, 1920, 1080), "a 1080p window films 1080p scope");

        assertArrayEquals(new int[]{2560, 1440}, FilmFormat.nativeSize(16.0 / 9.0, 3440, 1440));
        assertFalse(FilmFormat.fits(FilmFormat.Resolution.UHD, 16.0 / 9.0, 3440, 1440));
    }

    @Test
    void everySizeIsOneTheEncoderTakesAndFitsTheWindow() {
        for (FilmFormat.Resolution r : FilmFormat.Resolution.values()) {
            for (FilmFormat.Shape s : FilmFormat.Shape.values()) {
                for (int[] win : new int[][]{{2560, 1600}, {1920, 1080}, {1366, 768}, {3440, 1440}, {3840, 2160}, {800, 600}, {1001, 999}}) {
                    int[] f = FilmFormat.size(r, s.aspect(), win[0], win[1]);
                    String at = r + " " + s + " on " + win[0] + "x" + win[1];
                    assertEquals(0, f[0] % 16, at + " width " + f[0]);
                    assertEquals(0, f[1] % 2, at + " height " + f[1]);
                    assertTrue(f[0] <= win[0] && f[1] <= win[1], at + " fits the window: " + f[0] + "x" + f[1]);
                    assertTrue(f[0] <= FilmFormat.MAX_WIDTH, at);
                }
            }
        }
    }

    @Test
    void theSettingsSurviveThePacket() {
        for (int rate : FilmFormat.RATES) {
            for (FilmFormat.Resolution r : FilmFormat.Resolution.values()) {
                for (FilmFormat.Quality q : FilmFormat.Quality.values()) {
                    for (FilmFormat.Shape s : FilmFormat.Shape.values()) {
                        for (FilmFormat.Speed v : FilmFormat.Speed.values()) {
                            int p = FilmFormat.pack(rate, r, q, s, v);
                            assertEquals(rate, FilmFormat.rateOf(p));
                            assertEquals(r, FilmFormat.resolutionOf(p));
                            assertEquals(q, FilmFormat.qualityOf(p));
                            assertEquals(s, FilmFormat.shapeOf(p));
                            assertEquals(v, FilmFormat.speedOf(p));
                        }
                    }
                }
            }
        }

        assertEquals(60, FilmFormat.rateOf(0xF));
        assertEquals(FilmFormat.Resolution.UHD, FilmFormat.resolutionOf(0xF0));
        assertEquals(FilmFormat.Shape.VIDEO, FilmFormat.shapeOf(-1));
        assertEquals(FilmFormat.Speed.LAPSE_60, FilmFormat.speedOf(-1));
    }

    @Test
    void aFormatFromBeforeSpeedsFilmsAtNormal() {
        int old = 2 | FilmFormat.Resolution.UHD.ordinal() << 4 | FilmFormat.Quality.HIGH.ordinal() << 8
                | FilmFormat.Shape.FLAT.ordinal() << 12;
        assertEquals(FilmFormat.Speed.NORMAL, FilmFormat.speedOf(old));
        assertEquals(FilmFormat.Shape.FLAT, FilmFormat.shapeOf(old));
        assertEquals(FilmFormat.Speed.NORMAL, FilmFormat.Speed.values()[0]);
        assertEquals(FilmFormat.Speed.NORMAL, FilmFormat.Speed.byKey(""), "no Speed saved");
        assertEquals(FilmFormat.DEFAULT_SPEED, FilmFormat.Speed.NORMAL);
    }

    @Test
    void theSpeedsAreWhatTheySay() {
        assertEquals(java.util.List.of(FilmFormat.Speed.values()), java.util.List.of(FilmFormat.SPEED_CYCLE),
                "the menu cycles through every speed, Normal, slower, then faster");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (FilmFormat.Speed v : FilmFormat.Speed.values()) {
            assertTrue(keys.add(v.key()), "a key once");
            assertEquals(v, FilmFormat.Speed.byKey(v.key()), "saved and read back");
            assertEquals(v.factor() < 1.0, v.slow());
            assertEquals(v.factor() > 1.0, v.lapse());
        }
        assertEquals("1x", FilmFormat.Speed.NORMAL.label());
        assertEquals("1/2x", FilmFormat.Speed.SLOW_HALF.label());
        assertEquals("1/4x", FilmFormat.Speed.SLOW_QUARTER.label());
        assertEquals("10x", FilmFormat.Speed.LAPSE_10.label());
        assertEquals("60x", FilmFormat.Speed.LAPSE_60.label());
        assertEquals(0.5, FilmFormat.Speed.SLOW_HALF.factor(), 0.0);
        assertEquals(60.0, FilmFormat.Speed.LAPSE_60.factor(), 0.0);
    }

    @Test
    void theRatesAreRealCamerasAndTheShapesGoRound() {
        assertEquals(30, FilmFormat.DEFAULT_RATE);
        assertEquals(24, FilmFormat.clampRate(23));
        assertEquals(60, FilmFormat.clampRate(144));
        assertEquals(FilmFormat.Shape.FLAT, FilmFormat.Shape.SCOPE.next());
        assertEquals(FilmFormat.Shape.SCOPE, FilmFormat.Shape.VIDEO.next(), "round from the last to the first");
        assertEquals(FilmFormat.Resolution.FULL_HD, FilmFormat.Resolution.byKey("nonsense"), "an unknown save reads as the default");
    }

    @Test
    void aTakeIsNamedSafelyAfterWhatItFollowed() {
        assertEquals("Bolat's Truck", FilmFormat.fileSafe("Bolat's Truck"));
        assertEquals("Ship abcd", FilmFormat.fileSafe("Ship a/b:c*d?<>|\"\\"), "the characters Windows forbids are dropped");
        assertEquals("tab here", FilmFormat.fileSafe("tab\there"), "control characters dropped");
        assertEquals("spaces", FilmFormat.fileSafe("  spaces   "), "trimmed");
        assertEquals("a b", FilmFormat.fileSafe("a    b"), "runs of spaces made one");
        assertEquals("Trailing", FilmFormat.fileSafe("Trailing..."), "no trailing dots, which Windows strips");
        assertEquals(40, FilmFormat.fileSafe("x".repeat(100)).length(), "at most 40 characters");
        assertEquals("", FilmFormat.fileSafe("???"), "nothing left is nothing");
        assertEquals("1080p", FilmFormat.Resolution.FULL_HD.label());
        assertEquals("4K", FilmFormat.Resolution.UHD.label());
    }

    @Test
    void theBitRateFollowsThePixelsAndIsBounded() {
        assertEquals((int) (1920 * 804 * 30 * 0.15), FilmFormat.bitrate(1920, 804, 30, FilmFormat.Quality.HIGH));
        assertEquals(FilmFormat.MAX_BITRATE, FilmFormat.bitrate(3840, 2160, 60, FilmFormat.Quality.BEST));
        assertEquals(FilmFormat.MIN_BITRATE, FilmFormat.bitrate(1280, 536, 24, FilmFormat.Quality.STANDARD));
        assertEquals(49.7, FilmFormat.megabytesPerMinute(FilmFormat.bitrate(1920, 804, 30, FilmFormat.Quality.HIGH)), 0.1);
    }
}
