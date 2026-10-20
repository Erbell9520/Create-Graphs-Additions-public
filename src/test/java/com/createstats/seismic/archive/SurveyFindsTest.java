package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyFindsTest {
    @Test
    @DisplayName("bearings and the sixteen points")
    void compass() {
        assertEquals(0.0, SurveyFinds.bearing(0, 0, 0, -10), 1e-9);
        assertEquals(90.0, SurveyFinds.bearing(0, 0, 10, 0), 1e-9);
        assertEquals(180.0, SurveyFinds.bearing(0, 0, 0, 10), 1e-9);
        assertEquals(270.0, SurveyFinds.bearing(0, 0, -10, 0), 1e-9);
        assertEquals("N", SurveyFinds.compass(0));
        assertEquals("N", SurveyFinds.compass(11.2));
        assertEquals("NNE", SurveyFinds.compass(11.3));
        assertEquals("NE", SurveyFinds.compass(45));
        assertEquals("SSW", SurveyFinds.compass(202.5));
        assertEquals("NNW", SurveyFinds.compass(337.6));
        assertEquals("N", SurveyFinds.compass(359.9));
        assertEquals("N", SurveyFinds.compass(-0.1));
    }

    @Test
    @DisplayName("a find says how far along the ground, which way and at what height")
    void where() {
        final SurveyScene.Body b = new SurveyScene.Body("c:ores/diamond", 0, 6, 1, 0, 0, 0, new int[0], 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0L, 0L, 0, 0, 0, 0, 0.0f);
        assertEquals("23 m NE, Y -54", SurveyFinds.place(b, 16, -54, -17, 0.5, 0.5).where());
        assertEquals("below, Y -54", SurveyFinds.place(b, 0, -54, 0, 0.5, 0.5).where());
    }

    @Test
    @DisplayName("ages in played time, one unit throughout")
    void ages() {
        assertEquals("now", SurveyFinds.ago(1_000L, 990L));
        assertEquals("40 s", SurveyFinds.ago(1_800L, 1_000L));
        assertEquals("3 min", SurveyFinds.ago(4_600L, 1_000L));
        assertEquals("2 h", SurveyFinds.ago(145_000L, 1_000L));
        assertEquals("5 d", SurveyFinds.ago(5L * 1_728_000L + 10L, 10L));
        assertEquals("never", SurveyFinds.ago(10L, Long.MIN_VALUE));
    }

    @Test
    @DisplayName("names: one per target, the station's; a liquid pocket says which liquid")
    void names() {
        assertEquals("diamond", SurveyFinds.noun("c:ores/diamond", 0));
        assertEquals("debris", SurveyFinds.noun("c:ores/netherite_scrap", 0));
        assertEquals("void", SurveyFinds.noun(SeismicCatalog.VOIDS, 0));
        assertEquals("lava", SurveyFinds.noun(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_LAVA));
        assertEquals("water", SurveyFinds.noun(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_WATER));
        assertEquals("veins", SurveyFinds.plural("c:ores/iron", 2));
        assertEquals("void", SurveyFinds.plural(SeismicCatalog.VOIDS, 1));
        assertEquals("pockets", SurveyFinds.plural(SeismicCatalog.LIQUIDS, 3));
        assertEquals("Voids", SurveyFinds.station(SeismicCatalog.VOIDS));
        assertEquals("Liquids", SurveyFinds.station(SeismicCatalog.LIQUIDS));
        assertEquals("Diamond", SurveyFinds.station("c:ores/diamond"));
        assertEquals("Liquids: lava", SurveyFinds.findName(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_LAVA));
    }

    @Test
    @DisplayName("the board's lines for the owner's iron rig, read from beside it")
    void boardLines() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/iron", 64, 1_000L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/diamond", 64, 2_000L);
        final long rig = ArchiveBlow.key(158, 69, -359);
        final SurveyScene s = SceneBuilder.build(a, List.of(rig), List.of(rig), Long.MIN_VALUE, 160, 69, -357, 256,
                5_000L, 1L);
        final List<String> lines = SurveyText.lines(s, "c:ores/iron", 160.5, -356.5, 8, 5_000L);
        assertTrue(lines.get(0).startsWith("Nearest iron ") && lines.get(0).contains(", Y "), lines.get(0));
        assertTrue(lines.get(1).startsWith("Iron: ") && lines.get(1).contains(" veins, "), lines.get(1));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Last blow 2 min ago, radius 64")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("diamond")), "the other target is listed too: " + lines);
        assertEquals(1, SurveyText.lines(s, "c:ores/iron", 160.5, -356.5, 1, 5_000L).size());
        for (final String l : lines) {
            assertFalse(l.indexOf((char) 0x2014) >= 0, "no em dash in a line: " + l);
        }
        final SurveyScene none = SceneBuilder.build(a, List.of(), List.of(), Long.MIN_VALUE, 0, 0, 0, 256, 0L, 0L);
        assertEquals(List.of("Seismic Scanner: no survey yet"), SurveyText.lines(none, "", 0, 0, 4, 0L));
    }
}
