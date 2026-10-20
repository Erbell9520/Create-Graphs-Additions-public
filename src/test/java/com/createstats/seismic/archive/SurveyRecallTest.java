package com.createstats.seismic.archive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.ShellScan;
import com.createstats.seismic.SurveyShells;
import com.createstats.seismic.VeinBook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyRecallTest {
    private static final String IRON = "c:ores/iron";
    private static final String DIAMOND = "c:ores/diamond";

    private static List<VeinBook.Vein> closedBy(SiteGround g, int[] rig, String target, int reach) {
        final List<VeinBook.Vein> out = new ArrayList<>();
        g.tune(target);
        final ShellScan<int[]> scan = new ShellScan<>(g, rig[0], rig[1], rig[2], reach,
                new VeinBook(32_768, SeismicCatalog.isOre(target)));
        while (!scan.done()) {
            out.addAll(scan.step());
        }
        return out;
    }

    private static Set<Set<Integer>> asSets(List<VeinBook.Vein> veins) {
        final Set<Set<Integer>> out = new HashSet<>();
        for (final VeinBook.Vein v : veins) {
            final Set<Integer> s = new HashSet<>();
            for (final int p : v.blocks()) {
                s.add(p);
            }
            out.add(s);
        }
        return out;
    }

    @Test
    @DisplayName("a recall gives back the veins the survey closed, block for block, nearest first")
    void recallIsTheSurvey() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 1_000L);
        final int[] r = SiteGround.OWNER_IRON_RIG;
        final SurveyRecall rc = a.recall(r[0], r[1], r[2], IRON);
        assertNotNull(rc);
        assertTrue(rc.complete(), "nothing has re-read the ground");
        assertEquals(rc.blocksFound(), rc.kept());
        assertEquals(rc.veinsFound(), rc.veins().size(), "regrouping finds the veins the book closed");
        final List<VeinBook.Vein> truth = closedBy(g, r, IRON, 48);
        assertEquals(asSets(truth), asSets(rc.veins()), "the same blocks in the same veins");
        for (int i = 1; i < rc.veins().size(); i++) {
            assertTrue(rc.veins().get(i - 1).nearest() <= rc.veins().get(i).nearest(), "nearest first");
        }
        for (final VeinBook.Vein v : rc.veins()) {
            final VeinBook.Vein t = truth.stream().filter(x -> x.blocks().length == v.blocks().length
                    && new HashSet<>(asSets(List.of(x))).equals(asSets(List.of(v)))).findFirst().orElseThrow();
            assertEquals(t.nearest(), v.nearest(), 1.0e-4f, "its echo times the same");
            assertEquals(t.farthest(), v.farthest(), 1.0e-4f);
            assertEquals(t.cx(), v.cx(), 1.0e-9);
            assertEquals(t.cy(), v.cy(), 1.0e-9);
            assertEquals(t.cz(), v.cz(), 1.0e-9);
            assertEquals(t.kind(), v.kind());
            for (int k = 1; k < v.blocks().length; k++) {
                final int p = v.blocks()[k - 1];
                final int q = v.blocks()[k];
                assertTrue(SurveyShells.distanceOf(SurveyShells.dx(p), SurveyShells.dy(p), SurveyShells.dz(p))
                        <= SurveyShells.distanceOf(SurveyShells.dx(q), SurveyShells.dy(q), SurveyShells.dz(q)) + 1.0e-9,
                        "a vein's blocks nearest first, as the scan found them");
            }
        }
        assertEquals(1_000L, rc.at());
        assertEquals(48, rc.reach());
        assertFalse(rc.cutShort());
    }

    @Test
    @DisplayName("caves and liquids regroup across faces only, and lava never joins water")
    void voidsAndLiquidsJoinAcrossFaces() {
        final SiteGround g = SiteGround.owner();
        for (final String target : new String[] {SeismicCatalog.VOIDS, SeismicCatalog.LIQUIDS}) {
            final SeismicArchive a = new SeismicArchive();
            final int[] r = SiteGround.OWNER_IRON_RIG;
            g.strike(a, r, target, 24, 1_000L);
            final SurveyRecall rc = a.recall(r[0], r[1], r[2], target);
            assertNotNull(rc, target);
            assertEquals(asSets(closedBy(g, r, target, 24)), asSets(rc.veins()), target + ": the same veins");
        }
    }

    @Test
    @DisplayName("the newest finished blow wins; another target, another rig, or nothing struck gives nothing")
    void whichBlowIsRecalled() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final int[] r = SiteGround.OWNER_IRON_RIG;
        assertNull(a.recall(r[0], r[1], r[2], IRON), "nothing struck yet");
        g.strike(a, r, IRON, 32, 1_000L);
        g.strike(a, r, DIAMOND, 32, 2_000L);
        assertEquals(IRON, a.recall(r[0], r[1], r[2], IRON).target());
        assertEquals(DIAMOND, a.recall(r[0], r[1], r[2], null).target(), "any target: the newest blow");
        g.strike(a, r, IRON, 40, 3_000L);
        assertEquals(40, a.recall(r[0], r[1], r[2], IRON).reach(), "the newer iron blow");
        final int[] d = SiteGround.OWNER_DIAMOND_RIG;
        assertNull(a.recall(d[0], d[1], d[2], IRON), "another rig's position");

        g.strike(a, r, IRON, 64, 4_000L, 0L, 0L, 5);
        assertEquals(40, a.recall(r[0], r[1], r[2], IRON).reach(), "the running blow is not recalled");
    }

    @Test
    @DisplayName("ground a newer blow re-read is the newer blow's: the recall says it is no longer complete")
    void reReadGroundLeavesTheRecall() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final int[] west = SiteGround.WEST_RIG;
        g.strike(a, west, IRON, 48, 1_000L);
        final int before = a.recall(west[0], west[1], west[2], IRON).kept();
        g.strike(a, SiteGround.OWNER_IRON_RIG, IRON, 48, 2_000L);
        final SurveyRecall after = a.recall(west[0], west[1], west[2], IRON);
        assertTrue(after.kept() < before, "the overlap went to the newer blow");
        assertFalse(after.complete());
        assertEquals(after.blocksFound(), before, "what the survey found is still said");
    }

    @Test
    @DisplayName("a recall is a copy: changing it changes nothing in the record")
    void recallIsACopy() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        final int[] r = SiteGround.OWNER_IRON_RIG;
        final ArchiveBlow b = g.strike(a, r, IRON, 32, 1_000L);
        final int[] cells = b.cells().clone();
        final SurveyRecall rc = a.recall(r[0], r[1], r[2], IRON);
        for (final VeinBook.Vein v : rc.veins()) {
            java.util.Arrays.fill(v.blocks(), 0);
        }
        assertTrue(java.util.Arrays.equals(cells, b.cells()));
        assertEquals(cells.length, a.recall(r[0], r[1], r[2], IRON).kept());
    }
}
