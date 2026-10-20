package com.createstats.driverhelmet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutTest {
    private static final int[][] SCREENS = {
        {853, 533}, {960, 540}, {640, 360}, {683, 384}, {480, 270}, {427, 240}, {320, 240},
    };

    @Test
    void noPlateCoversTheHotbarTheOffhandTheStatusRowsOrAnotherPlate() {
        for (int[] s : SCREENS) {
            final int w = s[0];
            final int h = s[1];
            final HudLayout at = HudLayout.of(w, h);
            final String where = w + "x" + h;
            final int[] top = {at.barX - 6, at.barY - 4, at.barX + HudLayout.BAR_WIDTH + 5,
                    at.barY - 4 + HudLayout.TOP_PLATE_HEIGHT};
            final int[] dash = {at.dashX0, at.dashY0, at.dashX1, at.dashY1};
            final int[] hotbar = {w / 2 - HudLayout.HOTBAR_HALF - HudLayout.OFFHAND, h - 22,
                    w / 2 + HudLayout.HOTBAR_HALF + HudLayout.OFFHAND, h};
            final int[] status = {w / 2 - HudLayout.HOTBAR_HALF, h - HudLayout.STATUS_ROWS,
                    w / 2 + HudLayout.HOTBAR_HALF, h - 22};

            assertTrue(onScreen(dash, w, h), where + ": dash off screen");
            assertFalse(overlap(dash, top), where + ": dash on the throttle plate");
            assertFalse(overlap(dash, hotbar), where + ": dash over the hotbar or offhand");
            assertFalse(overlap(dash, status), where + ": dash over the health, armour or food");

            if (at.wheels) {
                final int[] wheels = {at.dashX0, at.wheelsY0, at.dashX1, at.wheelsY1};
                assertTrue(onScreen(wheels, w, h), where + ": wheels off screen");
                assertFalse(overlap(wheels, dash), where + ": wheels on the dash");
                assertFalse(overlap(wheels, top), where + ": wheels on the throttle plate");
                assertFalse(overlap(wheels, hotbar), where + ": wheels over the hotbar");
            }
            if (at.g) {
                final int[] g = {at.gX0, at.gY0, at.gX1, at.gY1};
                final int[] crosshair = {w / 2 - HudLayout.CROSSHAIR_CLEAR, h / 2 - HudLayout.CROSSHAIR_CLEAR,
                        w / 2 + HudLayout.CROSSHAIR_CLEAR, h / 2 + HudLayout.CROSSHAIR_CLEAR};
                assertTrue(onScreen(g, w, h), where + ": g panel off screen");
                assertFalse(overlap(g, top), where + ": g panel on the throttle plate");
                assertFalse(overlap(g, crosshair), where + ": g panel at the crosshair");
                assertFalse(overlap(g, hotbar) || overlap(g, status), where + ": g panel over the hotbar");
                if (h >= 360) {
                    final int[] chat = {0, h - 48 - 90, 320, h - 48};
                    assertFalse(overlap(g, chat), where + ": g panel over the chat");
                }
            }
        }
    }

    @Test
    void onTheOwnersScreenEveryPlateShows() {
        final HudLayout at = HudLayout.of(853, 533);
        assertEquals(853 - HudLayout.MARGIN, at.dashX1);
        assertEquals(533 - 12, at.dashY1);
        assertEquals(853 / 2 - HudLayout.BAR_WIDTH / 2, at.barX);
        assertTrue(at.wheels, "wheels plate");
        assertTrue(at.g, "g panel");
    }

    @Test
    void theTopPlateMovesDownUnderWhateverHoldsTheTopButNeverOntoAnything() {
        final HudLayout owner = HudLayout.of(853, 533);
        assertEquals(owner.barY, owner.barYBelow(0), "nothing there: its usual place");
        assertEquals(40 + HudLayout.GAP + 4, owner.barYBelow(40), "a Jade box down to y 40: just under it");
        for (int[] s : SCREENS) {
            final int w = s[0];
            final int h = s[1];
            final HudLayout at = HudLayout.of(w, h);
            final String where = w + "x" + h;
            for (int blocked : new int[]{0, 30, 60, 120, 400}) {
                final int by = at.barYBelow(blocked);
                assertTrue(by >= at.barY, where + ": never above its usual place");
                final int[] top = {at.barX - 6, by - 4, at.barX + HudLayout.BAR_WIDTH + 5,
                        by + HudLayout.TOP_PLATE_BELOW_BAR};
                final int[] crosshair = {w / 2 - HudLayout.CROSSHAIR_CLEAR, h / 2 - HudLayout.CROSSHAIR_CLEAR,
                        w / 2 + HudLayout.CROSSHAIR_CLEAR, h / 2 + HudLayout.CROSSHAIR_CLEAR};
                if (at.maxBarY > at.barY) {
                    assertFalse(overlap(top, crosshair), where + ": pushed onto the crosshair at " + blocked);
                }
                if (at.g) {
                    assertFalse(overlap(top, new int[]{at.gX0, at.gY0, at.gX1, at.gY1}),
                            where + ": pushed onto the g panel at " + blocked);
                }
                if (at.wheels) {
                    assertFalse(overlap(top, new int[]{at.dashX0, at.wheelsY0, at.dashX1, at.wheelsY1}),
                            where + ": pushed onto the wheels at " + blocked);
                }
            }
        }
    }

    @Test
    void theLedWidthsTheMockUsed() {
        assertEquals(88, HudLayout.BAR_WIDTH);
        assertEquals(17, LedFont.width("BRK", 1, 0, 1));
        assertEquals(46, LedFont.width("087", 2, 1, 1));
        assertEquals(20, LedFont.height(2, 1));
        for (char c : "0123456789DRNBHSTVKL-".toCharArray()) {
            assertTrue(LedFont.has(c), "no LED glyph for " + c);
        }

        for (char c : "ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcxyz0123456789".toCharArray()) {
            assertTrue(LedFont.has(c), "the welcome cannot spell " + c);
        }
        assertTrue(LedFont.lit('1', 2, 0), "the 1's stem");
        assertFalse(LedFont.lit('1', 0, 0));
    }

    private static boolean onScreen(int[] r, int w, int h) {
        return r[0] >= 0 && r[1] >= 0 && r[2] <= w && r[3] <= h;
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }
}
