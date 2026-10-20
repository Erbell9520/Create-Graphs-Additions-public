package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SurveyShells;
import com.createstats.seismic.VeinBook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicArchiveTest {
    private static final String IRON = "c:ores/iron";
    private static final String DIAMOND = "c:ores/diamond";

    private static Set<Long> truth(SiteGround g, ArchiveBlow b, int oreClass) {
        final Set<Long> out = new HashSet<>();
        final int r = b.reach;
        for (int x = b.x - r; x <= b.x + r; x++) {
            for (int y = b.y - r - 1; y < b.y; y++) {
                for (int z = b.z - r; z <= b.z + r; z++) {
                    if (b.covers(x, y, z) && g.classAt(x, y, z) == oreClass) {
                        out.add(ArchiveBlow.key(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    private static Set<Long> held(ArchiveBlow... blows) {
        final Set<Long> out = new HashSet<>();
        for (final ArchiveBlow b : blows) {
            for (final int c : b.cells()) {
                assertTrue(out.add(ArchiveBlow.key(b.cellX(c), b.cellY(c), b.cellZ(c))),
                        "a block is held twice across blows of one target");
            }
        }
        return out;
    }

    private static int ironClass() {
        return 10 + java.util.Arrays.asList(SiteGround.ORES).indexOf("iron");
    }

    @Test
    @DisplayName("a blow on the owner's iron rig holds exactly the iron its bowl reaches")
    void oneBlowHoldsItsBowl() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow b = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 1_000L);
        assertTrue(b.done() && !b.full(), "an iron survey ends on its own and never fills the book");
        assertEquals(64.0, b.recorded(), 1.0e-6);
        final Set<Long> truth = truth(g, b, ironClass());
        assertEquals(truth, held(b), "the archive holds what the ground holds, block for block");
        assertEquals(b.blocksFound(), b.cellCount(), "nothing newer has re-read it yet");
        assertTrue(b.veinsFound() > 50, "the owner's goggle read 207 iron veins at this rig: " + b.veinsFound());
        assertEquals(b.cellCount(), a.cellTotal());
    }

    @Test
    @DisplayName("a second blow after mining loses the mined vein and keeps the rest, now as the newer blow's")
    void aMinedVeinIsGoneAfterTheNextBlow() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow first = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 1_000L);

        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (final int c : first.cells()) {
            final double d = first.distanceTo(first.cellX(c), first.cellY(c), first.cellZ(c));
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        final int mx = first.cellX(best);
        final int my = first.cellY(best);
        final int mz = first.cellZ(best);
        g.mine(mx, my, mz);
        final ArchiveBlow second = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 9_000L);
        assertEquals(0, first.cellCount(), "the newer bowl is the same bowl: the older blow keeps nothing");
        final Set<Long> now = held(first, second);
        assertFalse(now.contains(ArchiveBlow.key(mx, my, mz)), "the mined block is gone");
        assertEquals(truth(g, second, ironClass()), now);
        assertEquals(first.blocksFound(), second.blocksFound() + 1, "the history still says what each blow found");
    }

    @Test
    @DisplayName("overlapping rigs: the newer wins its own bowl, the older keeps what only it reached")
    void overlappingBowlsSplitTheGround() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow west = g.strike(a, SiteGround.WEST_RIG, IRON, 48, 1_000L);
        final int westBefore = west.cellCount();
        final ArchiveBlow east = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 2_000L);

        for (final int c : west.cells()) {
            assertFalse(east.covers(west.cellX(c), west.cellY(c), west.cellZ(c)), "the older kept re-read ground");
        }
        assertTrue(west.cellCount() < westBefore, "the bowls overlap, so the older gave some up");
        assertTrue(west.cellCount() > 0, "and kept what only it reached");

        final Set<Long> both = truth(g, west, ironClass());
        both.addAll(truth(g, east, ironClass()));
        assertEquals(both, held(west, east));
    }

    @Test
    @DisplayName("targets never touch: a diamond blow over iron ground leaves the iron where it was")
    void targetsAreIndependent() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow iron = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 1_000L);
        final int cells = iron.cellCount();
        g.strike(a, SiteGround.OWNER_DIAMOND_RIG, DIAMOND, 64, 2_000L);
        assertEquals(cells, iron.cellCount());
    }

    @Test
    @DisplayName("an older survey still running keeps nothing from ground a newer one has read")
    void aRunningOlderBlowCannotTakeBackGround() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();

        final ArchiveBlow older = a.begin(159, 69, -359, IRON, 32, 1_000L, -64, 0L, 0L);
        final ArchiveBlow newer = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 32, 1_001L);
        final int newerCells = newer.cellCount();
        assertTrue(newerCells > 0);

        final List<VeinBook.Vein> veins = new ArrayList<>();
        final int[] blocks = new int[newer.cellCount()];
        for (int i = 0; i < blocks.length; i++) {
            final int c = newer.cells()[i];
            blocks[i] = SurveyShells.pack(newer.cellX(c) - 159, newer.cellY(c) - 69, newer.cellZ(c) + 359);
        }
        veins.add(new VeinBook.Vein(0, blocks.length, 1.0f, 32.0f, blocks, 0, 0, 0));
        a.step(older, 32.0, false, veins, true);
        assertEquals(0, older.cellCount(), "the newer blow read that ground: the older keeps none of it");
        assertEquals(newerCells, newer.cellCount(), "and takes none of the newer's");
        assertEquals(blocks.length, older.blocksFound(), "its own history still says what it found");
    }

    @Test
    @DisplayName("a chunk that was not loaded is neither read nor taken over")
    void holesAreUnknown() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow first = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 1_000L);

        final int rx = SiteGround.OWNER_IRON_RIG[0];
        final int rz = SiteGround.OWNER_IRON_RIG[2];
        final long[] holes = ArchiveBlow.holes(rx, rz, 48, (cx, cz) -> !(cx == (rx >> 4) + 1 && cz == rz >> 4));
        assertTrue(holes[0] != 0L || holes[1] != 0L);
        final ArchiveBlow second = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 2_000L, holes[0], holes[1],
                Integer.MAX_VALUE);
        for (final int c : first.cells()) {
            assertTrue(second.hole(first.cellX(c), first.cellZ(c)), "only the hole's ground stays the older blow's");
        }
        for (final int c : second.cells()) {
            assertFalse(second.hole(second.cellX(c), second.cellZ(c)), "nothing is read in a hole");
        }
        assertFalse(second.covers((rx & ~15) + 16 + 3, 40, rz), "a hole is not covered");
    }

    @Test
    @DisplayName("a void survey that fills its book knows the ground only as far as it kept everything")
    void aFullBookStopsTheRecordedRadius() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();

        final ArchiveBlow voids = g.strike(a, SiteGround.DEEP_RIG, SeismicCatalog.VOIDS, 64, 1_000L);
        assertTrue(voids.full(), "the ground under the deep rig holds more than 32,768 blocks of cave in reach");
        assertTrue(voids.recorded() < 64.0 && voids.recorded() > 8.0, "recorded " + voids.recorded());
        assertEquals(64.0, voids.front(), 1.0e-6, "the wave still ran to its reach");
        for (final int c : voids.cells()) {
            assertTrue(voids.distanceTo(voids.cellX(c), voids.cellY(c), voids.cellZ(c)) <= 64.0 + 1.0e-6);
        }

        for (int dx = -10; dx <= 10; dx++) {
            for (int dy = -12; dy <= -1; dy++) {
                for (int dz = -10; dz <= 10; dz++) {
                    final int x = voids.x + dx;
                    final int y = voids.y + dy;
                    final int z = voids.z + dz;
                    final boolean cave = g.classAt(x, y, z) == 1 && g.covered(x, y, z);
                    if (voids.covers(x, y, z) && cave) {
                        assertTrue(contains(voids, x, y, z), "a cave block inside the recorded radius was not kept");
                    }
                }
            }
        }
    }

    private static boolean contains(ArchiveBlow b, int x, int y, int z) {
        final int p = SurveyShells.pack(x - b.x, y - b.y, z - b.z);
        for (final int c : b.cells()) {
            if (ArchiveBlow.packedOf(c) == p) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("a survey cut off mid-wave keeps what it read and is closed when it goes stale")
    void aCutOffBlowIsClosed() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final ArchiveBlow b = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 1_000L, 0L, 0L, 12);
        assertFalse(b.done());
        assertTrue(b.front() > 18.0 && b.front() < 20.0, "twelve ticks of wave: " + b.front());
        assertEquals(b, a.open(158, 69, -359));
        a.closeStale(1_000L + SeismicArchive.STALE_TICKS - 1);
        assertFalse(b.done(), "not yet stale");
        a.closeStale(1_000L + SeismicArchive.STALE_TICKS + 1);
        assertTrue(b.done() && b.cutShort(), "closed, and it says it was cut short");
        assertNull(a.open(158, 69, -359));

        final ArchiveBlow c = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 5_000L, 0L, 0L, 3);
        final ArchiveBlow d = g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 64, 5_100L);
        assertTrue(c.done() && c.cutShort() && d.done());
    }

    @Test
    @DisplayName("the archive drops its oldest finished blows to stay inside its caps")
    void capsDropTheOldest() {
        final SeismicArchive a = new SeismicArchive();
        final List<ArchiveBlow> made = new ArrayList<>();
        for (int i = 0; i < SeismicArchive.MAX_BLOWS + 5; i++) {
            final ArchiveBlow b = a.begin(i * 200, 64, 0, IRON, 32, i, -64, 0L, 0L);
            a.step(b, 32.0, false, List.of(), true);
            made.add(b);
        }
        assertEquals(SeismicArchive.MAX_BLOWS, a.blows().size());
        assertEquals(made.get(5), a.blows().get(0), "the five oldest went");
    }

    @Test
    @DisplayName("every change bumps the revision of the rig it touched")
    void revisions() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final long rig = ArchiveBlow.key(158, 69, -359);
        final long other = ArchiveBlow.key(140, 68, -385);
        assertEquals(0L, a.revisionOf(rig));
        g.strike(a, SiteGround.WEST_RIG, IRON, 48, 1_000L);
        final long westRev = a.revisionOf(other);
        assertTrue(westRev > 0L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 2_000L);
        assertTrue(a.revisionOf(rig) > westRev);
        assertTrue(a.revisionOf(other) > westRev, "the older rig's blow lost cells, so its revision moved too");
    }

    @Test
    @DisplayName("keys pack and unpack the way BlockPos.asLong does, Sable's plots included")
    void keys() {
        for (final int[] p : new int[][] {{0, 0, 0}, {158, 69, -359}, {-30_000_000, -64, 29_999_999},
                {20_480_123, 319, -20_480_456}}) {
            final long k = ArchiveBlow.key(p[0], p[1], p[2]);
            assertEquals(p[0], ArchiveBlow.keyX(k));
            assertEquals(p[1], ArchiveBlow.keyY(k));
            assertEquals(p[2], ArchiveBlow.keyZ(k));
        }
    }
}
