package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundFixtures;
import com.createstats.ground.GroundFrame;
import com.createstats.ground.GroundSnapshot;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// faces have no scissor, a label a pixel past the bezel floats in the air
class DriveArtTest {
    static List<GroundFrame> frames(List<String> names) {
        final List<GroundFrame> out = new ArrayList<>();
        for (final GroundFixtures.Named n : GroundFixtures.all()) {
            out.add(GroundFrame.of(n.snapshot()));
            names.add(n.name());
        }
        final GroundSnapshot s = GroundFixtures.roadTrain();
        final DriveReadout r = DriveMath.read(s);
        out.add(new GroundFrame(r, GroundAlerts.evaluate(r, List.of(), 6.0), 6.0, s.gameTime(), 0.0f));
        names.add("stale road train");
        out.add(null);
        names.add("no feed");
        return out;
    }

    static PanelModel model(Mk3Settings.FaceMode mode, GroundFrame f, int look, boolean linked) {
        final PanelModel m = new PanelModel();
        m.faceMode = mode;
        m.drive = f;
        m.driveLinked = linked;
        m.driveLook = look;
        return m;
    }

    static Rect wall(int w, int h, float scale) {
        final float ppb = 64.0f / scale;
        final float texel = ppb / 16.0f;
        return new Rect(2.0f * texel, 2.0f * texel, w * ppb - 4.0f * texel, h * ppb - 5.0f * texel);
    }

    static Rect console(float scale) {
        final float ppb = 64.0f / scale;
        return new Rect(2.0f / 16.0f * 14.0f / 16.0f * ppb, 1.0f / 16.0f * 11.0f / 16.0f * ppb,
                12.0f / 16.0f * 14.0f / 16.0f * ppb, 11.0f / 16.0f * 11.0f / 16.0f * ppb);
    }

    private static List<Rect> faces(List<String> names) {
        final List<Rect> out = new ArrayList<>();
        for (final float scale : Mk3Settings.FACE_SCALES) {
            for (final int[] wh : new int[][]{{1, 1}, {2, 1}, {1, 2}, {2, 2}, {3, 2}, {4, 3}, {5, 3}}) {
                out.add(wall(wh[0], wh[1], scale));
                names.add(String.format(Locale.ROOT, "%dx%d at %d%%", wh[0], wh[1], Math.round(scale * 100.0f)));
            }
            out.add(console(scale));
            names.add(String.format(Locale.ROOT, "console at %d%%", Math.round(scale * 100.0f)));
        }
        return out;
    }

