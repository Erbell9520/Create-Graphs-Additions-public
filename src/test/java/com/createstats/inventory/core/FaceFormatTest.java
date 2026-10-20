package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceFormatTest {
    private static final String HM = "%sH %sM";
    private static final String DAYS = "%s DAYS";

    @Test
    void compactCountsMatchTheDesign() {
        assertEquals("832", FaceFormat.compact(832));
        assertEquals("1642", FaceFormat.compact(1642));
        assertEquals("9999", FaceFormat.compact(9999));
        assertEquals("10.0K", FaceFormat.compact(10_000));
        assertEquals("12.4K", FaceFormat.compact(12_400));
        assertEquals("128K", FaceFormat.compact(128_000));
        assertEquals("1.24M", FaceFormat.compact(1_240_000));
        assertEquals("12.4M", FaceFormat.compact(12_400_000));
        assertEquals("1.24B", FaceFormat.compact(1_240_000_000L));
    }

    @Test
    void compactNeverExceedsFiveCharactersAtAnyMagnitude() {
        final long[] edges = {9_999, 10_000, 99_949, 99_950, 99_999, 100_000, 999_499, 999_500, 999_999, 1_000_000,
                9_994_999, 9_995_000, 99_949_999, 99_950_000, 999_499_999, 999_500_000, Long.MAX_VALUE};
        for (long n : edges) {
            final String s = FaceFormat.compact(n);
            assertTrue(s.length() <= FaceFormat.COMPACT_MAX, n + " printed as " + s);
        }
        for (long p = 1; p > 0 && p < Long.MAX_VALUE / 10; p *= 10) {
            for (long n : new long[]{p - 1, p, p + 1, p * 5, p * 9 + p - 1}) {
                if (n >= 0) {
                    final String s = FaceFormat.compact(n);
                    assertTrue(s.length() <= FaceFormat.COMPACT_MAX, n + " printed as " + s);
                }
            }
        }
    }

    @Test
    void clocksReadAsTheFaceSaysThem() {
        assertEquals("6:10", FaceFormat.clock(370, HM, DAYS));
        assertEquals("0:00", FaceFormat.clock(0, HM, DAYS));
        assertEquals("59:59", FaceFormat.clock(3599, HM, DAYS));
        assertEquals("1H 33M", FaceFormat.clock(5580, HM, DAYS));
        assertEquals("1H 05M", FaceFormat.clock(3900, HM, DAYS));
        assertEquals("3 DAYS", FaceFormat.clock(3 * 86_400 + 500, HM, DAYS));
        assertEquals("1h 33m", FaceFormat.clock(5580, "%sh %sm", "%s days"));
    }

    @Test
    void clocksFitTheVerdictLineAtTwoTimes() {
        for (double s : new double[]{0, 59, 3599, 3600, 86_399, 86_400, 29 * 86_400.0}) {
            final String line = "EMPTY IN " + FaceFormat.clock(s, HM, DAYS);
            assertTrue(FaceFormat.glassWidth(line, 2) <= 224, line);
        }
    }

    @Test
    void ratesKeepOneDecimalOnlyWhenSlow() {
        assertEquals("0.5/MIN", FaceFormat.rate(0.5, "%s/MIN"));
        assertEquals("3/MIN", FaceFormat.rate(3.0, "%s/MIN"));
        assertEquals("48/MIN", FaceFormat.rate(48.2, "%s/MIN"));
        assertEquals("1500/MIN", FaceFormat.rate(1500, "%s/MIN"));
        assertEquals("12.4K/MIN", FaceFormat.rate(12_400, "%s/MIN"));
        assertEquals("-3", FaceFormat.rateFigure(-3.0));
    }

    @Test
    void bucketsAndEnergyAndPercent() {
        assertEquals("9.6", FaceFormat.buckets(9_600));
        assertEquals("0.5", FaceFormat.buckets(500));
        assertEquals("48", FaceFormat.buckets(48_000));
        assertEquals("1240", FaceFormat.buckets(1_240_000));
        assertEquals("1,240 FE", FaceFormat.fe(1240));
        assertEquals("12.4K FE", FaceFormat.fe(12_400));
        assertEquals("1.24M FE", FaceFormat.fe(1_240_000));
        assertEquals("46%", FaceFormat.percent(0.456));
        assertEquals("100%", FaceFormat.percent(1.4));
        assertEquals("8.4K", FaceFormat.stockCount(8_416));
        assertEquals("48K", FaceFormat.stockCount(48_210));
        assertEquals("1.2M", FaceFormat.stockCount(1_240_000));
        assertEquals("940", FaceFormat.stockCount(940));
    }

    @Test
    void noOutputCarriesADashTheGlassCannotDraw() {
        final StringBuilder all = new StringBuilder();
        for (long n = -20; n < 3_000_000_000L; n = n < 100 ? n + 1 : n * 3 + 7) {
            all.append(FaceFormat.compact(n)).append(FaceFormat.grouped(n)).append(FaceFormat.stockCount(Math.abs(n)))
                    .append(FaceFormat.rate(n / 7.0, "%s/MIN")).append(FaceFormat.fe(Math.abs(n)))
                    .append(FaceFormat.buckets(Math.abs(n))).append(FaceFormat.clock(Math.abs(n), HM, DAYS));
        }
        final String s = all.toString();
        assertFalse(s.contains("\u2014"), "an em dash");
        assertFalse(s.contains("\u2013"), "an en dash");
        assertTrue(FaceFormat.faceSafe(s), "every character prints in the glass font");
    }

    @Test
    void trimmedNamesEndInAPeriodAndFit() {
        final String name = "STONE BRICK STAIRS AND SLABS";
        final String t = FaceFormat.trimToGlass(name, 100);
        assertTrue(FaceFormat.glassWidth(t, 1) <= 100, t);
        assertTrue(t.endsWith("."), t);
        assertEquals("OAK CHEST", FaceFormat.trimToGlass("OAK CHEST", 100));
    }
}
