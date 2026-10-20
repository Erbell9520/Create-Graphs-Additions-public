package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellScanTest {
    private static final int BX = 100;
    private static final int BY = 64;
    private static final int BZ = -40;

    private static final class Planted implements ShellScan.Ground<Object> {
        final Set<Long> ore = new HashSet<>();
        final Set<Long> skipped = new HashSet<>();
        final Map<Long, Integer> reads = new HashMap<>();
        final Object section = new Object();

        void plant(int x, int y, int z) {
            ore.add(key(x, y, z));
        }

        @Override
        public Object section(int sx, int sy, int sz) {
            return skipped.contains(key(sx, sy, sz)) ? null : section;
        }

        @Override
        public int kindAt(Object s, int x, int y, int z) {
            reads.merge(key(x, y, z), 1, Integer::sum);
            return ore.contains(key(x, y, z)) ? 0 : -1;
        }

        @Override
        public boolean covered(int x, int y, int z) {
            return true;
        }

        static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }
    }

    private static List<VeinBook.Vein> runAll(ShellScan<Object> scan, List<Integer> closedAt) {
        final List<VeinBook.Vein> all = new ArrayList<>();
        while (!scan.done()) {
            for (final VeinBook.Vein v : scan.step()) {
                all.add(v);
                closedAt.add(scan.tick());
            }
        }
        return all;
    }

    @Test
    @DisplayName("each vein in the bowl is found once and whole; nothing above the plate or past the radius")
    void findsWhatIsThere() {
        final Planted ground = new Planted();

        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                ground.plant(BX + 4 + dx, BY - 10, BZ + dz);
            }
        }

        ground.plant(BX, BY - 2, BZ);
        ground.plant(BX + 1, BY - 2, BZ);

        ground.plant(BX + 3, BY, BZ);
        ground.plant(BX + 30, BY - 5, BZ);
        final ShellScan<Object> scan = new ShellScan<>(ground, BX, BY, BZ, 16, new VeinBook(1024, true));
        final List<Integer> closedAt = new ArrayList<>();
        final List<VeinBook.Vein> veins = runAll(scan, closedAt);
        assertEquals(2, veins.size());
        assertEquals(2, veins.get(0).count(), "the near vein closes first");
        assertEquals(9, veins.get(1).count());
        assertEquals(11, scan.blocks());
        assertEquals(2, scan.veins());
        assertEquals(SeismicWave.scanTicks(16), scan.tick());

        for (int i = 0; i < veins.size(); i++) {
            final VeinBook.Vein v = veins.get(i);
            final int soonest = (int) Math.ceil((v.farthest() + VeinBook.MARGIN) / SeismicWave.BLOCKS_PER_TICK - 1e-9);
            assertEquals(soonest, (int) closedAt.get(i), "vein " + i + " closed later than it could have");
            assertTrue(closedAt.get(i) <= v.echoTick() + 2, "vein " + i + " closed more than two ticks after its echo");
        }
        assertTrue(closedAt.get(1) <= veins.get(1).echoTick(), "a vein ten blocks down is closed before its echo returns");
    }

    @Test
    @DisplayName("every block of the bowl is read exactly once, and a skipped section's never")
    void readsEachBlockOnce() {
        final Planted ground = new Planted();

        ground.skipped.add(Planted.key(BX >> 4, (BY - 1) >> 4, BZ >> 4));
        final ShellScan<Object> scan = new ShellScan<>(ground, BX, BY, BZ, 24, new VeinBook(16, true));
        runAll(scan, new ArrayList<>());
        int expected = 0;
        int inSkipped = 0;
        for (int i = 0; i < SurveyShells.countWithin(24); i++) {
            final int p = SurveyShells.packed(i);
            final int x = BX + SurveyShells.dx(p);
            final int y = BY + SurveyShells.dy(p);
            final int z = BZ + SurveyShells.dz(p);
            if (ground.skipped.contains(Planted.key(x >> 4, y >> 4, z >> 4))) {
                inSkipped++;
                assertFalse(ground.reads.containsKey(Planted.key(x, y, z)), "a skipped section's block was read");
            } else {
                expected++;
                assertEquals(1, ground.reads.get(Planted.key(x, y, z)), "a block was read other than once");
            }
        }
        assertTrue(inSkipped > 0, "the test skipped nothing");
        assertEquals(expected, scan.checked());
        assertEquals(expected, ground.reads.size());
    }

    @Test
    @DisplayName("a full book keeps the nearest blocks and says it ran out of paper")
    void fullBook() {
        final Planted ground = new Planted();
        for (int k = 2; k < 40; k++) {
            ground.plant(BX + k, BY - 2, BZ);
            ground.plant(BX - k, BY - 2, BZ);
        }
        final ShellScan<Object> scan = new ShellScan<>(ground, BX, BY, BZ, 32, new VeinBook(10, false));
        final List<VeinBook.Vein> veins = runAll(scan, new ArrayList<>());
        assertTrue(scan.full());
        assertEquals(10, scan.blocks());
        for (final VeinBook.Vein v : veins) {
            assertTrue(v.farthest() < 8.0, "a far block was kept over a near one");
        }
    }
}
