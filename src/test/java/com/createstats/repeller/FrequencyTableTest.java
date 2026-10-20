package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrequencyTableTest {
    @Test
    void theHashIsSpecifiedFnv1aAndNeverDrifts() {
        assertEquals(0xcbf29ce484222325L, FrequencyTable.fnv1a64(""));
        assertEquals(0xaf63dc4c8601ec8cL, FrequencyTable.fnv1a64("a"));
        assertEquals(0x0bef04f99bc34f8cL, FrequencyTable.fnv1a64("minecraft:zombie"));
        assertEquals(0xdf1326a6d10f51bfL, FrequencyTable.fnv1a64("minecraft:phantom"));

        assertEquals(270, FrequencyTable.homeSlot("minecraft:zombie"));
        assertEquals(521, FrequencyTable.homeSlot("minecraft:phantom"));
    }

    @Test
    void placedMobsKeepTheirPlaceAndEveryoneElseGetsTheirHome() {
        final Map<String, Double> placed = Map.of("minecraft:zombie", 31.5, "minecraft:ghast", 20.5);
        final FrequencyTable t = FrequencyTable.build(placed,
                List.of("minecraft:zombie", "minecraft:ghast", "modded:beast"));
        assertEquals(315, t.tenthsOf("minecraft:zombie"));
        assertEquals(205, t.tenthsOf("minecraft:ghast"));
        assertEquals(FrequencyTable.homeSlot("modded:beast"), t.tenthsOf("modded:beast"));
        assertEquals("31.5", FrequencyTable.format(t.tenthsOf("minecraft:zombie")));
    }

    @Test
    void aCollisionGoesToTheNearestFreeSlotAndPlacedEntriesWin() {
        final int zombie = 315;
        String rival = null;
        for (int i = 0; i < 100_000 && rival == null; i++) {
            if (FrequencyTable.homeSlot("modded:rival_" + i) == zombie) {
                rival = "modded:rival_" + i;
            }
        }
        assertTrue(rival != null, "no colliding id found to test with");
        final FrequencyTable t = FrequencyTable.build(Map.of("minecraft:zombie", 31.5),
                List.of(rival, "minecraft:zombie"));
        assertEquals(zombie, t.tenthsOf("minecraft:zombie"), "the placed mob keeps its station");
        assertEquals(zombie + 1, t.tenthsOf(rival), "the stranger moves up one tenth");
    }

    @Test
    void theResultDependsOnTheSetNeverOnTheOrder() {
        final List<String> ids = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            ids.add("mod" + (i % 7) + ":mob_" + i);
        }
        final Map<String, Double> placed = new LinkedHashMap<>();
        placed.put("mod1:mob_1", 40.0);
        placed.put("mod2:mob_2", 40.0);
        placed.put("mod3:mob_3", 99.0);
        final FrequencyTable a = FrequencyTable.build(placed, ids);
        final List<String> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled, new java.util.Random(7));
        final Map<String, Double> reversed = new LinkedHashMap<>();
        reversed.put("mod3:mob_3", 99.0);
        reversed.put("mod2:mob_2", 40.0);
        reversed.put("mod1:mob_1", 40.0);
        final FrequencyTable b = FrequencyTable.build(reversed, shuffled);
        for (String id : ids) {
            assertEquals(a.tenthsOf(id), b.tenthsOf(id), id);
        }
    }

    @Test
    void everyStationIsInBandAndOneMobToASlotUntilTheBandIsFull() {
        final List<String> ids = new ArrayList<>();
        for (int i = 0; i < FrequencyTable.SLOTS; i++) {
            ids.add("pack:mob_" + i);
        }
        final FrequencyTable t = FrequencyTable.build(Map.of("pack:mob_3", 12.0, "pack:mob_4", 80.0), ids);
        final Set<Integer> used = new HashSet<>();
        for (String id : ids) {
            final int f = t.tenthsOf(id);
            assertTrue(f >= FrequencyTable.MIN_TENTHS && f <= FrequencyTable.MAX_TENTHS, id + " at " + f);
            assertTrue(used.add(f), id + " shares " + f);
        }
        assertEquals(200, t.tenthsOf("pack:mob_3"), "below the band is clamped to 20.0");
        assertEquals(650, t.tenthsOf("pack:mob_4"), "above it to 65.0");
        assertEquals(FrequencyTable.SLOTS, used.size(), "451 mobs fill all 451 slots exactly");
    }

    @Test
    void theLabelIsTheSameInEveryLocale() {
        final Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("64.0", FrequencyTable.format(640));
            assertEquals("20.5", FrequencyTable.format(205));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void byFrequencyIsAscending() {
        final FrequencyTable t = FrequencyTable.build(Map.of("a:x", 50.0, "a:y", 30.0, "a:z", 40.0), List.of());
        assertEquals(List.of("a:y", "a:z", "a:x"), t.byFrequency());
    }
}
