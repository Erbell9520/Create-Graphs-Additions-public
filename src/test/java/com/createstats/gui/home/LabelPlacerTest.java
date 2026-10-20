package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelPlacerTest {
    private static Set<Long> room(int x0, int z0, int x1, int z1) {
        final Set<Long> cells = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                cells.add(PlanPainter.pack(x, z));
            }
        }
        return cells;
    }

    private static LabelPlacer.Spot place(Set<Long> cells, long centre, Set<Long> marked, boolean clear) {
        return LabelPlacer.place(cells, centre, marked, c -> clear, c -> false, Set.of(), 40, 8);
    }

    @Test
    @DisplayName("an empty room: the label sits on the middle row, centred")
    void centredOnAnEmptyRoom() {
        final Set<Long> cells = room(2, 2, 9, 7);
        final LabelPlacer.Spot s = place(cells, PlanPainter.pack(5, 4), Set.of(), true);
        assertNotNull(s);
        assertEquals(4, s.z());

        assertTrue(s.x() == 2 || s.x() == 3, "span starts at " + s.x());
        assertEquals((6 * 8 - 40) / 2, s.inset());
    }

    @Test
    @DisplayName("a strip lamp across the middle row pushes the label to the next row, never over the lamp")
    void neverOverAFixture() {
        final Set<Long> cells = room(2, 2, 9, 7);
        final Set<Long> marked = new HashSet<>();
        for (int x = 3; x <= 8; x++) {
            marked.add(PlanPainter.pack(x, 4));
        }
        final LabelPlacer.Spot s = place(cells, PlanPainter.pack(5, 4), marked, true);
        assertNotNull(s);
        assertTrue(s.z() == 3 || s.z() == 5, "the row beside the lamp's, not the lamp's: " + s.z());
        for (int x = s.x(); x < s.x() + 6; x++) {
            assertTrue(!marked.contains(PlanPainter.pack(x, s.z())));
        }
    }

    @Test
    @DisplayName("furniture costs, fixtures forbid: a row of chests is taken over no row at all")
    void furnitureIsOnlyACost() {
        final Set<Long> cells = room(2, 2, 9, 7);
        final Set<Long> marked = new HashSet<>();
        for (int z = 2; z <= 7; z++) {
            if (z != 6) {
                for (int x = 2; x <= 9; x++) {
                    marked.add(PlanPainter.pack(x, z));
                }
            }
        }

        final LabelPlacer.Spot s = place(cells, PlanPainter.pack(5, 4), marked, false);
        assertNotNull(s);
        assertEquals(6, s.z());
    }

    @Test
    @DisplayName("a room narrower than the name, or with a fixture on every row, gets no label")
    void noCleanSpotNoLabel() {
        assertNull(place(room(2, 2, 4, 7), PlanPainter.pack(3, 4), Set.of(), true),
                "three columns cannot hold six");
        final Set<Long> cells = room(2, 2, 9, 7);
        final Set<Long> marked = new HashSet<>();
        for (int z = 2; z <= 7; z++) {
            marked.add(PlanPainter.pack(5, z));
        }
        assertNull(place(cells, PlanPainter.pack(5, 4), marked, true),
                "a fixture in the middle of every row leaves no six-column span");
    }

    @Test
    @DisplayName("a room narrower than its name keeps the label, its ends over the room's own walls, never the next room")
    void endsOverTheWalls() {
        final Set<Long> laundry = room(11, 2, 14, 7);
        final Set<Long> walls = new HashSet<>();
        for (int z = 1; z <= 8; z++) {
            walls.add(PlanPainter.pack(10, z));
            walls.add(PlanPainter.pack(15, z));
        }
        final LabelPlacer.Spot s = LabelPlacer.place(laundry, PlanPainter.pack(12, 4), Set.of(), c -> true,
                walls::contains, Set.of(), 40, 8);
        assertNotNull(s, "six columns over a four-column room, the ends over its walls");
        assertTrue(s.x() >= 10 && s.x() + 6 - 1 <= 15, "within the walls either side: starts at " + s.x());

        assertNull(LabelPlacer.place(laundry, PlanPainter.pack(12, 4), Set.of(), c -> true, c -> false, Set.of(), 40, 8));

        assertNull(LabelPlacer.place(room(12, 2, 13, 7), PlanPainter.pack(12, 4), Set.of(), c -> true, c -> true, Set.of(), 40, 8));
    }

    @Test
    @DisplayName("the room's own floor beats its wall: a span over chests wins over one with an end on the wall")
    void floorBeatsWall() {
        final Set<Long> cells = room(10, 2, 14, 7);
        final Set<Long> walls = new HashSet<>();
        for (int z = 1; z <= 8; z++) {
            walls.add(PlanPainter.pack(9, z));
            walls.add(PlanPainter.pack(15, z));
        }
        final Set<Long> chests = Set.of(PlanPainter.pack(10, 4), PlanPainter.pack(11, 4));

        final LabelPlacer.Spot s = LabelPlacer.place(cells, PlanPainter.pack(12, 4), Set.of(), c -> !chests.contains(c),
                walls::contains, Set.of(), 30, 8);
        assertNotNull(s);
        assertTrue(s.x() >= 10 && s.x() + 4 - 1 <= 14, "on the room's own floor: " + s.x());
    }

    @Test
    @DisplayName("a second label keeps clear of the first: never the columns under it, nor the ones beside it")
    void labelsKeepTheirDistance() {
        final Set<Long> cells = room(2, 2, 13, 7);
        final Set<Long> taken = new HashSet<>();
        final LabelPlacer.Spot first = LabelPlacer.place(cells, PlanPainter.pack(4, 4), Set.of(), c -> true, c -> false,
                taken, 24, 8);
        assertNotNull(first);
        LabelPlacer.take(taken, first, 24, 8);
        final LabelPlacer.Spot second = LabelPlacer.place(cells, PlanPainter.pack(7, 4), Set.of(), c -> true, c -> false,
                taken, 24, 8);
        assertNotNull(second);
        final int firstEnd = first.x() + 3;
        assertTrue(second.z() != first.z() || second.x() > firstEnd + 1 || second.x() + 3 - 1 < first.x() - 1,
                "a column of air between them on a shared row: " + first + " then " + second);
    }

    @Test
    @DisplayName("on a row with a fixture at one end, the label moves aside by the least")
    void movesAsideByTheLeast() {
        final Set<Long> cells = room(2, 2, 11, 7);
        final Set<Long> marked = Set.of(PlanPainter.pack(2, 4), PlanPainter.pack(3, 4));
        final LabelPlacer.Spot s = place(cells, PlanPainter.pack(6, 4), marked, true);
        assertNotNull(s);
        assertEquals(4, s.z(), "the middle row still, there is room on it");
        assertTrue(s.x() >= 4, "clear of the lamp at 2 and 3");
        assertTrue(s.x() <= 5, "and no further than it must");
    }
}
