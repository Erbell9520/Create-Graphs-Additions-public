package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeltPassCounterTest {
    private static final float GATE = 3.5f;

    private static List<BeltPassCounter.Item> items(Object... idKeyPos) {
        final List<BeltPassCounter.Item> out = new ArrayList<>();
        for (int i = 0; i < idKeyPos.length; i += 3) {
            out.add(new BeltPassCounter.Item((Integer) idKeyPos[i], (String) idKeyPos[i + 1],
                    ((Number) idKeyPos[i + 2]).floatValue(), 1));
        }
        return out;
    }

    @Test
    void oneCrossingIsCountedOnce() {
        final BeltPassCounter c = new BeltPassCounter();
        float pos = 2.8f;
        int total = 0;
        for (long t = 0; t < 40; t++) {
            final Map<String, Integer> n = c.observe(t, items(7, "raw_iron", pos), GATE, true);
            total += n.getOrDefault("raw_iron", 0);
            pos += 0.1f;
        }
        assertEquals(1, total);
    }

    @Test
    void aReverseBeltCountsTheOtherWay() {
        final BeltPassCounter c = new BeltPassCounter();
        c.observe(0, items(1, "coal", 3.6f), GATE, false);
        assertEquals(Map.of("coal", 1), c.observe(1, items(1, "coal", 3.4f), GATE, false));
        final BeltPassCounter d = new BeltPassCounter();
        d.observe(0, items(1, "coal", 3.4f), GATE, true);
        assertTrue(d.observe(1, items(1, "coal", 3.6f), GATE, false).isEmpty(),
                "moving up a belt that runs down is not a pass");
    }

    @Test
    void removedBeforeTheGateIsNeverCounted() {
        final BeltPassCounter c = new BeltPassCounter();
        c.observe(0, items(5, "bread", 3.0f), GATE, true);
        c.observe(1, items(5, "bread", 3.3f), GATE, true);
        assertTrue(c.observe(2, items(), GATE, true).isEmpty());
        assertEquals(Long.MIN_VALUE, c.lastPass());
    }

    @Test
    void insertedPastTheGateIsNeverCounted() {
        final BeltPassCounter c = new BeltPassCounter();
        c.observe(0, items(), GATE, true);
        assertTrue(c.observe(1, items(9, "sand", 3.9f), GATE, true).isEmpty());
        assertTrue(c.observe(2, items(9, "sand", 4.2f), GATE, true).isEmpty());
    }

    @Test
    void twoInOneTickAreTwo() {
        final BeltPassCounter c = new BeltPassCounter();
        c.observe(0, items(1, "a", 3.4f, 2, "a", 3.45f), GATE, true);
        assertEquals(Map.of("a", 2), c.observe(1, items(1, "a", 3.6f, 2, "a", 3.55f), GATE, true));
    }

    @Test
    void aWholeStackCountsItsSize() {
        final BeltPassCounter c = new BeltPassCounter();
        c.observe(0, List.of(new BeltPassCounter.Item(1, "a", 3.4f, 16)), GATE, true);
        assertEquals(Map.of("a", 16), c.observe(1, List.of(new BeltPassCounter.Item(1, "a", 3.6f, 16)), GATE, true));
    }

    @Test
    void ratesAreTheLastMinute() {
        final BeltPassCounter c = new BeltPassCounter();

        for (long t = 0; t <= 2000; t++) {
            final List<BeltPassCounter.Item> on = new ArrayList<>();
            final int id = (int) (t / 20);
            final float pos = 3.0f + (t % 20) * 0.05f;
            on.add(new BeltPassCounter.Item(id, "iron", pos, 1));
            c.observe(t, on, GATE, true);
        }
        assertEquals(60.0, c.perMinute("iron", 2001), 1e-9);
        assertEquals(60.0, c.totalPerMinute(2001), 1e-9);
        assertEquals("iron", c.top(3, 2001).get(0).key());
        assertTrue(c.lastPass() >= 1990);
    }
}
