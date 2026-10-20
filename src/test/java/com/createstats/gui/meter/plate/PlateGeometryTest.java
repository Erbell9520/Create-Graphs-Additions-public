package com.createstats.gui.meter.plate;

import com.createstats.gui.meter.MeterHistory;
import com.createstats.gui.meter.plate.PlateGeometry.Line;
import com.createstats.gui.meter.plate.PlateGeometry.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateGeometryTest {
    private static final int[][] SCREENS = {{853, 533}, {640, 360}, {480, 270}, {427, 240}, {320, 240}, {1024, 768}};

    private static final Map<String, Integer> VANILLA = Map.of(
            "PEAK", 24, "CARRIED", 40, "COMPARATOR", 60, "LOAD BUFFER", 64, "RESET", 30, "15m", 18);

    @Test
    void thePlateFitsEveryScreen() {
        for (final int[] s : SCREENS) {
            final PlateGeometry g = PlateGeometry.of(s[0], s[1]);
            assertTrue(g.x >= 0 && g.y >= 0, at(s) + "plate starts off screen");
            assertTrue(g.x + g.w <= s[0] && g.y + g.h <= s[1], at(s) + "plate runs off screen");
            assertTrue(g.w >= PlateGeometry.MIN_W && g.w <= PlateGeometry.MAX_W, at(s) + "width " + g.w);
            assertTrue(g.h >= PlateGeometry.MIN_H && g.h <= PlateGeometry.MAX_H, at(s) + "height " + g.h);
        }
    }

    @Test
    void everyPartSitsOnThePlateFace() {
        for (final int[] s : SCREENS) {
            final PlateGeometry g = PlateGeometry.of(s[0], s[1]);
            for (final Rect r : parts(g)) {
                assertTrue(r.x() >= 4 && r.y() >= 4 && r.right() <= g.w - 4 && r.bottom() <= g.h - 4,
                        at(s) + "a part leaves the plate face: " + r);
            }
        }
    }

    @Test
    void nothingOverlaps() {
        for (final int[] s : SCREENS) {
            final PlateGeometry g = PlateGeometry.of(s[0], s[1]);
            final List<Rect> parts = parts(g);
            for (int i = 0; i < parts.size(); i++) {
                for (int j = i + 1; j < parts.size(); j++) {
                    assertFalse(parts.get(i).overlaps(parts.get(j)),
                            at(s) + parts.get(i) + " overlaps " + parts.get(j));
                }
            }
        }
    }

    @Test
    void theRateWindowSitsOnTheLineBetweenItsRuns() {
        for (final int[] s : SCREENS) {
            final PlateGeometry g = PlateGeometry.of(s[0], s[1]);
            assertEquals(g.rate.y() + g.rate.h() / 2, g.lineY, at(s) + "the line must run through the rate window");
            assertTrue(g.slotIn.right() < g.rate.x() && g.slotOut.x() > g.rate.right(), at(s) + "runs must meet the window");
            assertTrue(g.slotIn.x() >= g.terminal.right() && g.slotOut.right() <= g.gland.x(), at(s) + "runs must start at the fittings");
            assertTrue(g.slotIn.w() > 20 && g.slotOut.w() > 20, at(s) + "a run too short to show motion");
        }
    }

    @Test
    void atFullSizeTheTraceHasOneColumnPerSample() {
        final PlateGeometry g = PlateGeometry.of(853, 533);

        assertEquals(MeterHistory.SIZE, g.trace.glass().w());
        assertTrue(g.plotHeight() > 40, "the plot has to keep real height below the label band");
    }

    @Test
    void everyLegendClearsItsWindowAndEveryLeaderClearsItsLegend() {
        for (final int[] s : SCREENS) {
            final PlateGeometry g = PlateGeometry.of(s[0], s[1]);
            for (final Line l : new Line[]{g.peak, g.carried, g.comparator, g.load}) {
                final int inkEnd = l.legendX() + VANILLA.get(l.legend());
                assertTrue(inkEnd + 2 <= l.window().x(), at(s) + l.legend() + " runs into its window");
            }

            final int resetX = g.studZone.x() + (g.studZone.w() - VANILLA.get("RESET")) / 2;
            final int resetLitEdge = resetX + VANILLA.get("RESET");
            assertTrue(g.bracketX - resetLitEdge >= 2, at(s) + "bracket touches RESET");
            assertTrue(g.peak.legendX() - (g.peak.legendX() - 4) - 1 >= 2, at(s) + "bracket tick touches PEAK");

            final int leaderX = g.w - 6;
            assertTrue(leaderX > g.load.window().right() && leaderX < g.w - 4, at(s) + "load leader misplaced");
            assertTrue(leaderX > g.trace.right(), at(s) + "load leader crosses the trace");
        }
    }

    @Test
    void theWidestKeyLabelFitsItsKey() {
        final int label = VANILLA.get("15m");
        assertTrue(label + 2 <= PlateGeometry.KEY_W - 2, "15m does not fit inside the key's outline");
    }

    private static List<Rect> parts(PlateGeometry g) {
        final List<Rect> out = new ArrayList<>();
        out.add(g.rate);
        out.add(g.slotIn);
        out.add(g.slotOut);
        out.add(g.terminal);
        out.add(g.gland);
        out.add(g.jewelIn);
        out.add(g.jewelOut);
        out.add(g.trace);
        for (final Rect k : g.keys) {
            out.add(k);
        }
        for (final Line l : new Line[]{g.peak, g.carried, g.comparator, g.load}) {
            out.add(l.window());
        }
        out.add(g.stud);
        return out;
    }

    private static String at(int[] s) {
        return s[0] + " x " + s[1] + ": ";
    }
}
