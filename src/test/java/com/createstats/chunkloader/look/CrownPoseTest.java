package com.createstats.chunkloader.look;

import com.createstats.chunkloader.core.CrownPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownPoseTest {
    private static String signature(int n) {
        final CrownPose p = CrownPose.settled(n);
        final CrownAnchors a = new CrownAnchors();
        final MeshProbe m = MeshProbe.of(p, a, CrownMesh.LOD_FULL);
        int latched = 0;
        for (int i = 0; i < CrownGeometry.MAX; i++) {
            latched += p.mount[i] > 0.5 ? 1 : 0;
        }
        return String.format(java.util.Locale.ROOT, "%d/%.3f/%.3f/%.3f/%d/%.3f/%.3f/%.3f/%d", latched, p.ring0Y,
                p.stemTop, p.hornTop, m.quads.size(), m.maxY, m.maxX - m.minX, p.yokeSwing, a.pods);
    }

    @Test
    @DisplayName("every count from 0 to 25 is a form of its own (the owner: 'it should be continous')")
    void everyCountIsAFormOfItsOwn() {
        final Set<String> seen = new HashSet<>();
        for (int n = 0; n <= CrownGeometry.MAX; n++) {
            assertTrue(seen.add(signature(n)), n + " chunks looks exactly like another count");
        }
    }

    @Test
    @DisplayName("the growth only goes one way: no part moves back as the count rises")
    void theGrowthOnlyGoesOneWay() {
        CrownPose prev = CrownPose.settled(0);
        for (int n = 1; n <= CrownGeometry.MAX; n++) {
            final CrownPose p = CrownPose.settled(n);
            assertTrue(p.ring0Y >= prev.ring0Y - 1e-9, "the first ring sank at " + n);
            assertTrue(p.stemTop >= prev.stemTop - 1e-9, "the stem shrank at " + n);
            assertTrue(p.hornTop >= prev.hornTop - 1e-9, "the horns folded at " + n);
            for (int t = 0; t < CrownGeometry.TIERS; t++) {
                assertTrue(p.tierUp[t] >= prev.tierUp[t] - 1e-9, "ring " + t + " sank at " + n);
            }
            for (int k = 0; k < CrownGeometry.MAX; k++) {
                assertTrue(p.mount[k] >= prev.mount[k] - 1e-9, "a sector went home at " + n);
            }
            assertEquals(n, count(p), "one more sector latched for one more chunk");
            prev = p;
        }
    }

    private static int count(CrownPose p) {
        int c = 0;
        for (double m : p.mount) {
            c += m >= 1.0 ? 1 : 0;
        }
        return c;
    }

    @Test
    @DisplayName("rings rise at 6, 11, 16 and 21, and the first ring is always there")
    void ringsRiseEveryFifthChunk() {
        for (int n = 0; n <= CrownGeometry.MAX; n++) {
            final CrownPose p = CrownPose.settled(n);
            int up = 0;
            for (double u : p.tierUp) {
                up += u > 0.5 ? 1 : 0;
            }
            assertEquals(n <= 5 ? 1 : n <= 10 ? 2 : n <= 15 ? 3 : n <= 20 ? 4 : 5, up, "rings at " + n);
        }
    }

    @Test
    @DisplayName("up to 5 chunks it is exactly one block, the item's 0 included")
    void upToFiveItIsOneBlock() {
        for (int n = 0; n <= 5; n++) {
            final MeshProbe m = MeshProbe.of(CrownPose.settled(n));
            assertNull(m.outside(CrownPlan.cellsFor(Math.max(1, n))), n + " chunks leaves its own block");
        }
        assertNull(MeshProbe.of(CrownPose.item()).outside(CrownPlan.cellsFor(1)), "the item leaves its block");
    }

    @Test
    @DisplayName("at every count the crown stays in the cells it claims: never past the 3 x 3 x 3")
    void itStaysInItsCells() {
        for (int n = 1; n <= CrownGeometry.MAX; n++) {
            final List<CrownPlan.Cell> cells = CrownPlan.cellsFor(n);
            final String out = MeshProbe.of(CrownPose.settled(n)).outside(cells);
            assertNull(out, n + " chunks reaches outside its claimed cells at " + out);
        }
    }

    @Test
    @DisplayName("at 25 the crown spans exactly 48 px across the horns and stays under 48 px high")
    void theUltimateSize() {
        final MeshProbe m = MeshProbe.of(CrownPose.settled(CrownGeometry.MAX));
        assertEquals(-16.0, m.minX, 1e-4, "the west horn's top knob stands on the 3 x 3's west edge");
        assertEquals(32.0, m.maxX, 1e-4, "the east horn's top knob stands on its east edge");
        assertTrue(m.maxY <= 48.0, "taller than three blocks: " + m.maxY);
        assertTrue(m.maxY > 47.0, "the locked yoke reaches near the top, as in the concept (47.7 px): " + m.maxY);
        assertTrue(m.minZ >= -16.0 && m.maxZ <= 32.0, "deeper than the 3 x 3");
    }

    @Test
    @DisplayName("the item is concept '0': the first ring folded in its well, five stowed sectors, the heart shut")
    void theItemIsTheFoldedCrown() {
        final CrownPose p = CrownPose.item();
        for (int k = 0; k < CrownGeometry.MAX; k++) {
            assertEquals(0.0, p.mount[k], 1e-12);
            assertEquals(CrownPose.INK_OFF, p.ink[k]);
        }
        assertEquals(1.0, p.tierUp[0], 1e-12, "the first ring is there, folded");
        assertEquals(CrownGeometry.RING0_LOW, p.ring0Y, 1e-12);
        assertEquals(0.0, p.capOpen, 1e-12, "the heart is shut in the claw");
        assertEquals(0.0, p.hatch, 1e-12, "the iris is closed");
        assertEquals(CrownPose.PILOT_OFF, p.pilot);
        assertEquals(0.0, p.store, 1e-12);
        final CrownAnchors a = new CrownAnchors();
        final MeshProbe m = MeshProbe.of(p, a, CrownMesh.LOD_FULL);
        assertEquals(0, a.pods, "no pod has grown on a stowed sector");
        long rose = m.quads.stream().filter(q -> q.material() == CrownMaterial.ROSE_0).count();
        assertTrue(rose >= 5 * 3 * 2, "the five stowed sectors show their unlit quartz (v17): " + rose);
        assertTrue(m.quads.stream().noneMatch(MeshProbe.Quad::glow), "nothing on the item glows");
    }
}
