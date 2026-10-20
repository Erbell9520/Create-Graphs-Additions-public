package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveStudioLayoutTest {
    static final int[][] SIZES = {{497, 247}, {585, 349}, {683, 401}, {1024, 555}, {1040, 680}, {900, 560}, {700, 480},
            {560, 400}};

    static float tabsNeed() {
        float longest = 0.0f;
        for (final String t : DriveArt.TABS) {
            longest = Math.max(longest, CountingSurface.advance(t, Text.META));
        }
        return 6.0f + DriveArt.TABS.length * (longest + 16.0f);
    }

    @Test
    void nothingOverlapsOrLeavesThePageAtAnyStudioSize() {
        final float need = tabsNeed();
        final List<String> failures = new ArrayList<>();
        for (final int[] wh : SIZES) {
            final Rect area = new Rect(10.0f, 20.0f, wh[0], wh[1]);
            for (int commands = 0; commands <= 3; commands++) {
                final DriveArt.StudioLayout l = DriveArt.studioLayout(area, need, commands);
                final String at = String.format(Locale.ROOT, "%dx%d, %d command(s)", wh[0], wh[1], commands);
                final List<Rect> widgets = new ArrayList<>(List.of(l.tabs(), l.look(), l.face()));
                if (commands > 0) {
                    widgets.add(l.commands());
                }
                for (final Rect r : widgets) {
                    if (!inside(area, r)) {
                        failures.add(at + ": " + r + " leaves the page");
                    }
                }
                for (int i = 0; i < widgets.size(); i++) {
                    for (int j = i + 1; j < widgets.size(); j++) {
                        if (!widgets.get(i).intersect(widgets.get(j)).isEmpty()) {
                            failures.add(at + ": " + widgets.get(i) + " overlaps " + widgets.get(j));
                        }
                    }
                }
                if (l.tabs().w + 0.5f < need) {
                    failures.add(at + ": the tab bar is " + l.tabs().w + " wide, its labels need " + need);
                }
                for (final Rect r : widgets) {
                    if (!l.content().intersect(r).isEmpty()) {
                        failures.add(at + ": a widget sits over the content, " + r);
                    }
                }
                if (l.headerReserved() > 0.0f) {
                    if (l.look().bottom() > area.y + DriveArt.HEADER_H || l.face().bottom() > area.y + DriveArt.HEADER_H) {
                        failures.add(at + ": the buttons moved up but do not fit the header");
                    }
                    if (area.w - l.headerReserved() < 150.0f) {
                        failures.add(at + ": the header keeps " + (area.w - l.headerReserved()) + " for the name");
                    }
                }
                if (l.content().h < 120.0f) {
                    failures.add(at + ": " + l.content().h + " left for the tab itself");
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " layout faults:\n" + String.join("\n", failures));
    }

    @Test
    void theButtonsShareTheTabRowWhenThereIsRoom() {
        final DriveArt.StudioLayout wide = DriveArt.studioLayout(new Rect(0.0f, 0.0f, 1040.0f, 680.0f), tabsNeed(), 0);
        assertTrue(wide.headerReserved() == 0.0f, "on a wide studio the header keeps its whole width");
        assertTrue(wide.look().y >= wide.tabs().y, "and the buttons sit on the tab row");
        final DriveArt.StudioLayout narrow = DriveArt.studioLayout(new Rect(0.0f, 0.0f, 497.0f, 247.0f), tabsNeed(), 0);
        assertTrue(narrow.headerReserved() > 0.0f, "at 497 wide the buttons move up beside the name");
    }

    private static boolean inside(Rect area, Rect r) {
        return r.x >= area.x - 0.01f && r.y >= area.y - 0.01f && r.right() <= area.right() + 0.01f
                && r.bottom() <= area.bottom() + 0.01f;
    }
}
