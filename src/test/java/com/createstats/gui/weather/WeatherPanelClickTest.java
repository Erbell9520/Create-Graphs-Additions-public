package com.createstats.gui.weather;

import com.createstats.gui.WeatherPanelScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherPanelClickTest {
    private static final int LEFT = 100;
    private static final int TOP = 50;

    private static final int CHIP_W = 46;
    private static final int CHIP_H = 12;
    private static final int CLOSE_W = 11;
    private static final int CHIP_X = LEFT + WeatherTheme.PAGE_W - WeatherTheme.PAD - CLOSE_W - 6 - CHIP_W;
    private static final int CHIP_Y = TOP + WeatherTheme.HEADER_Y + 2;
    private static final int CLOSE_X = LEFT + WeatherTheme.PAGE_W - WeatherTheme.PAD - CLOSE_W;

    @Test
    void theChipIsHitAcrossItsWholeBox() {
        assertTrue(WeatherPanelScreen.hitsUnitChip(CHIP_X, CHIP_Y, LEFT, TOP), "top left corner");
        assertTrue(WeatherPanelScreen.hitsUnitChip(CHIP_X + CHIP_W - 1, CHIP_Y + CHIP_H - 1, LEFT, TOP),
                "bottom right corner");
        assertTrue(WeatherPanelScreen.hitsUnitChip(CHIP_X + CHIP_W / 2, CHIP_Y + CHIP_H / 2, LEFT, TOP),
                "centre");
    }

    @Test
    void theChipStopsAtItsOwnEdges() {
        assertFalse(WeatherPanelScreen.hitsUnitChip(CHIP_X - 1, CHIP_Y + 5, LEFT, TOP), "one left");
        assertFalse(WeatherPanelScreen.hitsUnitChip(CHIP_X + CHIP_W, CHIP_Y + 5, LEFT, TOP), "one right");
        assertFalse(WeatherPanelScreen.hitsUnitChip(CHIP_X + 5, CHIP_Y - 1, LEFT, TOP), "one above");
        assertFalse(WeatherPanelScreen.hitsUnitChip(CHIP_X + 5, CHIP_Y + CHIP_H, LEFT, TOP), "one below");
    }

    @Test
    void thePageAroundItIsNotTheChip() {
        assertFalse(WeatherPanelScreen.hitsUnitChip(LEFT + 20, CHIP_Y + 5, LEFT, TOP),
                "the place name is not a button");
        assertFalse(WeatherPanelScreen.hitsUnitChip(LEFT + 160, TOP + WeatherTheme.HERO_Y + 40, LEFT, TOP),
                "the sky card is not a button");
    }

    @Test
    void theChipAndTheCloseButtonDoNotOverlap() {
        assertFalse(WeatherPanelScreen.hitsUnitChip(CLOSE_X + 5, CHIP_Y + 5, LEFT, TOP),
                "a click on close must not toggle the units");
        assertFalse(WeatherPanelScreen.hitsClose(CHIP_X + CHIP_W / 2, CHIP_Y + 5, LEFT, TOP),
                "a click on the chip must not shut the screen");
        assertTrue(WeatherPanelScreen.hitsClose(CLOSE_X + 5, CHIP_Y + 5, LEFT, TOP));
    }

    @Test
    void bothControlsStayOnThePage() {
        for (int left : new int[]{0, 100, 617}) {
            for (int top : new int[]{0, 50, 233}) {
                assertTrue(WeatherPanelScreen.hitsUnitChip(
                        left + WeatherTheme.PAGE_W - WeatherTheme.PAD - CLOSE_W - 6 - CHIP_W / 2,
                        top + WeatherTheme.HEADER_Y + 6, left, top),
                        "chip missing at " + left + "," + top);
            }
        }
    }
}
