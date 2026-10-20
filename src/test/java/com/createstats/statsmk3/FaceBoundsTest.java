package com.createstats.statsmk3;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.train.smart.ClientRailNetwork;

import org.junit.jupiter.api.Test;

class FaceBoundsTest {
    private static final float BASE_PPB = 64.0f;

    private record Face(String name, Rect rect) {
    }

    private static Face wall(int w, int h, float scale) {
        final float ppb = BASE_PPB / scale;
        final float texel = ppb / 16.0f;
        return new Face(w + "x" + h + " wall at " + Math.round(scale * 100.0f) + "%",
                new Rect(2.0f * texel, 2.0f * texel,
                        w * ppb - 4.0f * texel, h * ppb - 5.0f * texel));
    }

    private static Face console(float scale) {
        final float ppb = BASE_PPB / scale;
        return new Face("console at " + Math.round(scale * 100.0f) + "%", new Rect(
                2.0f / 16.0f * 14.0f / 16.0f * ppb,
                1.0f / 16.0f * 11.0f / 16.0f * ppb,
                12.0f / 16.0f * 14.0f / 16.0f * ppb,
                11.0f / 16.0f * 11.0f / 16.0f * ppb));
    }

    private static final List<Face> FACES = buildFaces();

    private static List<Face> buildFaces() {
        final List<Face> out = new ArrayList<>();
        for (final float scale : com.createstats.statsmk3.Mk3Settings.FACE_SCALES) {
            out.add(wall(1, 1, scale));
            out.add(wall(2, 1, scale));
            out.add(wall(1, 2, scale));
            out.add(wall(3, 2, scale));
            out.add(wall(5, 3, scale));
            out.add(console(scale));
        }
        return out;
    }

    @Test
    void nothingRailwayIsDrawnOutsideTheFace() {
        ClientRailNetwork.accept(RailFixtures.network(), true);
        final List<String> failures = new ArrayList<>();
        final Lod[] levels = Lod.values();
        final Mk3Settings.FaceMode[] modes = {
                Mk3Settings.FaceMode.TRAIN_BOARD, Mk3Settings.FaceMode.TRAIN_MAP,
                Mk3Settings.FaceMode.TRAIN_ROUTE, Mk3Settings.FaceMode.TRAIN_DIAGRAM,
                Mk3Settings.FaceMode.TRAIN_FOCUS, Mk3Settings.FaceMode.TRAIN_STATUS};

        for (final Face face : FACES) {
            for (int tier = 0; tier < levels.length; tier++) {
                for (final Mk3Settings.FaceMode mode : modes) {
                    for (final Mk3Settings.MapFocus focus : Mk3Settings.MapFocus.values()) {
                        check(failures, face, levels[tier], LOD_NAMES[tier],
                                mode + " " + focus, railway(mode, focus));
                        if (mode != Mk3Settings.FaceMode.TRAIN_MAP) {
                            break;
                        }
                    }
                }

                check(failures, face, levels[tier], LOD_NAMES[tier], "unset board",
                        railway(Mk3Settings.FaceMode.TRAIN_BOARD, Mk3Settings.MapFocus.NETWORK,
                                "", "", ""));
            }
        }
        assertTrue(failures.isEmpty(), "drawn outside the railway face:\n"
                + String.join("\n", failures));
    }

    @Test
    void nothingInTheDeadlockOverlayIsDrawnOutsideTheFace() {
        ClientRailNetwork.accept(RailFixtures.deadlock(), true);
        final List<String> failures = new ArrayList<>();
        final Lod[] levels = Lod.values();
        for (final Face face : FACES) {
            for (int tier = 0; tier < levels.length; tier++) {
                for (final Mk3Settings.MapFocus focus : Mk3Settings.MapFocus.values()) {
                    check(failures, face, levels[tier], LOD_NAMES[tier],
                            "deadlock " + focus,
                            railway(Mk3Settings.FaceMode.TRAIN_MAP, focus));
                }
            }
        }
        ClientRailNetwork.accept(RailFixtures.network(), true);
        assertTrue(failures.isEmpty(), "the deadlock overlay drew outside the face:\n"
                + String.join("\n", failures));
    }

    private static PanelModel railway(Mk3Settings.FaceMode mode, Mk3Settings.MapFocus focus) {
        return railway(mode, focus, "Central *", "Stone circuit", "Stone Express");
    }

    private static PanelModel railway(Mk3Settings.FaceMode mode, Mk3Settings.MapFocus focus,
                                      String stations, String line, String train) {
        final PanelModel m = new PanelModel();
        m.faceMode = mode;
        m.mapFocus = focus;
        m.boardStation = stations;
        m.diagramLine = line;
        m.focusTrain = train;
        m.faceX = -40.0;
        m.faceZ = 20.0;
        m.mapRadius = 200;
        m.title = "Railway";
        m.status = "LIVE";
        return m;
    }

    @Test
    void nothingOnTheEfficiencyFaceIsDrawnOutsideIt() {
        final List<String> failures = new ArrayList<>();
        final Lod[] levels = Lod.values();
        for (final Face face : FACES) {
            for (int tier = 0; tier < levels.length; tier++) {
                check(failures, face, levels[tier], LOD_NAMES[tier], "efficiency healthy",
                        efficiency(true, true));
                check(failures, face, levels[tier], LOD_NAMES[tier], "efficiency struggling",
                        efficiency(false, true));

                check(failures, face, levels[tier], LOD_NAMES[tier], "efficiency without working",
                        efficiency(true, false));

                final PanelModel blank = new PanelModel();
                blank.faceMode = Mk3Settings.FaceMode.EFFICIENCY;
                check(failures, face, levels[tier], LOD_NAMES[tier], "efficiency no grade", blank);
            }
        }
        assertTrue(failures.isEmpty(), "drawn outside the efficiency face:\n"
                + String.join("\n", failures));
    }

