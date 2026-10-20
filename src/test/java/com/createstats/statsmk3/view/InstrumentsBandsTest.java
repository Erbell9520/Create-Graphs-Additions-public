package com.createstats.statsmk3.view;

import com.createstats.statsmk3.ui.Rect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InstrumentsBandsTest {
    private static final Object[][] SIZES = {
            {"1080p @ scale 2", 960, 540},
            {"1080p @ scale 3 (the default)", 640, 360},
            {"1440p @ scale 3", 853, 480},
            {"1440p @ scale 2", 1280, 720},
            {"1366x768 @ scale 2", 683, 384},
            {"1600x900 @ scale 2", 800, 450},
    };

    @Test
    void oneWholeCatalogRowFitsAtEveryCommonSize() {
        for (final Object[] size : SIZES) {
            final Rect area = viewArea((int) size[1], (int) size[2]);
            final InstrumentsView.Bands bands = InstrumentsView.bandsFor(area);

            assertTrue(bands.gallery().h >= bands.cardHeight(),
                    size[0] + ": the catalog is " + bands.gallery().h
                            + " tall and one card is " + bands.cardHeight());
        }
    }

    @Test
    void bandsStayInOrderAndInsideTheView() {
        for (int w = 360; w <= 1600; w += 20) {
            for (int h = 240; h <= 900; h += 20) {
                final Rect area = viewArea(w, h);
                if (area.isEmpty()) {
                    continue;
                }
                final InstrumentsView.Bands bands = InstrumentsView.bandsFor(area);
                final String at = "at " + w + " x " + h;

                assertTrue(bands.rules().y >= area.y - 0.5f, "rules above the view " + at);
                assertTrue(bands.gallery().y >= bands.rules().bottom() - 0.5f,
                        "the catalog overlaps the rules " + at);
                assertTrue(bands.actions().y >= bands.gallery().bottom() - 0.5f,
                        "the actions overlap the catalog " + at);
                assertTrue(bands.actions().bottom() <= area.bottom() + 0.5f,
                        "the actions run past the view " + at);
                assertTrue(bands.rules().h > 0.0f, "the rules block vanished " + at);
            }
        }
    }

    @Test
    void livePreviewsSurviveWhereThereIsRoom() {
        assertTrue(!InstrumentsView.bandsFor(viewArea(1280, 720)).tight(),
                "a 1440p window has room for the rule previews");
        assertTrue(InstrumentsView.bandsFor(viewArea(640, 360)).tight(),
                "the default GUI scale has to give the previews up for the catalog");
    }

    private static Rect viewArea(int width, int height) {
        return StudioLayout.of(width, height).viewArea(true);
    }
}
