package com.createstats.chunkloader.look;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownGeometryTest {
    private static final Path MODEL = Path.of("chunk_loader_preview/B_impeccable/crown_model.py");
    private static final Path ANIM = Path.of("chunk_loader_preview/B_impeccable/crown_anim.py");
    private static final double EPS = 1e-9;

    private static String read(Path p) throws IOException {
        assertTrue(Files.exists(p), "concept B's source must be in the tree: " + p);

        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static double[] list(String py, String name) {
        final Matcher m = Pattern.compile("^" + name + "\\s*=\\s*[\\[(]([^\\])]*)[\\])]", Pattern.MULTILINE)
                .matcher(py);
        assertTrue(m.find(), "crown_model.py no longer has " + name);
        final String[] parts = m.group(1).split(",");
        final double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }

    private static double scalar(String py, String name) {
        final Matcher m = Pattern.compile("^" + name + "\\s*=\\s*([0-9.]+)", Pattern.MULTILINE).matcher(py);
        assertTrue(m.find(), "no " + name);
        return Double.parseDouble(m.group(1));
    }

    @Test
    @DisplayName("48 px across the horns at 25: the top knobs' outer faces stand on the 3 x 3's edges")
    void fortyEightAcrossTheHorns() {
        final double outer = CrownGeometry.knobCentre(CrownGeometry.TIERS - 1)
                + CrownGeometry.KNOB[CrownGeometry.TIERS - 1] / 2;
        assertEquals(24.0, outer, 1e-9, "agreed 3.4: at most three wide; the concept spans exactly 48 px");
    }

    @Test
    @DisplayName("the locked yoke ends either side of the heart, where the concept puts them")
    void theYokeLocksOverTheHeart() {
        final double[] arm = new double[6];
        CrownGeometry.yokeArm(1, 1, 1, arm);

        assertEquals(CrownGeometry.YOKE_END_R, arm[4], 0.01);
        assertEquals(CrownGeometry.YOKE_END_Y, arm[5], 0.01);
        CrownGeometry.yokeArm(1, 0, 0, arm);
        assertEquals(arm[0], arm[2], 1e-9, "folded, the arm is a stub standing straight up from its knob");
        assertEquals(arm[1] + CrownGeometry.YOKE_STUB, arm[3], 1e-9);
    }

    @Test
    @DisplayName("the draw's perceptual level is 0 at one chunk and 1 at a full station, on a log scale")
    void theDrawLevel() {
        assertEquals(0.0, CrownGeometry.drawLevel(1024), 1e-12);
        assertEquals(1.0, CrownGeometry.drawLevel(100_707), 1e-6);
        assertEquals(0.0, CrownGeometry.drawLevel(0), 1e-12);
        assertTrue(CrownGeometry.drawLevel(13_905) > 0.3 && CrownGeometry.drawLevel(13_905) < 0.7,
                "nine chunks reads about half: a log, like loudness");
    }
}
