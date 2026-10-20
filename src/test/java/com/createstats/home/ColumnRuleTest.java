package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColumnRuleTest {
    private static RoomFill.Cell at(double feetTop, boolean roofed) {
        return ColumnRule.classify(true, false, feetTop, true, roofed);
    }

    @Test
    @DisplayName("a floor-length glass pane is a wall, so a window is not a hole in the house")
    void paneIsAWall() {
        assertEquals(RoomFill.Cell.WALL, at(1.0, true));

        assertEquals(RoomFill.Cell.WALL, at(1.5, true));
    }

    @Test
    @DisplayName("what a person steps onto or over is floor")
    void lowThingsAreFloor() {
        assertEquals(RoomFill.Cell.FLOOR, at(0.0, true), "nothing");
        assertEquals(RoomFill.Cell.FLOOR, at(0.0625, true), "a carpet");
        assertEquals(RoomFill.Cell.FLOOR, at(0.5, true), "a bottom slab");
    }

    @Test
    @DisplayName("furniture taller than half a block is walked round, not through")
    void furnitureIsAnObstacle() {
        assertEquals(RoomFill.Cell.WALL, at(0.875, true), "a chest");
        assertEquals(RoomFill.Cell.WALL, at(0.5625, true), "a bed");
    }

    @Test
    @DisplayName("doors, gates and trapdoors are the line between rooms, whatever their shape")
    void doorsAreBoundaries() {
        assertEquals(RoomFill.Cell.DOOR, ColumnRule.classify(true, true, 0.0, true, true));
        assertEquals(RoomFill.Cell.DOOR, ColumnRule.classify(true, true, 1.0, true, false));
    }

    @Test
    @DisplayName("a hole in the floor is the room's edge; open sky is outside; unloaded is never a room")
    void edges() {
        assertEquals(RoomFill.Cell.WALL, ColumnRule.classify(true, false, 0.0, false, true), "a stairwell");
        assertEquals(RoomFill.Cell.OPEN, at(0.0, false), "the garden");
        assertEquals(RoomFill.Cell.OPEN, ColumnRule.classify(false, false, 0.0, true, true), "unloaded");
    }

    @Test
    @DisplayName("a roof is anything that blocks motion at head height or above: glass counts")
    void roofFromTheHeightMap() {
        assertTrue(ColumnRule.roofed(66, 68), "a ceiling a block over the head");
        assertTrue(ColumnRule.roofed(66, 67), "a beam at head height");
        assertFalse(ColumnRule.roofed(66, 66), "the highest solid block is at the feet: sky above the head");
        assertFalse(ColumnRule.roofed(66, 65), "open ground");
    }
}
