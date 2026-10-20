package com.createstats.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomFillTest {
    private static RoomFill.Grid grid(String... rows) {
        return (x, z) -> {
            if (z < 0 || z >= rows.length || x < 0 || x >= rows[z].length()) {
                return RoomFill.Cell.OPEN;
            }
            return switch (rows[z].charAt(x)) {
                case '.' -> RoomFill.Cell.FLOOR;
                case '#' -> RoomFill.Cell.WALL;
                case 'D' -> RoomFill.Cell.DOOR;
                default -> RoomFill.Cell.OPEN;
            };
        };
    }

    @Test
    @DisplayName("a walled room fills to its walls and no further")
    void walledRoom() {
        final RoomFill.Result r = RoomFill.fill(grid(
                "#######",
                "#.....#",
                "#.....#",
                "#######"), 2, 1);
        assertTrue(r.isRoom());
        assertEquals(10, r.cells().size());
        assertTrue(r.cells().contains(RoomFill.pack(5, 2)));
    }

    @Test
    @DisplayName("a door is the line between two rooms: the fill stops at it")
    void doorIsABoundary() {
        final RoomFill.Grid g = grid(
                "#########",
                "#...D...#",
                "#...#...#",
                "#########");
        final RoomFill.Result left = RoomFill.fill(g, 1, 1);
        final RoomFill.Result right = RoomFill.fill(g, 6, 2);
        assertTrue(left.isRoom());
        assertEquals(6, left.cells().size());
        assertEquals(6, right.cells().size());
        assertTrue(!left.cells().contains(RoomFill.pack(4, 1)), "the door belongs to neither room");
    }

    @Test
    @DisplayName("a missing wall reaches the sky: that is not a room, and the garden is never claimed")
    void escapedIsRefused() {
        final RoomFill.Result r = RoomFill.fill(grid(
                "######",
                "#....#",
                "#.....",
                "#.....",
                "#.....",
                "#.....",
                "######"), 1, 1);
        assertEquals(RoomFill.Refusal.ESCAPED, r.refusal(), "four open columns is a wall gone, not a doorway");
        assertTrue(r.cells().isEmpty());
    }

    @Test
    @DisplayName("an open doorway with no door is the room's edge, like a door: the room is claimed, the garden is not")
    void openDoorwayIsAnEdge() {
        final RoomFill.Result hall = RoomFill.fill(grid(
                "#####",
                "#....",
                "#####"), 1, 1);
        assertTrue(hall.isRoom(), "a hall open at one end");
        assertEquals(4, hall.cells().size());
        final RoomFill.Result arch = RoomFill.fill(grid(
                "#######",
                "#.....#",
                "#.....#",
                "#.....#",
                "###   #"), 1, 1);
        assertTrue(arch.isRoom(), "an arch three wide onto the garden");
        assertEquals(15, arch.cells().size(), "the arch's open columns are not floor of the room");
    }

    @Test
    @DisplayName("two walls touching only at a corner do not leak: four neighbours, never eight")
    void diagonalDoesNotLeak() {
        final RoomFill.Result r = RoomFill.fill(grid(
                "####",
                "#..#",
                "#.#.",
                "##.."), 1, 1);
        assertTrue(r.isRoom(), "the gap at (3,2)-(2,3) is diagonal and must not open the room");
        assertEquals(3, r.cells().size());
    }

    @Test
    @DisplayName("clicking a wall, a door or the open sky claims nothing")
    void notFloor() {
        final RoomFill.Grid g = grid("#D.#");
        assertEquals(RoomFill.Refusal.NOT_FLOOR, RoomFill.fill(g, 0, 0).refusal());
        assertEquals(RoomFill.Refusal.NOT_FLOOR, RoomFill.fill(g, 1, 0).refusal());
        assertEquals(RoomFill.Refusal.NOT_FLOOR, RoomFill.fill(g, 9, 9).refusal());
    }

    @Test
    @DisplayName("an enclosed space bigger than the limit is refused, not truncated")
    void tooBig() {
        final RoomFill.Result r = RoomFill.fill(grid(
                "######",
                "#....#",
                "#....#",
                "######"), 1, 1, 7);
        assertEquals(RoomFill.Refusal.TOO_BIG, r.refusal());
        assertTrue(RoomFill.fill(grid(
                "######",
                "#....#",
                "#....#",
                "######"), 1, 1, 8).isRoom(), "exactly at the limit is still a room");
    }

    @Test
    @DisplayName("columns pack and unpack losslessly, negative coordinates included")
    void packing() {
        for (int x : new int[] {0, 1, -1, 30_000_000, -30_000_000}) {
            for (int z : new int[] {0, 7, -7, 29_999_999, -29_999_999}) {
                final long p = RoomFill.pack(x, z);
                assertEquals(x, RoomFill.unpackX(p));
                assertEquals(z, RoomFill.unpackZ(p));
            }
        }
    }
}
