package com.createstats.surveyor.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadPaintTest {
    static final double[][] NODES = {{-2.0, 12.0}, {3.0, 12.0}, {7.0, 11.4}, {10.5, 9.4}, {13.5, 7.4}, {17.0, 6.6}, {21.5, 6.5}};
    static final double STOP_S = 17.8;

    static final double LIMIT_S = 15.0;
    static final double CRUISE = 6.0;

    record Quad(float[] p, int argb) {
        float minY() {
            return Math.min(Math.min(p[1], p[4]), Math.min(p[7], p[10]));
        }

        float maxY() {
            return Math.max(Math.max(p[1], p[4]), Math.max(p[7], p[10]));
        }

        float cx() {
            return (p[0] + p[3] + p[6] + p[9]) / 4f;
        }

        float cz() {
            return (p[2] + p[5] + p[8] + p[11]) / 4f;
        }

        int rgb() {
            return argb & 0xFFFFFF;
        }
    }

    static Pathway plaza() {
        List<double[]> pts = new ArrayList<>();
        for (double[] n : NODES) pts.add(new double[]{n[0], 0.0, n[1]});
        return Pathway.create(new UUID(3, 3), "Plaza", 3, new UUID(1, 1)).appendAll(pts);
    }

    static double speedAt(double s) {
        double d = Math.abs(s - STOP_S);
        return d < 5.0 ? Math.max(1.0, CRUISE * d / 5.0) : CRUISE;
    }

    static RoadPaint.Scene scene(double time, double camX, double camZ, boolean warnings) {
        PathSpline sp = PathSpline.of(plaza());
        int n = sp.sampleCount();
        double[] height = new double[n];
        int[] level = new int[n];
        double[] flow = new double[n];
        double len = sp.length();
        for (int i = 0; i < n; i++) {
            height[i] = RoadPaint.LIFT;
            if (sp.sampleS(i) >= len - 3.6) level[i] = 3;
            if (i > 0) flow[i] = flow[i - 1] + sp.step() / speedAt(sp.sampleS(i));
        }
        return new RoadPaint.Scene(sp, height, level, flow, time, 0x3AB3DA, 0xE0, 1.5, warnings, camX, camZ, 160.0,
                List.of(new RoadPaint.StopMark(STOP_S, 3.0, 2.0)), List.of(new RoadPaint.LimitMark(LIMIT_S, 3.0)), 0.6);
    }

    static List<Quad> paint(RoadPaint.Scene sc) {
        List<Quad> out = new ArrayList<>();
        RoadPaint.paint(sc, (x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, argb) ->
                out.add(new Quad(new float[]{x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3}, argb)));
        return out;
    }

    @Test
    void thePaintLiesOnTheSurveyAndNothingFloats() {
        List<Quad> q = paint(scene(0.0, 10.0, 9.0, true));
        assertTrue(q.size() > 200, "a road's worth of paint: " + q.size());
        for (Quad x : q) {
            assertTrue(x.minY() >= RoadPaint.LIFT - 1e-4 && x.maxY() <= RoadPaint.LIFT + 0.01,
                    "on the ground, lifted at most a hundredth over the lift: " + x.minY() + " to " + x.maxY());
        }
    }

    @Test
    void farAwayOnlyTheEdgesArePainted() {
        List<Quad> q = paint(scene(0.0, 10.0 + 120.0, 9.0, true));
        assertTrue(!q.isEmpty(), "still drawn inside the range");
        for (Quad x : q) {
            assertTrue(x.rgb() == 0x3AB3DA || x.rgb() == RoadPaint.RED || x.rgb() == RoadPaint.GREY,
                    "only edge lines past " + RoadPaint.NEAR + " b: " + Integer.toHexString(x.argb));
        }
        assertTrue(paint(scene(0.0, 10.0 + 400.0, 9.0, true)).isEmpty(), "nothing past the range");
    }

    private static double firstDash(RoadPaint.Scene sc) {
        PathSpline sp = sc.sp();
        for (int i = 0; i < sp.sampleCount() - 1; i++) {
            double phase = (sc.flow()[i] - sc.time()) / sc.period();
            double prev = i == 0 ? 0.9 : (sc.flow()[i - 1] - sc.time()) / sc.period();
            boolean on = phase - Math.floor(phase) < 0.5;
            boolean wasOn = prev - Math.floor(prev) < 0.5;
            if (on && (!wasOn || i == 0) && i > 0) return sp.sampleS(i);
        }
        return Double.NaN;
    }

    @Test
    void theDashesMarchAtThePlannedSpeed() {
        double a = firstDash(scene(10.0, 0, 12, true));
        double b = firstDash(scene(10.1, 0, 12, true));
        double moved = b - a;
        if (moved < -1.0) moved += CRUISE * 0.6;
        assertEquals(0.6, moved, 0.26, "moved " + moved);

        List<Quad> white = new ArrayList<>();
        for (Quad x : paint(scene(10.0, 0, 12, true))) if (x.rgb() == RoadPaint.WHITE && x.cx() < 4.0 && Math.abs(x.cz() - 12.0) < 0.2) white.add(x);
        assertTrue(white.size() >= 5, "dashes on the first straight: " + white.size());
    }

    @Test
    void onlyRedStretchesAreHatchedAndTheStopAndLimitArePainted() {
        RoadPaint.Scene sc = scene(0.0, 10.0, 9.0, true);
        List<Quad> q = paint(sc);
        double len = sc.sp().length();
        double hazardX = sc.sp().x(len - 3.6);
        int stripes = 0;
        int bar = 0;
        int yellow = 0;
        int digits = 0;
        for (Quad x : q) {
            if ((x.argb >>> 24) >= 0xE0 && x.rgb() == RoadPaint.WHITE) {
                if (x.cx() > hazardX - 0.2) {
                    stripes++;
                }
            }
            if (x.rgb() == RoadPaint.YELLOW) yellow++;
            double sx = sc.sp().x(STOP_S);
            double sz = sc.sp().z(STOP_S);
            if (x.rgb() == RoadPaint.WHITE && Math.hypot(x.cx() - sx, x.cz() - sz) < 0.3) bar++;
            double lx = sc.sp().x(LIMIT_S - 2.5);
            double lz = sc.sp().z(LIMIT_S - 2.5);
            if (x.rgb() == RoadPaint.WHITE && Math.hypot(x.cx() - lx, x.cz() - lz) < 0.9) digits++;
        }
        assertTrue(stripes >= 3, "the lake's stretch is hatched: " + stripes);
        assertTrue(yellow >= 10, "the bay is outlined: " + yellow);
        assertTrue(bar >= 1, "the stop bar is painted");
        assertTrue(digits >= 10, "the 3 is painted, as its ten cells: " + digits);
    }

    @Test
    void thePixelDigitsAreThreeByFive() {
        for (char ch : "0123456789".toCharArray()) {
            String[] g = RoadPaint.glyph(ch);
            assertEquals(5, g.length);
            for (String row : g) assertEquals(3, row.length(), "digit " + ch);
        }
    }

    @Test
    void writesThePreviewFrames() throws IOException {
        Path out = Path.of("build/road-paint");
        Files.createDirectories(out);
        for (double t : new double[]{0.0, 0.3}) {
            StringBuilder sb = new StringBuilder("{\"quads\":[");
            List<Quad> q = paint(scene(t, 10.0, 9.0, true));
            for (int i = 0; i < q.size(); i++) {
                Quad x = q.get(i);
                sb.append(i == 0 ? "" : ",\n").append('[');
                for (int k = 0; k < 12; k++) sb.append(String.format(Locale.ROOT, "%.4f,", x.p[k]));
                sb.append(String.format(Locale.ROOT, "\"#%08X\"]", x.argb));
            }
            sb.append("]}");
            Files.writeString(out.resolve(String.format(Locale.ROOT, "plaza_t%.1f.json", t)), sb.toString(), StandardCharsets.UTF_8);
        }
    }
}
