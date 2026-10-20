package com.createstats.train.map;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RailPerformanceArtTest {
    private static final File OUT = new File("fixture_preview");

    @Test
    void drawsEveryGradeWithoutFalling() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final String[] captions = {
                "Even. Four an hour, evenly spaced",
                "Convoy. Four an hour, in two pairs",
                "Uneven, but not yet a convoy",
                "Recorded, but nothing has come round twice"};
        final float[][] cases = {
                {450.0f, 450.0f, 450.0f, 450.0f, 450.0f, 450.0f},
                {60.0f, 840.0f, 60.0f, 840.0f, 55.0f, 900.0f},
                {200.0f, 700.0f, 300.0f, 650.0f, 250.0f, 600.0f},
                {}};

        final int w = 460;
        final int h = 150;
        final BufferedImage sheet = new BufferedImage(40 + 2 * (w + 20), 74 + 2 * (h + 32),
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0A0B0E));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xB8C0CC));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString("Service regularity. Gaps between trains, and where the time went.", 22, 28);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(0x7A828E));
        g.drawString("The first two run identical numbers of trains an hour.", 22, 46);

        for (int i = 0; i < cases.length; i++) {
            final RailPerformance.Report report = report(cases[i]);
            final BufferedImage panel = panel(w, h, report);
            final int x = 20 + (i % 2) * (w + 20);
            final int y = 60 + (i / 2) * (h + 32);
            g.drawImage(panel, x, y, null);
            g.setColor(new Color(0x8A93A0));
            g.drawString(captions[i], x + 2, y + h + 16);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "service_regularity.png"));

        assertTrue(report(cases[1]).excessWait() > report(cases[0]).excessWait() + 100.0f,
                "the convoy should cost minutes the even service does not");
        assertEquals(RailPerformance.Grade.REGULAR, report(cases[0]).grade());
        assertEquals(RailPerformance.Grade.BUNCHED, report(cases[1]).grade());
        assertEquals(RailPerformance.Grade.UNEVEN, report(cases[2]).grade());
        assertEquals(RailPerformance.Grade.UNKNOWN, report(cases[3]).grade());
    }

    private static void assertEquals(RailPerformance.Grade want, RailPerformance.Grade got) {
        org.junit.jupiter.api.Assertions.assertEquals(want, got);
    }

    private static BufferedImage panel(int w, int h, RailPerformance.Report report) {
        final RasterSurface s = new RasterSurface(w, h, 0x14161B);
        final Rect area = new Rect(10, 8, w - 20, h - 16);
        float y = area.y;

        s.text(report.label(), area.x, y, 0xFFE8ECF2, Text.META);
        RailPerformanceArt.gradeChip(s, area.right() - 56.0f, y + 6.0f, report, Text.MICRO);
        y += 18.0f;
        s.text(RailPerformanceArt.excessLine(report), area.x, y,
                report.excessWait() >= 30.0f ? 0xFFF0417A : 0xFF8A93A0, Text.MICRO);
        y += 16.0f;

        RailPerformanceArt.headways(s, new Rect(area.x, y, area.w, 46.0f), report, 0xFF3B82F6,
                Text.MICRO);
        y += 54.0f;
        RailPerformanceArt.timeSplit(s, new Rect(area.x, y, area.w, 30.0f), report, Text.MICRO);
        return s.finish();
    }

    private static RailPerformance.Report report(float[] gaps) {
        final RailPerformance.Waits w = RailPerformance.waits(gaps);
        float worst = 0.0f;
        float best = gaps.length == 0 ? 0.0f : Float.MAX_VALUE;
        for (final float gap : gaps) {
            worst = Math.max(worst, gap);
            best = Math.min(best, gap);
        }
        final RailPerformance.Grade grade = gaps.length < RailPerformance.MIN_CROSSINGS - 1
                ? RailPerformance.Grade.UNKNOWN : RailPerformance.grade(w.cv());
        return new RailPerformance.Report(0, "Basmane Gleiss circular", grade, 4, gaps.length + 1,
                gaps, w.mean(), worst, best, w.cv(), w.scheduled(), w.actual(), w.excess(),
                2400.0f, 0.62f, 0.24f, 0.11f, 0.03f, "Erebor Gleiss");
    }
}
