package com.createstats.statsmk3.page;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import com.createstats.efficiency.EfficiencyGrader;
import com.createstats.efficiency.EfficiencyInputs;
import com.createstats.efficiency.EfficiencyScore;
import com.createstats.efficiency.EfficiencyTrace;
import com.createstats.efficiency.ODEState;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.paint.EfficiencyFacePainter;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EfficiencyArtTest {
    private static final File OUT = new File("fixture_preview");

    private static EfficiencyGrader.Evaluation healthy() {
        final EfficiencyInputs in = new EfficiencyInputs(4096.0f, 2534.0f, 3010.0f, 0.081f, true,
                0.014f, true, false, 0.0f, true, 0.02f, true, 2400L);
        final ODEState state = new ODEState();
        EfficiencyGrader.Evaluation ev = null;
        for (int i = 0; i < 60; i++) {
            ev = EfficiencyGrader.evaluate(in, state, 40);
        }
        return ev;
    }

    private static EfficiencyGrader.Evaluation struggling() {
        final EfficiencyInputs in = new EfficiencyInputs(2048.0f, 2150.0f, 2300.0f, 0.0f, false,
                0.0f, false, true, 42.0f, false, 0.0f, false, 2400L);
        final ODEState state = new ODEState();
        state.previousU = 0.72f;
        EfficiencyGrader.Evaluation ev = null;
        for (int i = 0; i < 30; i++) {
            ev = EfficiencyGrader.evaluate(in, state, 40);
        }
        return ev;
    }

    @Test
    void rendersTheWorksheet() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }

        final int[][] sizes = {{1040, 880}, {640, 620}};
        final EfficiencyGrader.Evaluation[] cases = {healthy(), struggling()};

        final BufferedImage sheet = new BufferedImage(
                48 + sizes[0][0] + sizes[1][0], 64 + Math.max(sizes[0][1], sizes[1][1]),
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        int x = 16;
        for (int i = 0; i < sizes.length; i++) {
            final RasterSurface s = new RasterSurface(sizes[i][0], sizes[i][1], 0x0A0B0E);
            EfficiencyMathArt.paint(s, new Rect(8.0f, 8.0f, sizes[i][0] - 16.0f, sizes[i][1] - 16.0f),
                    cases[i].trace(), cases[i].score(), i * 1.4f,
                    trendSeries(cases[i].score().compositeScore()));
            g.drawImage(s.finish(), x, 32, null);
            x += sizes[i][0] + 16;
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "efficiency_math.png"));
    }

    @Test
    void rendersTheFaceAtEverySize() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final int[][] sizes = {{1, 1}, {2, 2}, {5, 3}};
        final Lod[] levels = {Lod.FULL, Lod.REDUCED, Lod.GLYPH};
        final int cell = 340;

        final BufferedImage sheet = new BufferedImage(48 + levels.length * (cell + 40),
                80 + sizes.length * (cell + 40), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        for (int row = 0; row < sizes.length; row++) {
            final int y = 48 + row * (cell + 40);
            for (int i = 0; i < levels.length; i++) {
                final int fw = 64 * sizes[row][0] - 16;
                final int fh = 64 * sizes[row][1] - 20;
                final RasterSurface s = new RasterSurface(fw, fh, 0x05070A);
                EfficiencyFacePainter.paint(s, new Rect(0.0f, 0.0f, fw, fh),
                        faceModel(row == 0 ? struggling() : healthy()), levels[i]);
                g.drawImage(s.finish(), 24 + i * (cell + 40), y, null);
            }
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "efficiency_face.png"));
    }

    @Test
    void everyStateThatIsNotAReadingDrawsWithoutThrowing() {
        final EfficiencyGrader.Evaluation ev = healthy();
        for (final Lod lod : Lod.values()) {
            assertDoesNotThrow(() -> EfficiencyFacePainter.paint(new RasterSurface(96, 76, 0),
                    new Rect(0.0f, 0.0f, 96.0f, 76.0f), new PanelModel(), lod));
            final PanelModel noTrace = faceModel(ev);
            noTrace.efficiencyTrace = null;
            assertDoesNotThrow(() -> EfficiencyFacePainter.paint(new RasterSurface(96, 76, 0),
                    new Rect(0.0f, 0.0f, 96.0f, 76.0f), noTrace, lod));
            assertDoesNotThrow(() -> EfficiencyFacePainter.paint(new RasterSurface(304, 172, 0),
                    new Rect(0.0f, 0.0f, 304.0f, 172.0f), faceModel(ev), lod));
        }
        assertDoesNotThrow(() -> EfficiencyMathArt.paint(new RasterSurface(400, 300, 0),
                new Rect(0.0f, 0.0f, 400.0f, 300.0f), null, null, -1.0f, null));
        assertDoesNotThrow(() -> EfficiencyMathArt.paint(new RasterSurface(400, 300, 0),
                new Rect(0.0f, 0.0f, 400.0f, 300.0f), ev.trace(), ev.score(), 1.0f, null));
    }

    @Test
    void theFixturesAreRealGraderOutput() {
        final EfficiencyGrader.Evaluation ev = healthy();
        assertNotNull(ev.trace(), "the grader returned no trace for a live network");
        final EfficiencyScore score = ev.score();
        assertNotNull(score.grade());
    }

    private static PanelModel faceModel(EfficiencyGrader.Evaluation ev) {
        final PanelModel m = new PanelModel();
        m.faceMode = Mk3Settings.FaceMode.EFFICIENCY;
        m.efficiency = ev.score();
        m.efficiencyTrace = ev.trace();
        m.efficiencyTrend = trendSeries(ev.score().compositeScore());
        m.title = "Efficiency";
        m.status = "LIVE";
        return m;
    }

    private static float[] trendSeries(float end) {
        final float[] out = new float[48];
        for (int i = 0; i < out.length; i++) {
            final float t = i / (float) (out.length - 1);
            out[i] = 50.0f + (end - 50.0f) * t + (float) Math.sin(i * 0.7) * 2.5f;
        }
        out[out.length - 1] = end;
        return out;
    }
}
