package com.createstats.power;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduDeviceIdsTest {

    private static final String RANGE = "Induction Range";
    private static final String LAMP = "Strip Lamp";

    private static Map<BlockPos, String> branch(String name, int count) {
        final Map<BlockPos, String> out = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            out.put(new BlockPos(i, 64, 0), name);
        }
        return out;
    }

    @Test
    @DisplayName("numbers start at one and run without gaps")
    void numbersAreDense() {
        final PduDeviceIds ids = new PduDeviceIds();
        final Map<BlockPos, String> present = branch(RANGE, 5);
        ids.assignAll(present);
        final boolean[] seen = new boolean[6];
        for (BlockPos p : present.keySet()) {
            final int id = ids.peek(p);
            assertTrue(id >= 1 && id <= 5, "id out of range: " + id);
            seen[id] = true;
        }
        for (int i = 1; i <= 5; i++) {
            assertTrue(seen[i], "no device got id " + i);
        }
    }

    @Test
    @DisplayName("numbering is per name, so lamps and ranges both start at one")
    void numberingIsScopedByName() {
        final PduDeviceIds ids = new PduDeviceIds();
        final Map<BlockPos, String> present = new LinkedHashMap<>();
        present.put(new BlockPos(0, 64, 0), RANGE);
        present.put(new BlockPos(1, 64, 0), RANGE);
        present.put(new BlockPos(2, 64, 0), LAMP);
        ids.assignAll(present);
        assertEquals(1, ids.peek(new BlockPos(0, 64, 0)));
        assertEquals(2, ids.peek(new BlockPos(1, 64, 0)));
        assertEquals(1, ids.peek(new BlockPos(2, 64, 0)),
                "a lamp must not be numbered against the ranges");
    }

    @Test
    @DisplayName("inserting a machine does not renumber the ones already there")
    void insertingDoesNotRenumber() {
        final PduDeviceIds ids = new PduDeviceIds();
        final Map<BlockPos, String> present = branch(RANGE, 4);
        ids.assignAll(present);
        final Map<BlockPos, Integer> before = new HashMap<>();
        for (BlockPos p : present.keySet()) {
            before.put(p, ids.peek(p));
        }
        // A new range dropped in at a position that sorts in the middle of the existing run.
        final BlockPos inserted = new BlockPos(2, 64, -1);
        present.put(inserted, RANGE);
        ids.assignAll(present);
        for (Map.Entry<BlockPos, Integer> e : before.entrySet()) {
            assertEquals(e.getValue().intValue(), ids.peek(e.getKey()),
                    "device at " + e.getKey() + " was renumbered by an insertion");
        }
        assertEquals(5, ids.peek(inserted), "the new one takes the next free number");
    }

    @Test
    @DisplayName("rebuilding every tick never changes a number")
    void repeatedRebuildsAreStable() {
        final PduDeviceIds ids = new PduDeviceIds();
        final Map<BlockPos, String> present = branch(RANGE, 8);
        ids.assignAll(present);
        final BlockPos watched = new BlockPos(3, 64, 0);
        final int original = ids.peek(watched);
        for (int tick = 0; tick < 200; tick++) {
            ids.assignAll(present);
            assertEquals(original, ids.peek(watched), "renumbered on rebuild " + tick);
        }
    }

    @Test
    @DisplayName("a switch taking devices off the branch and back does not reshuffle them")
    void anOpenSwitchDoesNotReshuffle() {
        final PduDeviceIds ids = new PduDeviceIds();
        final Map<BlockPos, String> all = branch(RANGE, 6);
        ids.assignAll(all);
        final Map<BlockPos, Integer> before = new HashMap<>();
        for (BlockPos p : all.keySet()) {
            before.put(p, ids.peek(p));
        }
        // The switch opens: the last three drop off the roster entirely for a while.
        final Map<BlockPos, String> partial = new LinkedHashMap<>();
        int kept = 0;
        for (Map.Entry<BlockPos, String> e : all.entrySet()) {
            if (kept++ < 3) {
                partial.put(e.getKey(), e.getValue());
            }
        }
        for (int tick = 0; tick < 50; tick++) {
            ids.assignAll(partial);
        }
        ids.assignAll(all);   // and it closes again
        for (Map.Entry<BlockPos, Integer> e : before.entrySet()) {
            assertEquals(e.getValue().intValue(), ids.peek(e.getKey()),
                    "device at " + e.getKey() + " changed number across a switch cycle");
        }
    }

    @Test
    @DisplayName("numbering is reproducible from scratch, so a reload lands on the same numbers")
    void aColdStartReproducesTheSameNumbers() {
        // Deliberately different iteration orders, which is what a fresh HashMap after a reload gives.
        final Map<BlockPos, String> forwards = new LinkedHashMap<>();
        final Map<BlockPos, String> backwards = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            forwards.put(new BlockPos(i, 64, 0), RANGE);
        }
        for (int i = 5; i >= 0; i--) {
            backwards.put(new BlockPos(i, 64, 0), RANGE);
        }
        final PduDeviceIds a = new PduDeviceIds();
        final PduDeviceIds b = new PduDeviceIds();
        a.assignAll(forwards);
        b.assignAll(backwards);
        for (BlockPos p : forwards.keySet()) {
            assertEquals(a.peek(p), b.peek(p),
                    "a reload would renumber " + p + ": " + a.peek(p) + " became " + b.peek(p));
        }
    }

    @Test
    @DisplayName("two devices never share a number")
    void numbersAreUnique() {
        final PduDeviceIds ids = new PduDeviceIds();
        final Map<BlockPos, String> present = branch(RANGE, 40);
        ids.assignAll(present);
        final java.util.Set<Integer> used = new java.util.HashSet<>();
        for (BlockPos p : present.keySet()) {
            assertTrue(used.add(ids.peek(p)), "number " + ids.peek(p) + " was handed out twice");
        }
    }

    @Test
    @DisplayName("replacing a block with a different kind renumbers it under the new name")
    void replacingABlockRenumbers() {
        final PduDeviceIds ids = new PduDeviceIds();
        final BlockPos spot = new BlockPos(0, 64, 0);
        final Map<BlockPos, String> present = new LinkedHashMap<>();
        present.put(spot, RANGE);
        present.put(new BlockPos(1, 64, 0), RANGE);
        ids.assignAll(present);
        assertEquals(1, ids.peek(spot));

        present.put(spot, LAMP);   // the range is broken and a lamp goes up in its place
        ids.assignAll(present);
        assertEquals(1, ids.peek(spot), "the lamp is the first lamp, whatever the range was");
        assertNotEquals(RANGE, present.get(spot));
    }

    @Test
    @DisplayName("the label is zero padded so the numbers form a scannable column")
    void labelIsPadded() {
        assertEquals("Induction Range ID:07", PduDeviceIds.label(RANGE, 7, false));
        assertEquals("Induction Range ID:15", PduDeviceIds.label(RANGE, 15, false));
    }

    @Test
    @DisplayName("a one of a kind block reads as its bare name, since numbering it says nothing")
    void aUniqueDeviceIsNotNumbered() {
        assertEquals("Power Distribution Unit",
                PduDeviceIds.label("Power Distribution Unit", 1, true));
        assertEquals("Power Distribution Unit",
                PduDeviceIds.label("Power Distribution Unit", 0, false));
    }

    @Test
    @DisplayName("the table is bounded, so a long lived panel cannot grow without limit")
    void pruningKeepsTheTableBounded() {
        final PduDeviceIds ids = new PduDeviceIds();
        // Walk a moving branch past the prune threshold, one new position at a time.
        for (int i = 0; i < PduDeviceIds.PRUNE_ABOVE + 200; i++) {
            final Map<BlockPos, String> present = new LinkedHashMap<>();
            present.put(new BlockPos(i, 64, 0), RANGE);
            ids.assignAll(present);
        }
        assertTrue(ids.size() <= PduDeviceIds.PRUNE_ABOVE + 1,
                "table grew to " + ids.size() + " entries");
    }
}
