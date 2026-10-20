package com.createstats.dynlight;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmissionPlanTest {
    private static final long A = BlockPos.asLong(0, 64, 0);
    private static final long B = BlockPos.asLong(3, 64, 0);
    private static final long C = BlockPos.asLong(0, 64, 6);
    private static final long FAR = BlockPos.asLong(40, 64, 40);

    private static Long2ByteMap cells(long[] positions, int[] levels) {
        final Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        for (int i = 0; i < positions.length; i++) {
            map.put(positions[i], (byte) levels[i]);
        }
        return map;
    }

    private static EmissionPlan.Step plan(Long2ByteMap applied, Long2ByteMap wanted, int maxCells, int maxChanges) {
        return EmissionPlan.plan(applied, wanted, 0.5, 64.5, 0.5, maxCells, maxChanges);
    }

    @Test
    void twoLightsOnOneCellKeepTheBrighter() {
        final Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();
        EmissionPlan.mergeMax(map, A, 5);
        EmissionPlan.mergeMax(map, A, 9);
        EmissionPlan.mergeMax(map, A, 3);
        assertEquals(9, map.get(A));
        EmissionPlan.mergeMax(map, B, 0);
        EmissionPlan.mergeMax(map, B, -4);
        assertFalse(map.containsKey(B), "a light that is off places nothing");
        EmissionPlan.mergeMax(map, C, 40);
        assertEquals(15, map.get(C), "nothing is brighter than 15");
    }

    @Test
    void anUnchangedFieldIsIdleAndHandsBackTheSameMap() {
        final Long2ByteMap applied = Long2ByteMaps.unmodifiable(cells(new long[] {A, B}, new int[] {10, 4}));
        final EmissionPlan.Step step = plan(applied, cells(new long[] {A, B}, new int[] {10, 4}), 256, 64);
        assertTrue(step.isIdle());
        assertSame(applied, step.next(), "an idle update must not republish, so the engine can skip the swap");
        assertEquals(0, step.deferred());

        final EmissionPlan.Step empty = plan(Long2ByteMaps.EMPTY_MAP, new Long2ByteOpenHashMap(), 256, 64);
        assertTrue(empty.isIdle());
    }

    @Test
    void anAddedARemovedAndARelitCellAreEachAChange() {
        final Long2ByteMap applied = cells(new long[] {A, B}, new int[] {10, 5});
        final Long2ByteMap wanted = cells(new long[] {A, C}, new int[] {12, 7});
        final EmissionPlan.Step step = plan(applied, wanted, 256, 64);

        assertEquals(3, step.changed().length);
        assertEquals(B, step.changed()[0], "the light that left B is taken out first");
        final long[] rest = Arrays.copyOfRange(step.changed(), 1, 3);
        Arrays.sort(rest);
        final long[] expected = {A, C};
        Arrays.sort(expected);
        assertArrayEquals(expected, rest);
        assertEquals(12, step.next().get(A));
        assertEquals(7, step.next().get(C));
        assertFalse(step.next().containsKey(B));
        assertEquals(0, step.deferred());
    }

    @Test
    void removalsGoFirstThenTheNearestChanges() {
        final Long2ByteMap applied = cells(new long[] {FAR}, new int[] {10});
        final Long2ByteMap wanted = cells(new long[] {C, A, B}, new int[] {6, 6, 6});
        final EmissionPlan.Step step = plan(applied, wanted, 256, 3);

        assertArrayEquals(new long[] {FAR, A, B}, step.changed(),
                "a light gone out beats a nearer new one; then nearest first");
        assertEquals(1, step.deferred(), "C did not fit and waits");
        assertFalse(step.next().containsKey(C));
    }

    @Test
    void aBudgetDelaysChangesButNeverLosesOne() {
        Long2ByteMap applied = cells(new long[] {FAR, B}, new int[] {10, 3});
        final Long2ByteMap wanted = cells(new long[] {A, B, C}, new int[] {15, 9, 2});
        int updates = 0;
        while (true) {
            final EmissionPlan.Step step = plan(applied, wanted, 256, 1);
            if (step.isIdle()) {
                break;
            }
            assertEquals(1, step.changed().length);
            applied = step.next();
            updates++;
            assertTrue(updates < 10, "the plan converges");
        }
        assertEquals(4, updates, "one removal, one relit cell, two additions: four changes, one per update");
        assertEquals(wanted.size(), applied.size());
        for (Long2ByteMap.Entry e : wanted.long2ByteEntrySet()) {
            assertEquals(e.getByteValue(), applied.get(e.getLongKey()));
        }
    }

    @Test
    void theCellBudgetKeepsTheNearestCells() {
        final long[] line = new long[6];
        final int[] levels = new int[6];
        for (int i = 0; i < line.length; i++) {
            line[i] = BlockPos.asLong(i * 5, 64, 0);
            levels[i] = 8;
        }
        final EmissionPlan.Step step = plan(Long2ByteMaps.EMPTY_MAP, cells(line, levels), 3, 64);
        assertEquals(3, step.next().size());
        for (int i = 0; i < 3; i++) {
            assertTrue(step.next().containsKey(line[i]), "cell " + i + " is among the nearest three");
        }
    }

    @Test
    void onATieOfDistanceTheBrighterCellIsKept() {
        final long east = BlockPos.asLong(4, 64, 0);
        final long west = BlockPos.asLong(-4, 64, 0);
        final EmissionPlan.Step step = plan(Long2ByteMaps.EMPTY_MAP,
                cells(new long[] {east, west}, new int[] {5, 11}), 1, 64);
        assertEquals(1, step.next().size());
        assertEquals(11, step.next().get(west));
    }

    @Test
    void aLevelOutsideOneToFifteenIsMadeLegalBeforeItIsPlanned() {
        final Long2ByteOpenHashMap raw = new Long2ByteOpenHashMap();
        raw.put(A, (byte) 40);
        raw.put(B, (byte) 0);
        raw.put(C, (byte) -3);
        final EmissionPlan.Step step = plan(Long2ByteMaps.EMPTY_MAP, raw, 256, 64);
        assertEquals(1, step.next().size());
        assertEquals(15, step.next().get(A));
    }

    @Test
    void theAppliedMapIsNeverChangedAndTheNextOneCannotBe() {
        final Long2ByteMap applied = Long2ByteMaps.unmodifiable(cells(new long[] {A}, new int[] {10}));
        final EmissionPlan.Step step = plan(applied, cells(new long[] {B}, new int[] {7}), 256, 64);
        assertEquals(10, applied.get(A), "planning never writes into what the light engine is reading");
        assertThrows(UnsupportedOperationException.class, () -> step.next().put(C, (byte) 3),
                "a published field is replaced whole, never edited under the light engine");
    }

    @Test
    void aZeroBudgetChangesNothingAndCountsWhatWaits() {
        final EmissionPlan.Step step = plan(Long2ByteMaps.EMPTY_MAP, cells(new long[] {A, B}, new int[] {3, 3}), 256, 0);
        assertTrue(step.isIdle());
        assertEquals(2, step.deferred());
    }
}