    @Test
    void nothingOnADriveFaceIsDrawnOutsideIt() {
        final List<String> faceNames = new ArrayList<>();
        final List<Rect> faces = faces(faceNames);
        final List<String> frameNames = new ArrayList<>();
        final List<GroundFrame> frames = frames(frameNames);
        final Mk3Settings.FaceMode[] modes = {Mk3Settings.FaceMode.DRIVE_DASH, Mk3Settings.FaceMode.DRIVE_GARAGE,
                Mk3Settings.FaceMode.DRIVE_PIT, Mk3Settings.FaceMode.DRIVE_TRAIN};
        final List<String> failures = new ArrayList<>();
        for (int i = 0; i < faces.size(); i++) {
            final Rect rect = faces.get(i);
            for (final Lod lod : Lod.values()) {
                for (final Mk3Settings.FaceMode mode : modes) {
                    for (int look = 0; look < DriveKit.Look.values().length; look++) {
                        for (int fi = 0; fi < frames.size(); fi++) {
                            final AeroExtentSurface s = new AeroExtentSurface(rect);
                            PanelPainter.paint(s, rect, model(mode, frames.get(fi), look, true), lod);
                            final float shapes = s.shapes.over(rect);
                            final float text = s.text.over(rect);
                            if (shapes > 1.0f || text > 0.0f) {
                                failures.add(String.format(Locale.ROOT, "  %s / %s / %s / %s / %s: shapes %.1f, text %.1f%s",
                                        faceNames.get(i), lod, mode, DriveKit.Look.values()[look], frameNames.get(fi),
                                        shapes, text, text > 0.0f ? " (\"" + s.escapedText + "\")" : ""));
                            }
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside a Drive face:\n"
                + String.join("\n", failures.subList(0, Math.min(60, failures.size()))));
    }

    @Test
    void anUnlinkedFaceSaysSoInsideItsBezel() {
        final List<String> names = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final List<Rect> faces = faces(names);
        for (int i = 0; i < faces.size(); i++) {
            final Rect rect = faces.get(i);
            final WordSurface s = new WordSurface(rect);
            PanelPainter.paint(s, rect, model(Mk3Settings.FaceMode.DRIVE_DASH, null, 0, false), Lod.FULL);
            if (s.text.over(rect) > 0.0f || s.words.isEmpty()) {
                failures.add("  " + names.get(i) + ": " + s.words);
            }
        }
        assertTrue(failures.isEmpty(), "an unlinked face must say so, inside itself:\n" + String.join("\n", failures));
    }

    @Test
    void nothingOnTheDrivePageIsDrawnOutsideIt() {
        final List<String> frameNames = new ArrayList<>();
        final List<GroundFrame> frames = frames(frameNames);

        final int[][] sizes = {{497, 247}, {585, 349}, {683, 401}, {1024, 555}, {1040, 680}, {900, 560}, {700, 480},
                {560, 400}};
        final List<String> failures = new ArrayList<>();
        for (final int[] wh : sizes) {
            final Rect area = new Rect(0.0f, 0.0f, wh[0], wh[1]);
            final Rect content = new Rect(area.x, area.y + DriveArt.HEADER_H + DriveArt.TABS_H + 10.0f, area.w,
                    area.h - DriveArt.HEADER_H - DriveArt.TABS_H - 10.0f);
            for (int tab = 0; tab < DriveArt.TABS.length; tab++) {
                for (int look = 0; look < DriveKit.Look.values().length; look++) {
                    for (int fi = 0; fi < frames.size(); fi++) {
                        final GroundFrame f = frames.get(fi);
                        final AeroExtentSurface s = new AeroExtentSurface(area);
                        if (f == null) {
                            DriveArt.empty(s, content, true);
                        } else {
                            DriveArt.header(s, area.top(DriveArt.HEADER_H), f, 0.0f);
                            DriveArt.paint(s, content, f, tab, DriveKit.Look.values()[look]);
                        }
                        final float shapes = s.shapes.over(area);
                        final float text = s.text.over(area);
                        if (shapes > 1.0f || text > 0.0f) {
                            failures.add(String.format(Locale.ROOT, "  %dx%d / %s / %s / %s: shapes %.1f, text %.1f%s",
                                    wh[0], wh[1], DriveArt.TABS[tab], DriveKit.Look.values()[look], frameNames.get(fi),
                                    shapes, text, text > 0.0f ? " (\"" + s.escapedText + "\")" : ""));
                        }
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " drawn outside the Drive page:\n"
                + String.join("\n", failures.subList(0, Math.min(60, failures.size()))));
    }

    @Test
    void noDriveArtEverPrintsANumberItDoesNotHave() {
        final List<String> frameNames = new ArrayList<>();
        final List<GroundFrame> frames = frames(frameNames);
        final List<String> failures = new ArrayList<>();
        final Rect area = new Rect(0.0f, 0.0f, 900.0f, 560.0f);
        for (int fi = 0; fi < frames.size(); fi++) {
            final GroundFrame f = frames.get(fi);
            if (f == null) {
                continue;
            }
            final WordSurface s = new WordSurface(area);
            DriveArt.header(s, area.top(DriveArt.HEADER_H), f, 0.0f);
            for (int tab = 0; tab < DriveArt.TABS.length; tab++) {
                DriveArt.paint(s, area, f, tab, DriveKit.Look.CLUSTER);
            }
            for (final Mk3Settings.FaceMode mode : new Mk3Settings.FaceMode[]{Mk3Settings.FaceMode.DRIVE_DASH,
                    Mk3Settings.FaceMode.DRIVE_GARAGE, Mk3Settings.FaceMode.DRIVE_PIT, Mk3Settings.FaceMode.DRIVE_TRAIN}) {
                for (int look = 0; look < 2; look++) {
                    PanelPainter.paint(s, wall(2, 2, 0.5f), model(mode, f, look, true), Lod.FULL);
                    PanelPainter.paint(s, console(0.5f), model(mode, f, look, true), Lod.FULL);
                }
            }
            for (final String w : s.words) {
                if (w.contains("NaN") || w.contains("Infinity") || w.contains("null")) {
                    failures.add("  " + frameNames.get(fi) + ": \"" + w + "\"");
                }
            }
        }
        assertTrue(failures.isEmpty(), "an unknown printed as a number:\n" + String.join("\n", failures));
    }

    static final class WordSurface implements PanelSurface {
        final AeroExtentSurface extent;
        final AeroExtentSurface.Extent text;
        final List<String> words = new ArrayList<>();

        WordSurface(Rect bound) {
            this.extent = new AeroExtentSurface(bound);
            this.text = this.extent.text;
        }

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            this.extent.tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            if (s != null && !s.isEmpty() && (color >>> 24) != 0) {
                this.words.add(s);
            }
            this.extent.text(s, x, y, color, scale);
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
            return this.extent.textWidth(s, scale);
        }

        @Override
        public void clip(Rect r) {
            this.extent.clip(r);
        }

        @Override
        public void unclip() {
            this.extent.unclip();
        }

        @Override
        public boolean culled(Rect r) {
            return this.extent.culled(r);
        }
    }
}
