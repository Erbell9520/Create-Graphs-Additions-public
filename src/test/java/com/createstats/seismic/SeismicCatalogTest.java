package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicCatalogTest {
    @Test
    @DisplayName("ore ids are recognised by their tag; voids and liquids are not ores")
    void ids() {
        assertTrue(SeismicCatalog.isOre("c:ores/iron"));
        assertEquals("iron", SeismicCatalog.orePath("c:ores/iron"));
        assertFalse(SeismicCatalog.isOre("c:ores/"));
        assertFalse(SeismicCatalog.isOre(SeismicCatalog.VOIDS));
        assertFalse(SeismicCatalog.isOre(null));
        assertNull(SeismicCatalog.mineral("c:ores/osmium"));
    }

    @Test
    @DisplayName("a vein's label is its kind: IRON, VOID, LAVA or WATER, DEBRIS, and a modded ore's own name")
    void labels() {
        assertEquals("IRON", SeismicCatalog.veinLabel("c:ores/iron", 0));
        assertEquals("VOID", SeismicCatalog.veinLabel(SeismicCatalog.VOIDS, 0));
        assertEquals("LAVA", SeismicCatalog.veinLabel(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_LAVA));
        assertEquals("WATER", SeismicCatalog.veinLabel(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_WATER));
        assertEquals("DEBRIS", SeismicCatalog.veinLabel("c:ores/netherite_scrap", 0));
        assertEquals("OSMIUM", SeismicCatalog.veinLabel("c:ores/osmium", 0));
        assertEquals("BLACK QUARTZ", SeismicCatalog.veinLabel("c:ores/black_quartz", 0));
    }

    @Test
    @DisplayName("the dial runs from the softest echo to the hardest, unknown ores after every known one")
    void dialOrder() {
        final List<String> ids = new ArrayList<>(List.of("c:ores/gold", "c:ores/osmium", "c:ores/coal",
                SeismicCatalog.LIQUIDS, "c:ores/iron", SeismicCatalog.VOIDS, "c:ores/apatite", "c:ores/diamond"));
        ids.sort(SeismicCatalog::compare);
        assertEquals(List.of(SeismicCatalog.VOIDS, SeismicCatalog.LIQUIDS, "c:ores/coal", "c:ores/iron",
                "c:ores/diamond", "c:ores/gold", "c:ores/apatite", "c:ores/osmium"), ids);
    }

    @Test
    @DisplayName("every station has a bright colour and a line naming its mineral, or its tag when unknown")
    void coloursAndLines() {
        for (final String id : List.of("c:ores/iron", "c:ores/coal", "c:ores/zinc", "c:ores/osmium",
                SeismicCatalog.VOIDS, SeismicCatalog.LIQUIDS)) {
            final int c = SeismicCatalog.colour(id, 0);
            final int brightest = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF));
            assertTrue(brightest >= 0x90, id + " is too dark to read through rock: " + Integer.toHexString(c));
        }
        assertEquals("magnetite, 38 MRayl", SeismicCatalog.subLabel("c:ores/iron"));
        assertEquals("c:ores/osmium", SeismicCatalog.subLabel("c:ores/osmium"));
        assertTrue(SeismicCatalog.colour(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_LAVA)
                != SeismicCatalog.colour(SeismicCatalog.LIQUIDS, SeismicCatalog.KIND_WATER));
        assertEquals(SeismicCatalog.colour("c:ores/osmium", 0), SeismicCatalog.colour("c:ores/osmium", 0),
                "a modded ore's colour is the same every time");
    }

    @Test
    @DisplayName("the bowl's bottom, and the band note: diamond from a lawn lies deeper, iron at 48 does not (P1-2)")
    void bands() {
        assertEquals(0, SeismicCatalog.bowlBottom(64, 64));
        assertTrue(SeismicCatalog.bandMissed("c:ores/diamond", 64, 64), "a lawn rig's 64 reaches Y 0; diamond peaks at -59");
        assertFalse(SeismicCatalog.bandMissed("c:ores/diamond", 0, 64), "carried down to Y 0 it reaches -64");
        assertFalse(SeismicCatalog.bandMissed("c:ores/iron", 64, 48), "Y 16 is the bowl's bottom: inside");
        assertTrue(SeismicCatalog.bandMissed("c:ores/iron", 64, 32));
        assertTrue(SeismicCatalog.bandMissed("c:ores/coal", 40, 32), "a band above the plate is outside too");
        assertFalse(SeismicCatalog.bandMissed("c:ores/osmium", 64, 32), "an ore the table does not know claims nothing");
        assertFalse(SeismicCatalog.bandMissed(SeismicCatalog.VOIDS, 64, 32));
        assertNull(SeismicCatalog.densest(SeismicCatalog.LIQUIDS));
    }
}
