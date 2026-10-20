package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireStyleTest {
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

        int minX() {
            return px.keySet().stream().mapToInt(k -> (int) (k >> 32)).min().orElse(0);
        }

        int maxX() {
            return px.keySet().stream().mapToInt(k -> (int) (k >> 32)).max().orElse(0);
        }
    }

    @Test
    @DisplayName("weight by load: two pixels for a spur, three from 100 FE/t, four from 1,000, never over half a block")
    void weightFollowsLoadAndScale() {
        assertEquals(2, WireStyle.width(0, 16));
        assertEquals(2, WireStyle.width(99, 16));
        assertEquals(3, WireStyle.width(100, 16));
        assertEquals(4, WireStyle.width(1000, 16));
        assertEquals(4, WireStyle.width(50_000, 16));

        assertEquals(2, WireStyle.width(5000, 4));
        assertEquals(2, WireStyle.width(5000, 5));
        assertEquals(3, WireStyle.width(5000, 6));
        assertEquals(4, WireStyle.width(5000, 8));
        assertEquals(WireStyle.width(1000, 8) + 2, WireStyle.casing(8), "the casing wraps the heaviest line");
    }

    @Test
    @DisplayName("a length is centred on the line between its two block centres, w pixels wide")
    void segmentIsCentred() {
        final Sheet s = new Sheet();
        WireStyle.segment(s, 10, 20, 40, 20, 3, WireStyle.COPPER);
        for (int x = 9; x <= 40; x++) {
            assertTrue(s.inked(x, 19) && s.inked(x, 20) && s.inked(x, 21), "three rows at x " + x);
            assertFalse(s.inked(x, 18) || s.inked(x, 22), "nothing beyond them at x " + x);
        }
    }

    @Test
    @DisplayName("a dead length is dotted: every other pixel, so it never reads as a thin live one")
    void deadLengthIsDotted() {
        final Sheet s = new Sheet();
        WireStyle.dotted(s, 10, 20, 20, 20, WireStyle.COPPER);
        assertTrue(s.inked(10, 20) && s.inked(12, 20) && s.inked(20, 20));
        assertFalse(s.inked(11, 20) || s.inked(13, 20) || s.inked(19, 20));
    }

    @Test
    @DisplayName("a chevron's arms trail behind its tip, clear of the copper by the line's half width")
    void chevronTrailsItsTip() {
        for (int w : new int[] {2, 3, 4}) {
            final Sheet s = new Sheet();
            WireStyle.chevron(s, 50, 20, 1, 0, w, WireStyle.COPPER_DARK);
            final int off = (w - 1) / 2;

            assertTrue(s.inked(50, 20 - 1 - off) && s.inked(50, 20 + 1 + off), "arms start beside the tip at w " + w);
            assertTrue(s.inked(48, 20 - 3 - off) && s.inked(48, 20 + 3 + off), "arms reach back two pixels at w " + w);
            assertEquals(48, s.minX(), "nothing ahead of the tip");
            assertEquals(50, s.maxX());
            for (int k = -off; k <= off; k++) {
                assertFalse(s.inked(50, 20 + k), "the line itself is left to the copper at w " + w);
            }
        }

        final Sheet n = new Sheet();
        WireStyle.chevron(n, 50, 20, 0, -1, 2, WireStyle.COPPER_DARK);
        assertTrue(n.inked(49, 20) && n.inked(51, 20) && n.inked(47, 22) && n.inked(53, 22));
        assertFalse(n.inked(49, 19) || n.inked(51, 19));
    }

    @Test
    @DisplayName("junction, riser, cap and entry: each centred where it is put, the entry a size up from a junction")
    void marksAreCentredAndSized() {
        final Sheet j = new Sheet();
        WireStyle.junction(j, 30, 30, 2);
        assertEquals(WireStyle.COPPER, j.at(30, 30));
        assertEquals(WireStyle.COPPER_DARK, j.at(28, 30));
        assertFalse(j.inked(27, 30));
        assertFalse(j.inked(33, 30));

        final Sheet r = new Sheet();
        WireStyle.riser(r, 30, 30, 2);
        assertEquals(WireStyle.COPPER_DARK, r.at(30, 30), "the riser's centre point");
        assertEquals(WireStyle.CORE, r.at(29, 30), "round a pale core");
        assertEquals(WireStyle.COPPER_DARK, r.at(28, 30));

        final Sheet e = new Sheet();
        WireStyle.entry(e, 30, 30, 2);
        assertEquals(WireStyle.CORE, e.at(30, 30));
        assertEquals(WireStyle.COPPER, e.at(28, 30), "a copper ring inside");
        assertEquals(WireStyle.COPPER_DARK, e.at(27, 30), "a dark rim outside");
        assertFalse(e.inked(26, 30));
        assertTrue(e.maxX() - e.minX() > j.maxX() - j.minX(), "the entry is wider than a junction");

        final Sheet c = new Sheet();
        WireStyle.cap(c, 30, 30, 2);
        assertEquals(WireStyle.COPPER_DARK, c.at(30, 30));
        assertEquals(WireStyle.COPPER_DARK, c.at(29, 30));
        assertFalse(c.inked(28, 30));
    }

    @Test
    @DisplayName("a length with no flow known yet is pale, never copper")
    void unknownFlowIsPale() {
        assertEquals(WireStyle.COPPER_PALE, WireStyle.colour(-1));
        assertEquals(WireStyle.COPPER, WireStyle.colour(0));
        assertEquals(WireStyle.COPPER, WireStyle.colour(300));
    }
}
