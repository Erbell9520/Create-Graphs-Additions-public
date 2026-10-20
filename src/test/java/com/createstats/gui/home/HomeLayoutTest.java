package com.createstats.gui.home;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeLayoutTest {
    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"853,533", "640,360", "480,270", "427,240", "400,230", "1920,1080", "1280,800"})
    void fitsAndDoesNotOverlap(int sw, int sh) {
        final HomeLayout l = HomeLayout.of(sw, sh);
        assertTrue(l.x >= 0 && l.x + l.w <= sw, "frame inside the screen across: " + l.x + "+" + l.w);
        assertTrue(l.y >= 0 && l.y + l.h <= sh, "frame inside the screen down: " + l.y + "+" + l.h);
        assertEquals(l.mapSize, l.planSize + 2 * HomeLayout.PARCHMENT, "the plan sits inside the parchment");
        assertTrue(l.planSize >= 120, "a map worth clicking on: " + l.planSize);
        assertTrue(l.mapX + l.mapSize < l.sideX, "map and side pane do not overlap");
        assertTrue(l.sideX + l.sideW <= l.x + l.w - HomeLayout.PAD, "side pane inside the frame");
        assertTrue(l.sideW >= HomeLayout.SIDE_MIN, "the side pane never narrower than the hotbar needs");
        assertTrue(l.scenesX >= l.sideX && l.scenesX + HomeLayout.HOTBAR_W <= l.sideX + l.sideW,
                "hotbar inside the side pane");
        assertTrue(l.scenesY + HomeLayout.HOTBAR_H <= l.y + l.h - HomeLayout.PAD, "hotbar inside the frame");
        assertTrue(l.footY + HomeLayout.BUTTON_H <= l.y + l.h - 3, "the foot's buttons inside the frame's border");
        assertTrue(l.sideBottom - l.sideY >= 120, "the list has room for rows: " + (l.sideBottom - l.sideY));
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"853,533", "640,360"})
    void fullSizeWhereItFits(int sw, int sh) {
        final HomeLayout l = HomeLayout.of(sw, sh);
        assertEquals(HomeLayout.FULL_H, l.h);
        assertEquals(HomeLayout.FULL_H - HomeLayout.TITLE_H - HomeLayout.FOOT_H, l.mapSize);
        assertEquals(HomeLayout.SIDE_MAX, l.sideW);
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"853,533"})
    void theOwnersScreenGetsAPanelWorthReading(int sw, int sh) {
        final HomeLayout l = HomeLayout.of(sw, sh);
        assertTrue(l.w * 2 > sw, "frame width " + l.w + " of " + sw);
    }
}
