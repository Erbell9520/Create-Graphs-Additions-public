package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyStoreTest {
    private static SurveyStore hull(int n) {
        SurveyStore s = new SurveyStore();
        for (int i = 0; i < n; i++) s.add(PackedPos.pack(i, 0, 0), "minecraft:oak_planks", BattleSystem.HULL);
        return s;
    }

    @Test
    void putBackWithADifferentBlockClears() {
        SurveyStore s = hull(10);
        int slot = s.slotOf(PackedPos.pack(3, 0, 0));
        assertTrue(s.markLost(slot, 100L, SurveyStore.CAUSE_BLAST));
        assertEquals(1, s.lostCount());
        s.putBack(slot, "minecraft:cobblestone", BattleSystem.HULL);
        assertEquals(0, s.lostCount(), "a patched hole is repaired whatever block patched it");
        assertFalse(s.isLost(slot));
        assertEquals("minecraft:cobblestone", s.blockAt(slot), "the patch becomes the reference");
        assertEquals(10, s.total(BattleSystem.HULL));
        assertEquals(0, s.lost(BattleSystem.HULL));
    }

    @Test
    void acceptPlaceDropsItsPositions() {
        SurveyStore s = hull(10);
        for (int i = 0; i < 4; i++) s.markLost(s.slotOf(PackedPos.pack(i, 0, 0)), 5L, SurveyStore.CAUSE_HIT);
        for (int i = 0; i < 4; i++) assertTrue(s.removePacked(PackedPos.pack(i, 0, 0)));
        assertEquals(6, s.size());
        assertEquals(0, s.lostCount());
        assertEquals(-1, s.slotOf(PackedPos.pack(2, 0, 0)));
        assertTrue(s.slotOf(PackedPos.pack(9, 0, 0)) >= 0, "swap-remove kept the rest findable");
        assertEquals("minecraft:oak_planks", s.blockAt(s.slotOf(PackedPos.pack(9, 0, 0))));
    }

    @Test
    void acceptShapeStartsAgain() {
        SurveyStore s = hull(10);
        s.markLost(0, 5L, SurveyStore.CAUSE_HIT);
        s.clear();
        assertEquals(0, s.size());
        assertEquals(0, s.lostCount());
        assertEquals(0, s.total(BattleSystem.HULL));
        s.add(PackedPos.pack(0, 1, 0), "minecraft:glass", BattleSystem.HULL);
        assertEquals(1, s.size());
    }

    @Test
    void changedBlocksAreAdoptedNotLost() {
        SurveyStore s = new SurveyStore();
        int slot = s.add(PackedPos.pack(0, 0, 0), "minecraft:copper_block", BattleSystem.HULL);
        s.adopt(slot, "minecraft:exposed_copper", BattleSystem.HULL);
        assertEquals(0, s.lostCount());
        assertEquals("minecraft:exposed_copper", s.blockAt(slot));
        s.adopt(slot, "create:basin", BattleSystem.CARGO);
        assertEquals(0, s.total(BattleSystem.HULL));
        assertEquals(1, s.total(BattleSystem.CARGO), "the counters follow a block that changes system");
    }

    @Test
    void aLossKeepsItsFirstTimeAndCause() {
        SurveyStore s = hull(2);
        assertTrue(s.markLost(1, 40L, SurveyStore.CAUSE_FIRE));
        assertFalse(s.markLost(1, 90L, SurveyStore.CAUSE_HIT));
        assertEquals(40L, s.lostAt(1));
        assertEquals(SurveyStore.CAUSE_FIRE, s.causeAt(1));
    }

    @Test
    void theRoundRobinVisitsEverySlotAndWraps() {
        SurveyStore s = hull(3);
        boolean[] seen = new boolean[3];
        for (int i = 0; i < 3; i++) seen[s.nextSlot()] = true;
        assertTrue(seen[0] && seen[1] && seen[2]);
        assertEquals(0, s.nextSlot());
        s.remove(2);
        for (int i = 0; i < 10; i++) assertTrue(s.nextSlot() < s.size());
    }

    @Test
    void stopsAtTheCapAndRefusesDuplicates() {
        SurveyStore s = new SurveyStore();
        int added = 0;
        for (int x = -1024; x < 1024 && added <= SurveyStore.CAP; x++) {
            for (int z = -40; z < 40 && added <= SurveyStore.CAP; z++) {
                if (s.add(PackedPos.pack(x, 0, z), "minecraft:stone", BattleSystem.HULL) >= 0) added++;
                else break;
            }
            if (s.isFull()) break;
        }
        assertEquals(SurveyStore.CAP, s.size());
        assertEquals(-1, s.add(PackedPos.pack(1000, 5, 5), "minecraft:stone", BattleSystem.HULL));
        SurveyStore t = hull(1);
        assertEquals(-1, t.add(PackedPos.pack(0, 0, 0), "minecraft:dirt", BattleSystem.HULL));
    }

    @Test
    void refilingAPaletteEntryMovesItsCounts() {
        SurveyStore s = new SurveyStore();
        int a = s.add(PackedPos.pack(0, 0, 0), "create:shaft", BattleSystem.PROPULSION);
        s.add(PackedPos.pack(1, 0, 0), "create:shaft", BattleSystem.PROPULSION);
        s.markLost(a, 3L, SurveyStore.CAUSE_HIT);
        s.setPaletteSystem(s.paletteAt(a), BattleSystem.GUNS);
        assertEquals(2, s.total(BattleSystem.GUNS));
        assertEquals(1, s.lost(BattleSystem.GUNS));
        assertEquals(0, s.total(BattleSystem.PROPULSION));
    }
}
