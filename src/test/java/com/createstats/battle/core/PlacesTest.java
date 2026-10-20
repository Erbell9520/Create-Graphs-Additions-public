package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacesTest {
    private static int p(int x, int y, int z) {
        return PackedPos.pack(x, y, z);
    }

    @Test
    void aLossWithinTwoJoinsAndFartherOpensANewPlace() {
        Places places = new Places();
        Places.Place a = places.add(p(0, 0, 0), Places.KIND_DAMAGE);
        assertEquals(1, a.id());
        assertEquals(1, places.add(p(2, 1, -2), Places.KIND_DAMAGE).id(), "Chebyshev 2 joins");
        assertEquals(2, places.add(p(10, 0, 0), Places.KIND_DAMAGE).id(), "farther opens R2");
        assertEquals(2, places.count());
    }

    @Test
    void aLossTouchingTwoMergesThemIntoTheLowerNumber() {
        Places places = new Places();
        places.add(p(0, 0, 0), Places.KIND_DAMAGE);
        places.add(p(4, 0, 0), Places.KIND_DAMAGE);
        assertEquals(2, places.count(), "4 apart: two places");
        Places.Place merged = places.add(p(2, 0, 0), Places.KIND_DAMAGE);
        assertEquals(1, merged.id());
        assertEquals(3, merged.size());
        assertEquals(1, places.count());
        assertEquals(1, places.placeOf(p(4, 0, 0)).id());
    }

    @Test
    void numbersAreNotReusedWhileAnyPlaceIsOpenAndRestartWhenAllClose() {
        Places places = new Places();
        places.add(p(0, 0, 0), Places.KIND_DAMAGE);
        places.add(p(20, 0, 0), Places.KIND_DAMAGE);
        places.remove(p(0, 0, 0));
        assertEquals(3, places.add(p(40, 0, 0), Places.KIND_DAMAGE).id(), "R1 closed but R2 is open: no reuse");
        places.remove(p(20, 0, 0));
        places.accept(3);
        assertEquals(0, places.count());
        assertEquals(1, places.add(p(0, 5, 0), Places.KIND_DAMAGE).id(), "the list emptied: back to R1");
    }

    @Test
    void theCapMergesTheSmallestIntoItsNearest() {
        Places places = new Places();
        for (int i = 0; i < Places.MAX_OPEN; i++) {
            places.add(p(i * 10, 0, 0), Places.KIND_DAMAGE);
            places.add(p(i * 10, 1, 0), Places.KIND_DAMAGE);
        }
        assertEquals(Places.MAX_OPEN, places.count());
        places.add(p(-20, 0, 0), Places.KIND_DAMAGE);
        assertEquals(Places.MAX_OPEN, places.count(), "never more than 32");
        assertEquals(1, places.placeOf(p(-20, 0, 0)).id(), "the new single block joined its nearest, R1");
    }

    @Test
    void kindsNeverMerge() {
        Places places = new Places();
        places.add(p(0, 0, 0), Places.KIND_BROKE_AWAY);
        Places.Place hole = places.add(p(1, 0, 0), Places.KIND_DAMAGE);
        assertNotEquals(1, hole.id());
        assertEquals(2, places.count());
    }

    @Test
    void namedOnOpeningAndRenamedOnlyOnMerging() {
        Places places = new Places();
        int[] calls = {0};
        Places.Namer namer = (cells, kind) -> "N" + (++calls[0]) + ":" + cells.length;
        places.add(p(0, 0, 0), Places.KIND_DAMAGE);
        places.nameDirty(namer);
        assertEquals("N1:1", places.byId(1).name());
        places.add(p(1, 0, 0), Places.KIND_DAMAGE);
        places.nameDirty(namer);
        assertEquals("N1:1", places.byId(1).name(), "a block joining does not rename");
        places.add(p(5, 0, 0), Places.KIND_DAMAGE);
        assertEquals(2, places.count());
        places.add(p(3, 0, 0), Places.KIND_DAMAGE);
        places.nameDirty(namer);
        assertTrue(places.byId(1).name().startsWith("N"));
        assertEquals(4, places.byId(1).size());
        assertNull(places.byId(2), "R2 merged into R1");
    }

    @Test
    void gunAndPropulsionPlacesComeFirstThenTheLargest() {
        Places places = new Places();
        places.add(p(0, 0, 0), Places.KIND_DAMAGE);
        places.add(p(1, 0, 0), Places.KIND_DAMAGE);
        places.add(p(2, 0, 0), Places.KIND_DAMAGE);
        places.add(p(20, 0, 0), Places.KIND_DAMAGE);
        places.add(p(40, 0, 0), Places.KIND_DAMAGE);
        places.add(p(41, 0, 0), Places.KIND_DAMAGE);
        int gunPart = p(20, 0, 0);
        List<Places.Place> order = places.ordered(c -> c == gunPart);
        assertEquals(2, order.get(0).id(), "the gun's place first although it is the smallest");
        assertEquals(1, order.get(1).id());
        assertEquals(3, order.get(2).id());
    }
}
