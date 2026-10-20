package com.createstats.chunkloader.map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownSampleShadeTest {
    @Test
    @DisplayName("water: deeper is darker on a checkerboard, against 0.5 and 0.9")
    void water() {
        assertEquals(CrownSampleShade.HIGH, CrownSampleShade.water(0, 0, 0));
        assertEquals(CrownSampleShade.HIGH, CrownSampleShade.water(4, 0, 0), "0.4");
        assertEquals(CrownSampleShade.NORMAL, CrownSampleShade.water(4, 1, 0), "0.4 + 0.2");
        assertEquals(CrownSampleShade.NORMAL, CrownSampleShade.water(9, 2, 0), "0.9 is not past 0.9");
        assertEquals(CrownSampleShade.LOW, CrownSampleShade.water(9, 2, 1), "1.1");
        assertEquals(CrownSampleShade.water(12, 3, 4), CrownSampleShade.water(40, 3, 4), "sounded 12 deep at most");
        assertEquals(CrownSampleShade.water(5, -3, 0), CrownSampleShade.water(5, 1, 0), "the checker by parity, negatives too");
    }

    @Test
    @DisplayName("land: lit where it rises above the sample to its north, shaded where it falls")
    void land() {
        assertEquals(CrownSampleShade.HIGH, CrownSampleShade.land(70, 69));
        assertEquals(CrownSampleShade.NORMAL, CrownSampleShade.land(70, 70));
        assertEquals(CrownSampleShade.LOW, CrownSampleShade.land(68, 70));
    }

    @Test
    @DisplayName("a sample is vanilla's packed map colour, 0 for nothing; 64 a chunk, read at each 2 x 2's cell centre")
    void layout() {
        assertEquals((byte) (12 << 2 | 1), CrownSampleShade.pack(12, CrownSampleShade.NORMAL));
        assertEquals(0, CrownSampleShade.pack(0, CrownSampleShade.HIGH));
        assertEquals(64, CrownSampleShade.PER_CHUNK);
        assertEquals(0, CrownSampleShade.index(0, 0));
        assertEquals(0, CrownSampleShade.index(1, 1));
        assertEquals(7, CrownSampleShade.index(15, 0));
        assertEquals(63, CrownSampleShade.index(15, 15));
        assertEquals(8, CrownSampleShade.index(0, 2));
        assertEquals(1, CrownSampleShade.readAt(0));
        assertEquals(15, CrownSampleShade.readAt(7));
    }

    @Test
    @DisplayName("PathwayTerrain still shades by the same rule: if the Board's map changes, this one must too")
    void sameRuleAsThePathwayBoard() throws IOException {
        final String src = Files.readString(Path.of("src/main/java/com/createstats/surveyor/PathwayTerrain.java"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertTrue(src.contains("double d = depth * 0.1 + ((x + z) & 1) * 0.2;"), "the depth dither moved");
        assertTrue(src.contains("b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : "
                + "MapColor.Brightness.NORMAL;"), "the depth thresholds moved");
        assertTrue(src.contains("while (depth < 12 &&"), "the sounding depth moved");
        assertTrue(src.contains("b = y > north ? MapColor.Brightness.HIGH : y < north ? MapColor.Brightness.LOW : "
                + "MapColor.Brightness.NORMAL;"), "the land rule moved");
    }
}
