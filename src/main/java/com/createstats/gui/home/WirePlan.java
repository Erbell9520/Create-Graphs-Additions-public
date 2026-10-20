package com.createstats.gui.home;

import java.util.List;
import java.util.function.IntUnaryOperator;

// out of the screen so it can be tested
public final class WirePlan {
    private WirePlan() {
    }

    public static final int CHEVRON_EVERY = 3;

    public static void draw(WireStyle.Ink ink, WireGraph graph, int yLow, int yHigh, int scale, IntUnaryOperator cx,
                            IntUnaryOperator cz, List<int[]> focus) {
        if (graph.isEmpty()) {
            return;
        }
        if (focus != null && !focus.isEmpty()) {
            final List<long[]> path = graph.pathToEntry(focus);
            for (int i = 0; i + 1 < path.size(); i++) {
                final long[] a = path.get(i);
                final long[] b = path.get(i + 1);

                if (a[0] != b[0] || a[2] != b[2]) {
                    WireStyle.segment(ink, cx.applyAsInt((int) a[0]), cz.applyAsInt((int) a[2]),
                            cx.applyAsInt((int) b[0]), cz.applyAsInt((int) b[2]), WireStyle.casing(scale),
                            WireStyle.CASING);
                }
            }
        }
        final List<WireGraph.Segment> segments = graph.segments(yLow, yHigh);
        for (WireGraph.Segment sg : segments) {
            final int ax = cx.applyAsInt(sg.ax());
            final int az = cz.applyAsInt(sg.az());
            final int bx = cx.applyAsInt(sg.bx());
            final int bz = cz.applyAsInt(sg.bz());
            if (sg.flow() == 0) {
                WireStyle.dotted(ink, ax, az, bx, bz, WireStyle.COPPER);
            } else {
                WireStyle.segment(ink, ax, az, bx, bz, WireStyle.width(sg.flow(), scale), WireStyle.colour(sg.flow()));
            }
        }
        for (WireGraph.Segment sg : segments) {
            if (sg.flow() <= 0 || sg.towards() == 0) {
                continue;
            }
            final int up = sg.towards() > 0 ? graph.hops(sg.ax(), sg.y(), sg.az()) : graph.hops(sg.bx(), sg.y(), sg.bz());
            if (up % CHEVRON_EVERY != 1) {
                continue;
            }
            final int dx = (sg.bx() - sg.ax()) * sg.towards();
            final int dz = (sg.bz() - sg.az()) * sg.towards();
            final int x = (cx.applyAsInt(sg.ax()) + cx.applyAsInt(sg.bx())) / 2 + dx;
            final int z = (cz.applyAsInt(sg.az()) + cz.applyAsInt(sg.bz())) / 2 + dz;
            WireStyle.chevron(ink, x, z, dx, dz, WireStyle.width(sg.flow(), scale), WireStyle.COPPER_DARK);
        }
        for (WireGraph.Wire w : graph.band(yLow, yHigh)) {
            final int x = cx.applyAsInt(w.x());
            final int z = cz.applyAsInt(w.z());
            final int lw = WireStyle.width(Math.max(0, w.flow()), scale);
            if (graph.hops(w.x(), w.y(), w.z()) == 0) {
                WireStyle.entry(ink, x, z, lw);
            } else if (graph.degree(w.x(), w.y(), w.z()) >= 3) {
                WireStyle.junction(ink, x, z, lw);
            } else if (graph.climbs(w.x(), w.y(), w.z())) {
                WireStyle.riser(ink, x, z, lw);
            } else if (graph.end(w.x(), w.y(), w.z())) {
                WireStyle.cap(ink, x, z, lw);
            }
        }
    }
}