    private static PanelModel efficiency(boolean healthy, boolean withTrace) {
        final com.createstats.efficiency.EfficiencyInputs in = healthy
                ? new com.createstats.efficiency.EfficiencyInputs(4096.0f, 2534.0f, 3010.0f,
                        0.081f, true, 0.014f, true, false, 0.0f, true, 0.02f, true, 2400L)
                : new com.createstats.efficiency.EfficiencyInputs(2048.0f, 2150.0f, 2300.0f,
                        0.0f, false, 0.0f, false, true, 42.0f, false, 0.0f, false, 2400L);
        final com.createstats.efficiency.ODEState state = new com.createstats.efficiency.ODEState();
        com.createstats.efficiency.EfficiencyGrader.Evaluation ev = null;
        for (int i = 0; i < 40; i++) {
            ev = com.createstats.efficiency.EfficiencyGrader.evaluate(in, state, 40);
        }
        final PanelModel m = new PanelModel();
        m.faceMode = Mk3Settings.FaceMode.EFFICIENCY;
        m.efficiency = ev.score();
        m.efficiencyTrace = withTrace ? ev.trace() : null;
        m.efficiencyTrend = new float[]{50.0f, 61.0f, 58.0f, 72.0f, 80.0f, 77.0f, 91.0f};
        return m;
    }

    @Test
    void nothingIsDrawnOutsideTheFace() {
        final List<String> failures = new ArrayList<>();
        final Lod[] levels = Lod.values();
        for (final Face face : FACES) {
            for (int tier = 0; tier < levels.length; tier++) {
                final String at = LOD_NAMES[tier];
                check(failures, face, levels[tier], at, "empty", PanelFaceRasterTest.empty());
                check(failures, face, levels[tier], at, "single",
                        PanelFaceRasterTest.preset("single", 4, 4, 1));
                check(failures, face, levels[tier], at, "quad",
                        PanelFaceRasterTest.preset("quad", 4, 4, 4));
                check(failures, face, levels[tier], at, "signal wall",
                        PanelFaceRasterTest.preset("signal_wall", 4, 4, 8));
            }
        }
        assertTrue(failures.isEmpty(), "drawn outside the face:\n" + String.join("\n", failures));
    }

    private static final String[] LOD_NAMES = {"full", "reduced", "glyph"};

    private static void check(List<String> failures, Face face, Lod lod, String at, String what,
                              PanelModel model) {
        final ExtentSurface surface = new ExtentSurface();
        PanelPainter.paint(surface, face.rect(), model, lod);

        report(failures, face, at, what, "shapes", surface.shapes, 1.0f);
        report(failures, face, at, what, "text", surface.text, 0.0f);
    }

    private static void report(List<String> failures, Face face, String at, String what,
                               String kind, Extent extent, float slack) {
        if (extent.empty()) {
            return;
        }
        final Rect r = face.rect();
        final float over = Math.max(
                Math.max(r.x - extent.minX, extent.maxX - r.right()),
                Math.max(r.y - extent.minY, extent.maxY - r.bottom()));
        if (over > slack) {
            failures.add(String.format(java.util.Locale.ROOT,
                    "  %s / %s / %s / %s: %.1f units past the edge (face %s, drawn %.1f,%.1f to %.1f,%.1f)",
                    face.name(), at, what, kind, over, r,
                    extent.minX, extent.minY, extent.maxX, extent.maxY));
        }
    }

    private static final class Extent {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        boolean empty() {
            return this.minX > this.maxX;
        }

        void add(float x, float y) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
        }
    }

    private static final class ExtentSurface implements PanelSurface {
        final Extent shapes = new Extent();
        final Extent text = new Extent();

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2,
                        float x3, float y3, int c3) {
            this.shapes.add(x1, y1);
            this.shapes.add(x2, y2);
            this.shapes.add(x3, y3);
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            if (s == null || s.isEmpty() || (color >>> 24) == 0) {
                return;
            }
            this.text.add(x, y);
            this.text.add(x + textWidth(s, scale), y + Text.lineHeight(scale));
        }

        @Override
        public void textCenter(String s, float centerX, float y, int color, float scale) {
            text(s, centerX - textWidth(s, scale) * 0.5f, y, color, scale);
        }

        @Override
        public void textRight(String s, float rightX, float y, int color, float scale) {
            text(s, rightX - textWidth(s, scale), y, color, scale);
        }

        @Override
        public float textWidth(String s, float scale) {
            return CountingSurface.advance(s, scale);
        }

        private final java.util.Deque<Rect> clips = new java.util.ArrayDeque<>();

        @Override
        public void clip(Rect r) {
            final Rect current = this.clips.peek();
            this.clips.push(current == null ? r : current.intersect(r));
        }

        @Override
        public void unclip() {
            if (!this.clips.isEmpty()) {
                this.clips.pop();
            }
        }

        @Override
        public boolean culled(Rect r) {
            final Rect current = this.clips.peek();
            return r.isEmpty() || (current != null && current.intersect(r).isEmpty());
        }
    }
}
