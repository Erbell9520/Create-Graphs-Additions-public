package com.createstats.statsmk3.view;

import com.createstats.statsmk3.ui.Rect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioLayoutTest {
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

    @Test
    void everyCommonWindowSizeGetsAPreview() {
        for (final Object[] size : SIZES) {
            final String name = (String) size[0];
            final StudioLayout box = StudioLayout.of((int) size[1], (int) size[2]);

            assertTrue(box.hasDock(), name + ": no preview dock at "
                    + size[1] + " x " + size[2] + " (body width " + box.root.w + ")");
            assertTrue(box.hasPreview(), name + ": dock present but the face was skipped, "
                    + "bezel frame is " + box.bezelFrame().h + " tall");
            assertTrue(box.work.w >= StudioLayout.MIN_WORK_W - 0.5f,
                    name + ": the dock squeezed the work area to " + box.work.w);
        }
    }

    @Test
    void defaultGuiScaleOn1080pShowsTheDock() {
        final StudioLayout box = StudioLayout.of(640, 360);
        final float bodyW = box.root.w;

        assertTrue(bodyW < 620.0f,
                "this test is only meaningful while the default GUI scale body is under the old "
                        + "620 pixel gate; it measured " + bodyW);
        assertTrue(box.hasDock(), "the preview dock must survive the default GUI scale");
        assertTrue(box.dock.w >= StudioLayout.DOCK_MIN_W,
                "a dock narrower than " + StudioLayout.DOCK_MIN_W + " is not a preview");
        assertTrue(box.hasPreview(), "the face must be drawn, not skipped");
        assertFalse(box.railLabels,
                "at this width the rail has to collapse to icons for the preview to fit");
    }

    @Test
    void railKeepsItsLabelsWhereverBothFit() {
        final StudioLayout roomy = StudioLayout.of(1280, 720);
        assertTrue(roomy.railLabels, "a 1440p window has room for labels and a preview");
        assertTrue(roomy.hasDock(), "and for the dock at its full width");

        final StudioLayout small = StudioLayout.of(420, 300);
        assertFalse(small.hasDock(), "there is no preview to buy at this size");
        assertTrue(small.railLabels, "so the rail should not have given its labels up");
    }

    @Test
    void previewKeepsTheAssemblyAspect() {
        final StudioLayout box = StudioLayout.of(960, 540);
        final Rect frame = box.bezelFrame();

        final Rect wide = StudioLayout.fitAspect(frame, 5.0f / 1.0f);
        final Rect tall = StudioLayout.fitAspect(frame, 1.0f / 3.0f);
        final Rect square = StudioLayout.fitAspect(frame, 1.0f);

        assertTrue(wide.w > wide.h * 4.0f, "a 5 x 1 wall should preview wide");
        assertTrue(tall.h > tall.w * 2.0f, "a 1 x 3 column should preview tall");
        assertTrue(Math.abs(square.w - square.h) < 0.5f, "a 1 x 1 panel should preview square");

        for (final Rect r : new Rect[]{wide, tall, square}) {
            assertTrue(r.w <= frame.w + 0.5f && r.h <= frame.h + 0.5f,
                    "the bezel escaped its frame: " + r + " in " + frame);
            assertFalse(r.isEmpty(), "a fitted bezel must never be empty");
        }
    }

    @Test
    void thezonesAddUpAtEveryWidth() {
        for (int w = 1600; w >= 360; w -= 2) {
            final StudioLayout box = StudioLayout.of(w, 540);
            final String at = "at width " + w;

            assertTrue(box.dock.w == 0.0f || box.dock.w >= StudioLayout.DOCK_MIN_W - 0.001f,
                    "a sliver of a dock was drawn " + at + ": " + box.dock.w);
            assertTrue(!box.hasDock() || box.work.w >= StudioLayout.MIN_WORK_W - 0.001f,
                    "the dock squeezed the work area to " + box.work.w + " " + at);
            assertTrue(box.rail.w + box.work.w + box.dock.w <= box.root.w + 0.5f,
                    "rail, work and dock overflowed the studio " + at);
            assertTrue(Math.abs(box.wideWork.w - (box.work.w + box.dock.w)) < 0.5f,
                    "giving the dock up must hand its width to the work area " + at);
            assertTrue(box.dock.isEmpty() || box.dock.x >= box.work.right() - 0.5f,
                    "the dock overlapped the work area " + at);
        }
    }

    @Test
    void railRowsStayInsideTheCard() {
        for (final Object[] size : SIZES) {
            final StudioLayout box = StudioLayout.of((int) size[1], (int) size[2]);
            final int capacity = box.railCapacity();
            assertTrue(capacity >= 1, size[0] + ": no room for even one rail row");
            assertTrue(box.railItem(capacity - 1).bottom() <= box.rail.bottom() + 0.5f,
                    size[0] + ": rail row " + (capacity - 1) + " runs past the card");
        }
    }
}
