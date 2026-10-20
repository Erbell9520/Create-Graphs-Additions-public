package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WirePlanTest {
    private static final int S = 8;

    private static final class Sheet implements WireStyle.Ink {
        final Map<Long, Integer> px = new HashMap<>();

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    px.put(((long) x << 32) | (y & 0xFFFFFFFFL), argb);
                }
            }
        }

        boolean inked(int x, int y) {
            return px.containsKey(((long) x << 32) | (y & 0xFFFFFFFFL));
        }

        int at(int x, int y) {
            return px.getOrDefault(((long) x << 32) | (y & 0xFFFFFFFFL), 0);
        }
    }

    private static int c(int block) {
        return block * S + S / 2;
    }

    private static WireGraph tee() {
        final List<WireGraph.Wire> w = new ArrayList<>();
        final int[] trunk = {250, 250, 250, 240, 240, 240, 0, 0};
        for (int x = 0; x < trunk.length; x++) {
            w.add(new WireGraph.Wire(x, 64, 0, trunk[x]));
        }
        for (int z = -1; z >= -3; z--) {
            w.add(new WireGraph.Wire(2, 64, z, 10));
        }
        return new WireGraph(w);
    }

    @Test
    @DisplayName("the entry wears the big terminal, a junction its dot, a run's last node its cap")
    void marksWhereTheyBelong() {
        final Sheet s = new Sheet();
        WirePlan.draw(s, tee(), 60, 70, S, WirePlanTest::c, WirePlanTest::c, null);
        assertEquals(WireStyle.CORE, s.at(c(0), c(0)), "the pale core of the entry's terminal");
        assertEquals(WireStyle.COPPER_DARK, s.at(c(0) - 3, c(0)), "its dark rim, a size up from a junction");
        assertEquals(WireStyle.COPPER, s.at(c(2), c(0)), "the tee's junction dot is copper at its centre");
        assertEquals(WireStyle.COPPER_DARK, s.at(c(2), c(-3)), "the branch's tip is capped");
        assertEquals(WireStyle.COPPER_DARK, s.at(c(7), c(0)), "and so is the trunk's far end");
    }

    @Test
    @DisplayName("a length carrying nothing is dotted; a live one is solid copper as wide as its load allows")
    void deadIsDottedLiveIsSolid() {
        final Sheet s = new Sheet();
        WirePlan.draw(s, tee(), 60, 70, S, WirePlanTest::c, WirePlanTest::c, null);

        assertTrue(s.inked(c(6), c(0)) && s.inked(c(6) + 2, c(0)), "dots");
        assertFalse(s.inked(c(6) + 1, c(0)), "and the gap between them");

        assertEquals(WireStyle.COPPER, s.at(c(3) + 4, c(0) - 1));
        assertEquals(WireStyle.COPPER, s.at(c(3) + 4, c(0) + 1));
        assertFalse(s.inked(c(3) + 4, c(0) - 2), "nothing beyond the copper's three rows");
    }

    @Test
    @DisplayName("the length one hop out of the entry wears a chevron pointing downstream, its arms behind the tip")
    void chevronPointsDownstream() {
        final Sheet s = new Sheet();
        WirePlan.draw(s, tee(), 60, 70, S, WirePlanTest::c, WirePlanTest::c, null);

        final int tip = (c(1) + c(2)) / 2 + 1;
        assertEquals(WireStyle.COPPER_DARK, s.at(tip, c(0) - 2));
        assertEquals(WireStyle.COPPER_DARK, s.at(tip, c(0) + 2));
        assertEquals(WireStyle.COPPER_DARK, s.at(tip - 1, c(0) - 3), "the arm trails back west, away from the way the power runs");
        assertEquals(WireStyle.COPPER_DARK, s.at(tip - 2, c(0) - 4));

        assertFalse(s.inked(tip + 1, c(0) - 3), "nothing ahead of the tip");
    }

    @Test
    @DisplayName("a hovered fixture's wire back to the entry is cased in white, under the copper")
    void focusIsCased() {
        final Sheet s = new Sheet();
        WirePlan.draw(s, tee(), 60, 70, S, WirePlanTest::c, WirePlanTest::c, List.of(new int[] {3, 64, -3}));

        assertEquals(WireStyle.CASING, s.at(c(2) - 3, c(-1)), "the casing's edge");
        assertEquals(WireStyle.COPPER, s.at(c(2), c(-1)), "the copper over it");

        assertEquals(WireStyle.CASING, s.at(c(1), c(0) - 3));
        assertFalse(s.at(c(5), c(0) - 3) == WireStyle.CASING, "the trunk past the tee is not on the way back");
    }

    @Test
    @DisplayName("an empty circuit draws nothing at all")
    void emptyDrawsNothing() {
        final Sheet s = new Sheet();
        WirePlan.draw(s, new WireGraph(List.of()), 60, 70, S, WirePlanTest::c, WirePlanTest::c, null);
        assertTrue(s.px.isEmpty());
    }
}
