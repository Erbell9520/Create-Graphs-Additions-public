package com.createstats.statsmk3.page;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainPageLayoutTest {
    private static final float[][] SIZES = {
            {496, 246}, {430, 220}, {640, 320}, {780, 400}, {900, 500}, {1180, 620},

            {1400, 760}, {1600, 900}};

    @Test
    void everyPaneStaysInsideThePanel() {
        for (final float[] size : SIZES) {
            final Rect area = new Rect(0, 0, size[0], size[1]);
            final TrainPageLayout.Frame f = TrainPageLayout.of(area);

            check(area, f.kpi(), "kpi", size);
            check(area, f.control(), "control", size);
            check(area, f.modes(), "modes", size);
            check(area, f.content(), "content", size);
            check(area, f.list(), "list", size);
            if (!f.detail().isEmpty()) {
                check(area, f.detail(), "detail", size);
            }
        }
    }

    @Test
    void theRowsStackWithoutOverlapping() {
        for (final float[] size : SIZES) {
            final TrainPageLayout.Frame f = TrainPageLayout.of(new Rect(0, 0, size[0], size[1]));
            assertTrue(f.kpi().bottom() <= f.control().y + 0.01f,
                    size[0] + "x" + size[1] + ": the summary runs into the controls");
            assertTrue(f.control().bottom() <= f.content().y + 0.01f,
                    size[0] + "x" + size[1] + ": the controls run into the content");
            assertTrue(f.content().h > 40.0f,
                    size[0] + "x" + size[1] + ": nothing left for the content");
        }
    }

    @Test
    void narrowPanelsDropToASinglePane() {
        final TrainPageLayout.Frame narrow = TrainPageLayout.of(new Rect(0, 0, 430, 220));
        assertTrue(narrow.singlePane(), "a 430 wide panel cannot hold two useful columns");
        assertEquals(narrow.content().w, narrow.list().w, 0.01f,
                "with one pane the list takes the whole width");

        final TrainPageLayout.Frame wide = TrainPageLayout.of(new Rect(0, 0, 900, 500));
        assertFalse(wide.singlePane());
        assertTrue(wide.list().w >= 190.0f, "a list narrower than this cannot hold a station name");
        assertTrue(wide.detail().w >= wide.list().w,
                "the detail is the thing being read and should not be the narrower half");
        assertTrue(wide.list().right() <= wide.detail().x + 0.01f, "the panes must not overlap");
    }

    @Test
    void theInspectorAppearsOnlyWhenTheMapCanAffordIt() {
        final TrainPageLayout.Frame narrowFrame = TrainPageLayout.of(new Rect(0, 0, 640, 320));
        final Rect narrow = narrowFrame.content();
        assertFalse(TrainPageLayout.hasInspector(narrow));
        assertTrue(TrainPageLayout.inspectorArea(narrow, narrowFrame.density()).isEmpty());
        assertEquals(narrow.w, TrainPageLayout.mapArea(narrow, narrowFrame.density()).w, 0.01f,
                "with no inspector the map takes the whole content");

        final TrainPageLayout.Frame wideFrame = TrainPageLayout.of(new Rect(0, 0, 1180, 620));
        final Rect wide = wideFrame.content();
        assertTrue(TrainPageLayout.hasInspector(wide));
        final Rect map = TrainPageLayout.mapArea(wide, wideFrame.density());
        final Rect inspector = TrainPageLayout.inspectorArea(wide, wideFrame.density());
        assertTrue(map.right() <= inspector.x + 0.01f, "the inspector must not sit on the map");
        assertTrue(inspector.right() <= wide.right() + 0.01f);
        assertTrue(map.w > inspector.w * 2.0f, "the map is the point and must keep most of it");
    }

    @Test
    void theSummaryShedsTilesRatherThanShrinkingThem() {
        assertEquals(3, TrainPageLayout.kpiColumns(new Rect(0, 0, 400, 46)));
        assertEquals(4, TrainPageLayout.kpiColumns(new Rect(0, 0, 500, 46)));
        assertEquals(5, TrainPageLayout.kpiColumns(new Rect(0, 0, 700, 46)));

        for (final float[] size : SIZES) {
            final Rect kpi = TrainPageLayout.of(new Rect(0, 0, size[0], size[1])).kpi();
            final int cells = TrainPageLayout.kpiColumns(kpi);
            final float per = (kpi.w - 8.0f * (cells - 1)) / cells;
            assertTrue(per >= 96.0f,
                    size[0] + "x" + size[1] + ": " + cells + " tiles of " + per + "px each");
        }
    }

    @Test
    void shortPanelsUseTheCompactSummary() {
        assertEquals(TrainPageLayout.KPI_H_TIGHT,
                TrainPageLayout.of(new Rect(0, 0, 900, 260)).kpi().h, 0.01f);
        assertEquals(TrainPageLayout.density(new Rect(0, 0, 900, 500)).kpiH(),
                TrainPageLayout.of(new Rect(0, 0, 900, 500)).kpi().h, 0.01f);
    }

    @Test
    void aLargePanelGetsALargerStep() {
        final TrainPageLayout.Density small = TrainPageLayout.density(new Rect(0, 0, 640, 320));
        final TrainPageLayout.Density roomy = TrainPageLayout.density(new Rect(0, 0, 1000, 520));
        final TrainPageLayout.Density wide = TrainPageLayout.density(new Rect(0, 0, 1600, 900));

        assertEquals(1.0f, small.step(), 0.001f, "the compact step is the reference");
        assertTrue(roomy.step() > small.step());
        assertTrue(wide.step() > roomy.step());
        for (final TrainPageLayout.Density d : new TrainPageLayout.Density[]{small, roomy, wide}) {
            assertTrue(d.secondary() < d.primary(),
                    "what qualifies a row must stay smaller than what the row is about");
            assertTrue(d.rowH() > d.listRowH(), "a board row carries two lines and a list row one");
        }
        assertTrue(wide.inspectorW() > small.inspectorW(),
                "the services column is where the labels were truncating");

        assertTrue(TrainPageLayout.hasInspector(
                TrainPageLayout.of(new Rect(0, 0, 1600, 900)).content()));
    }

    @Test
    void theControlRowHoldsItsTabsAndItsChips() {
        for (final float[] size : SIZES) {
            final Rect area = new Rect(0, 0, size[0], size[1]);
            final TrainPageLayout.Frame f = TrainPageLayout.of(area);
            final float modes = TrainPageLayout.modesWidth(f.control(), f.density());
            assertEquals(f.modes().w, modes, 0.01f,
                    size[0] + "x" + size[1] + ": the frame and the helper disagree");
            assertTrue(modes <= f.control().w + 0.01f,
                    size[0] + "x" + size[1] + ": the tabs run past the control row");
            assertTrue(modes >= 200.0f,
                    size[0] + "x" + size[1] + ": five tabs need room, got " + modes);
        }
    }

    @Test
    void slideOversStayOnScreen() {
        for (final float[] size : SIZES) {
            final Rect area = new Rect(0, 0, size[0], size[1]);
            final TrainPageLayout.Frame f = TrainPageLayout.of(area);
            for (final float want : new float[]{250.0f, 300.0f, 340.0f}) {
                final Rect panel = TrainPageLayout.overlay(area, f.control(), f.content(), want,
                        340.0f);
                check(area, panel, "overlay " + want, size);
                assertTrue(panel.w >= 80.0f, "an overlay narrower than this holds nothing");
                assertTrue(panel.h >= 60.0f);
            }
        }
    }

    @Test
    void theModeControlLeavesRoomForTheHeaderChips() {
        for (final float[] size : SIZES) {
            final TrainPageLayout.Frame f = TrainPageLayout.of(new Rect(0, 0, size[0], size[1]));
            final float spare = f.control().right() - f.modes().right();
            assertTrue(spare >= 0.0f,
                    size[0] + "x" + size[1] + ": the mode control overruns its row");
            if (size[0] >= 496.0f) {
                assertTrue(spare >= 120.0f, size[0] + "x" + size[1]
                        + ": only " + spare + "px left for the Filters and View chips");
            }
        }
    }

    @Test
    void everyModeLabelFitsItsSegment() {
        final RasterSurface surface = new RasterSurface(64, 16);
        for (final float[] size : SIZES) {
            final TrainPageLayout.Frame f = TrainPageLayout.of(new Rect(0, 0, size[0], size[1]));
            final float segment = TrainPageLayout.modeSegmentWidth(f.modes());
            final float scale = f.density().secondary();
            for (final String label : TrainPageLayout.MODES) {
                final float w = surface.textWidth(label, scale);
                assertTrue(w <= segment - 4.0f, size[0] + "x" + size[1] + ": \"" + label
                        + "\" is " + w + "px at scale " + scale + " in a " + segment
                        + "px segment");
            }
        }
    }

    private static void check(Rect area, Rect part, String name, float[] size) {
        final String at = size[0] + "x" + size[1] + " " + name;
        assertTrue(part.x >= area.x - 0.01f, at + " starts left of the panel");
        assertTrue(part.y >= area.y - 0.01f, at + " starts above the panel");
        assertTrue(part.right() <= area.right() + 0.01f, at + " runs off the right");
        assertTrue(part.bottom() <= area.bottom() + 0.01f, at + " runs off the bottom");
        assertFalse(part.isEmpty(), at + " collapsed to nothing");
    }
}
