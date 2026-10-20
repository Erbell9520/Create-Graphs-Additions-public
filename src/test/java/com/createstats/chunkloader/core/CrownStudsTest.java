package com.createstats.chunkloader.core;

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

class CrownStudsTest {
    private static final Path MODEL = Path.of("chunk_loader_preview/B_impeccable/crown_model.py");

    private static final double EPS = 1e-9;

    private static String model() throws IOException {
        assertTrue(Files.exists(MODEL), "concept B's model is the stud geometry's source and must be in the tree: "
                + MODEL);

        return Files.readString(MODEL, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static double number(String text, String regex) {
        final Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        assertTrue(m.find(), "crown_model.py no longer has " + regex + ": the studs moved, so the wire points must too");
        return Double.parseDouble(m.group(1));
    }

    @Test
    @DisplayName("facing south the points are the model's; every facing puts the caps flush on the back face")
    void theCapsSitOnTheBackFace() {
        assertArrayEquals(new double[] {5.6 / 16, 6.3 / 16, 0.05 / 16}, CrownStuds.attachPoint(0, 0), EPS);
        assertArrayEquals(new double[] {10.4 / 16, 6.3 / 16, 0.05 / 16}, CrownStuds.attachPoint(0, 1), EPS);

        assertArrayEquals(new double[] {1 - 5.6 / 16, 6.3 / 16, 1 - 0.05 / 16}, CrownStuds.attachPoint(2, 0), EPS);

        assertEquals(1 - 0.05 / 16, CrownStuds.attachPoint(CrownStuds.turnsFor("west"), 0)[0], EPS);

        assertEquals(0.05 / 16, CrownStuds.attachPoint(CrownStuds.turnsFor("east"), 1)[0], EPS);
        for (int t = 0; t < 4; t++) {
            final double[] a = CrownStuds.attachPoint(t, 0);
            final double[] b = CrownStuds.attachPoint(t, 1);
            assertEquals(4.8 / 16, Math.hypot(a[0] - b[0], a[2] - b[2]), EPS, "the studs stay 4.8 px apart");
            final double[] mid = CrownStuds.bayCentre(t);
            assertEquals((a[0] + b[0]) / 2, mid[0], EPS);
            assertEquals((a[2] + b[2]) / 2, mid[2], EPS);
        }
        assertEquals(0, CrownStuds.turnsFor("south"));
        assertEquals(1, CrownStuds.turnsFor("west"));
        assertEquals(2, CrownStuds.turnsFor("north"));
        assertEquals(3, CrownStuds.turnsFor("east"));
    }

    @Test
    @DisplayName("a new wire takes the free stud nearer its far end, and the last free stud when one is taken")
    void aWireTakesTheNearerStud() {
        assertEquals(0, CrownStuds.nearerStud(0, -6, -10, true, true), "a wire from the north-west takes A");
        assertEquals(1, CrownStuds.nearerStud(0, 6, -10, true, true), "a wire from the north-east takes B");
        assertEquals(1, CrownStuds.nearerStud(0, -6, -10, false, true), "only B free");
        assertEquals(0, CrownStuds.nearerStud(0, 6, -10, true, false), "only A free");
        assertEquals(-1, CrownStuds.nearerStud(0, 0, -10, false, false), "both taken");
    }

    @Test
    @DisplayName("the line split fills the first wired stud to its rating, then the second; a convention")
    void theLineSplit() {
        assertArrayEquals(new int[] {58_650, 0}, CrownStuds.split(58_650, 65_536, 65_536, true, true));
        assertArrayEquals(new int[] {65_536, 35_171}, CrownStuds.split(100_707, 65_536, 65_536, true, true));
        assertArrayEquals(new int[] {0, 40_000}, CrownStuds.split(40_000, 0, 65_536, false, true), "B alone");
        assertArrayEquals(new int[] {0, 0}, CrownStuds.split(9_000, 0, 0, false, false), "fed by cable: no bars");
        assertArrayEquals(new int[] {16_384, 0}, CrownStuds.split(20_000, 16_384, 0, true, false),
                "never past a stud's own rating");
    }
}
