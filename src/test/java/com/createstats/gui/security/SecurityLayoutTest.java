package com.createstats.gui.security;

import com.createstats.statsmk3.ui.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityLayoutTest {
    private static final Object[][] SIZES = {
            {"1080p @ scale 2", 960, 540},
            {"1080p @ scale 3 (the default)", 640, 360},
            {"1440p @ scale 3", 853, 480},
            {"1440p @ scale 2", 1280, 720},
            {"4K @ scale 4", 960, 540},
            {"1366x768 @ scale 2", 683, 384},
            {"1600x900 @ scale 2", 800, 450},
            {"ultrawide, short", 1720, 400},
    };

    private static SecurityLayout at(Object[] size) {
        return SecurityLayout.of((Integer) size[1], (Integer) size[2]);
    }

    @Test
    void everyCommonSizeKeepsTheInspector() {
        for (Object[] size : SIZES) {
            final SecurityLayout box = at(size);
            assertTrue(box.hasDock(), size[0] + " lost the inspector entirely");
            assertTrue(box.dock.w >= SecurityLayout.DOCK_MIN_W,
                    size[0] + " gave the inspector " + box.dock.w + ", under the floor");
        }
    }

    @Test
    void workAreaIsNeverSqueezed() {
        for (Object[] size : SIZES) {
            final SecurityLayout box = at(size);
            assertTrue(box.work.w >= SecurityLayout.MIN_WORK_W,
                    size[0] + " squeezed the work area to " + box.work.w);
        }
    }

    @Test
    void railWorkAndDockTileTheBody() {
        for (Object[] size : SIZES) {
            final SecurityLayout box = at(size);
            assertEquals(box.rail.right(), box.work.x, 0.01f, size[0] + ": gap after the rail");
            if (box.hasDock()) {
                assertEquals(box.work.right(), box.dock.x, 0.01f, size[0] + ": gap before the dock");
                assertEquals(box.root.right(), box.dock.right(), 0.01f,
                        size[0] + ": the dock does not reach the edge");
            } else {
                assertEquals(box.root.right(), box.work.right(), 0.01f,
                        size[0] + ": the work area does not reach the edge");
            }
        }
    }

    @Test
    void defaultGuiScaleCollapsesTheRailRatherThanTheInspector() {
        final SecurityLayout box = SecurityLayout.of(640, 360);
        assertFalse(box.railLabels, "the rail kept its labels and squeezed the inspector instead");
        assertEquals(SecurityLayout.RAIL_W_ICON, box.rail.w, 0.01f);
        assertTrue(box.hasDock(), "the inspector vanished at the most common size there is");
        assertTrue(box.foldedRows(),
                "a 360 work area cannot hold six columns, so rows must fold to two lines");
    }

    @Test
    void wideWindowKeepsEverything() {
        final SecurityLayout box = SecurityLayout.of(1280, 720);
        assertTrue(box.railLabels, "a 1280 wide window has room for rail labels");
        assertEquals(SecurityLayout.DOCK_W, box.dock.w, 0.01f);
        assertFalse(box.foldedRows(), "a wide work area should draw full columns");
    }

    @Test
    void noSizeProducesABackwardsRect() {
        for (int w = 320; w <= 2400; w += 17) {
            for (int h = 200; h <= 1400; h += 23) {
                final SecurityLayout box = SecurityLayout.of(w, h);
                assertNonNegative(box.root, w, h, "root");
                assertNonNegative(box.rail, w, h, "rail");
                assertNonNegative(box.work, w, h, "work");
                assertNonNegative(box.wideWork, w, h, "wideWork");
                assertNonNegative(box.dock, w, h, "dock");
                assertNonNegative(box.viewArea(box.hasDock()), w, h, "viewArea");
                assertNonNegative(box.dockInner(), w, h, "dockInner");
            }
        }
    }

    @Test
    void wizardDoneIsAlwaysOnThePanel() {
        for (Object[] size : SIZES) {
            assertWizardDoneFits(at(size), String.valueOf(size[0]));
        }
        for (int w = 320; w <= 2400; w += 17) {
            for (int h = 240; h <= 1400; h += 23) {
                assertWizardDoneFits(SecurityLayout.of(w, h), w + "x" + h);
            }
        }
    }

    private static void assertWizardDoneFits(SecurityLayout box, String name) {
        final Rect area = box.viewArea(false);
        final Rect done = SecurityLayout.wizardDone(area);
        final Rect clip = box.wideWork;
        assertTrue(done.x >= clip.x && done.right() <= clip.right()
                        && done.y >= clip.y && done.bottom() <= clip.bottom(),
                name + ": the wizard's Done button " + done + " is outside its clip " + clip);
        final float cardsRoom = done.y - 8.0f - (area.y + 40.0f);
        assertTrue(cardsRoom >= 52.0f,
                name + ": only " + cardsRoom + " px above Done, not one whole card");
    }

    private static void assertNonNegative(Rect r, int w, int h, String name) {
        assertTrue(r.w >= 0.0f && r.h >= 0.0f,
                name + " went backwards at " + w + "x" + h + ": " + r);
    }

    @Test
    void railRowsNeverOverlap() {
        final SecurityLayout box = SecurityLayout.of(1280, 720);
        for (int groups = 1; groups <= 3; groups++) {
            for (int i = 0; i < 8; i++) {
                final Rect a = box.railItem(i, groups);
                final Rect b = box.railItem(i + 1, groups);
                assertTrue(b.y >= a.bottom(),
                        "rail rows " + i + " and " + (i + 1) + " overlap at " + groups + " groups");
            }
        }
    }

    private static final int RAIL_ITEMS = 2 + com.createstats.power.SecurityDevice.DeviceClass.all().length + 3;
    private static final int RAIL_GROUPS = 3;

    @Test
    void theWholeRailFitsAtTheCommonSizes() {
        final int[][] sizes = {{640, 360}, {853, 533}, {853, 480}, {683, 384}, {800, 450}, {960, 540}, {1280, 720}};
        for (int[] size : sizes) {
            final SecurityLayout box = SecurityLayout.of(size[0], size[1], RAIL_ITEMS, RAIL_GROUPS);
            final String name = size[0] + "x" + size[1];
            assertFalse(box.railScrolls, name + ": the rail needs a scroll to show " + RAIL_ITEMS + " tabs");
            final Rect last = box.railItem(RAIL_ITEMS - 1, RAIL_GROUPS);
            assertTrue(last.bottom() <= box.railViewport().bottom(),
                    name + ": the last tab ends at " + last.bottom() + ", below the rail at "
                            + box.railViewport().bottom());
            assertTrue(box.railPitch >= SecurityLayout.RAIL_ITEM_MIN,
                    name + ": rows squeezed to " + box.railPitch + ", under the floor");
        }
    }

    @Test
    void aRailThatFitsKeepsItsFullPitch() {
        for (int[] size : new int[][]{{853, 533}, {960, 540}, {1280, 720}}) {
            final SecurityLayout box = SecurityLayout.of(size[0], size[1], RAIL_ITEMS, RAIL_GROUPS);
            final String name = size[0] + "x" + size[1];
            assertEquals(SecurityLayout.RAIL_ITEM_H, box.railPitch, 0.01f, name + " row pitch");
            assertTrue(box.railGroupH >= SecurityLayout.RAIL_GROUP_TIGHT && box.railGroupH <= SecurityLayout.RAIL_GROUP_H,
                    name + " caption " + box.railGroupH);
            assertFalse(box.railScrolls, name + " scrolls");
            final float avail = box.railViewport().h - com.createstats.statsmk3.ui.Tokens.S3;
            assertTrue(RAIL_GROUPS * (box.railGroupH + 1.0f) + RAIL_ITEMS * box.railPitch > avail
                            || box.railGroupH == SecurityLayout.RAIL_GROUP_H,
                    name + ": the captions gave up more than they had to (" + box.railGroupH + ")");
        }
        final SecurityLayout big = SecurityLayout.of(1280, 720, RAIL_ITEMS, RAIL_GROUPS);
        assertEquals(SecurityLayout.RAIL_GROUP_H, big.railGroupH, 0.01f, "1280x720 caption");
    }

    @Test
    void aRailTooShortForTheTightestPitchScrollsAndOneThatDoesNotFits() {
        final SecurityLayout tiny = SecurityLayout.of(320, 240, RAIL_ITEMS, RAIL_GROUPS);
        assertTrue(tiny.railScrolls, "a 240 tall window cannot show every tab at once");
        assertEquals(SecurityLayout.RAIL_ITEM_MIN, tiny.railPitch, 0.01f);
        assertTrue(tiny.railScrollMax() > 0.0f, "a scrolling rail must have somewhere to scroll to");
        for (int w = 320; w <= 2400; w += 17) {
            for (int h = 200; h <= 1400; h += 23) {
                final SecurityLayout box = SecurityLayout.of(w, h, RAIL_ITEMS, RAIL_GROUPS);
                if (!box.railScrolls) {
                    final Rect last = box.railItem(RAIL_ITEMS - 1, RAIL_GROUPS);
                    assertTrue(last.bottom() <= box.railViewport().bottom(),
                            w + "x" + h + ": an unscrolled rail ran past its viewport");
                }
            }
        }
    }
}
