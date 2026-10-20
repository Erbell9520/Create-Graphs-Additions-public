package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiofuelPagesTest {
    private static final Map<String, Integer> TICKS = Map.of(
            "plank", 300, "log", 300, "sapling", 100, "brick", 3000, "flower", 260,
            "coal", 0, "junk", -5);

    private static List<BiofuelPages.Page<String>> build(int perPage) {
        return BiofuelPages.build(TICKS.keySet(), TICKS::get, Comparator.naturalOrder(), perPage);
    }

    @Test
    @DisplayName("items with the same burn time share a row, longest first")
    void groupedByDurationLongestFirst() {
        final List<BiofuelPages.Page<String>> pages = build(36);
        assertEquals(List.of(3000, 300, 260, 100),
                pages.stream().map(BiofuelPages.Page::burnTicks).toList());
        assertEquals(List.of("log", "plank"), pages.get(1).items(), "sorted inside the group");
    }

    @Test
    @DisplayName("an item worth nothing is not promised a row")
    void unpricedItemsAreSkipped() {
        final List<String> listed = new ArrayList<>();
        build(36).forEach(p -> listed.addAll(p.items()));
        assertTrue(!listed.contains("coal") && !listed.contains("junk"), "listed: " + listed);
        assertEquals(5, listed.size(), "everything with a positive burn time is listed once");
    }

    @Test
    @DisplayName("a group larger than a page splits, and no page overruns its grid")
    void oversizedGroupsSplit() {
        final List<Integer> many = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            many.add(i);
        }
        final List<BiofuelPages.Page<Integer>> pages =
                BiofuelPages.build(many, i -> 300, Comparator.naturalOrder(), 36);
        assertEquals(List.of(36, 36, 8), pages.stream().map(p -> p.items().size()).toList());
        assertTrue(pages.stream().allMatch(p -> p.burnTicks() == 300));
        final List<Integer> flat = new ArrayList<>();
        pages.forEach(p -> flat.addAll(p.items()));
        assertEquals(many, flat, "no item lost or repeated across the split, order kept");
    }

    @Test
    @DisplayName("a page has to hold at least one slot")
    void zeroSlotsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> build(0));
    }

    @Test
    @DisplayName("burn times read the way a player reads a clock")
    void durationsReadAsClocks() {
        assertEquals("30 s", BiofuelValue.duration(BiofuelGrade.LOG.burnTicks()), "a log");
        assertEquals("1 s", BiofuelValue.duration(BiofuelValue.UNKNOWN_TICKS), "the floor");
        assertEquals("13 s", BiofuelValue.duration(260), "a flower");
        assertEquals("7.5 s", BiofuelValue.duration(150), "a fraction is kept under a minute");
        assertEquals("10 s", BiofuelValue.duration(BiofuelValue.COMPACTED_BURN_TICKS), "the brick");
        assertEquals("36 s", BiofuelValue.duration(BiofuelGrade.BALE.burnTicks()), "a hay bale");
        assertEquals("1 min", BiofuelValue.duration(1_200));
        assertEquals("3 min 20 s", BiofuelValue.duration(4_001), "a dried kelp block rounds to a second");
        assertEquals("0 s", BiofuelValue.duration(-3), "never negative");
    }
}
