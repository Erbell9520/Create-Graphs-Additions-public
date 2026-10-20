package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinBookTest {
    private static void feed(VeinBook book, int[][] blocks, int kind) {
        final List<int[]> sorted = new ArrayList<>(Arrays.asList(blocks));
        sorted.sort(Comparator.comparingDouble(b -> SurveyShells.distanceOf(b[0], b[1], b[2])));
        for (final int[] b : sorted) {
            assertTrue(book.add(SurveyShells.pack(b[0], b[1], b[2]), (float) SurveyShells.distanceOf(b[0], b[1], b[2]), kind));
        }
    }

    @Test
    @DisplayName("ore joins across faces, edges and corners: a diagonal pair is one vein")
    void diagonalOre() {
        final VeinBook book = new VeinBook(64, true);
        feed(book, new int[][] {{5, -5, 5}, {6, -6, 6}, {7, -7, 7}}, 0);
        final List<VeinBook.Vein> veins = book.closeAll();
        assertEquals(1, veins.size());
        assertEquals(3, veins.get(0).count());
    }

    @Test
    @DisplayName("air and water join across faces only: the same diagonal pair is three pockets")
    void faceOnlyForVoids() {
        final VeinBook book = new VeinBook(64, false);
        feed(book, new int[][] {{5, -5, 5}, {6, -6, 6}, {7, -7, 7}}, 0);
        assertEquals(3, book.closeAll().size());
    }

    @Test
    @DisplayName("lava never joins water, however they touch")
    void kindsNeverMerge() {
        final VeinBook book = new VeinBook(64, false);
        book.add(SurveyShells.pack(3, -3, 0), (float) SurveyShells.distanceOf(3, -3, 0), SeismicCatalog.KIND_LAVA);
        book.add(SurveyShells.pack(4, -3, 0), (float) SurveyShells.distanceOf(4, -3, 0), SeismicCatalog.KIND_WATER);
        final List<VeinBook.Vein> veins = book.closeAll();
        assertEquals(2, veins.size());
        assertTrue(veins.stream().anyMatch(v -> v.kind() == SeismicCatalog.KIND_LAVA));
        assertTrue(veins.stream().anyMatch(v -> v.kind() == SeismicCatalog.KIND_WATER));
    }

    @Test
    @DisplayName("a vein closes only once the front is a corner's reach past its farthest block")
    void closesWhenNothingCanJoin() {
        final VeinBook book = new VeinBook(64, true);
        final int[][] blob = {{0, -10, 0}, {1, -10, 0}, {0, -11, 0}};
        feed(book, blob, 0);
        double far = 0;
        for (final int[] b : blob) {
            far = Math.max(far, SurveyShells.distanceOf(b[0], b[1], b[2]));
        }
        assertTrue(book.close(far + VeinBook.MARGIN - 0.01).isEmpty(), "closed while a neighbour could still join");
        final List<VeinBook.Vein> closed = book.close(far + VeinBook.MARGIN);
        assertEquals(1, closed.size());
        assertTrue(book.close(1000).isEmpty(), "a vein closes once");
        assertTrue(book.closeAll().isEmpty());
    }

    @Test
    @DisplayName("a vein reports its count, its nearest block (its echo) and its centre")
    void veinFacts() {
        final VeinBook book = new VeinBook(64, true);
        feed(book, new int[][] {{2, -4, 2}, {3, -4, 2}, {2, -4, 3}, {3, -4, 3}}, 0);
        final VeinBook.Vein v = book.closeAll().get(0);
        assertEquals(4, v.count());
        assertEquals((float) SurveyShells.distanceOf(2, -4, 2), v.nearest(), 1e-6f);
        assertEquals((float) SurveyShells.distanceOf(3, -4, 3), v.farthest(), 1e-6f);
        assertEquals(3.0, v.cx(), 1e-9, "the mean block centre: x 2.5 and 3.5 average to 3");
        assertEquals(-3.5, v.cy(), 1e-9);
        assertEquals(3.0, v.cz(), 1e-9);
        assertEquals(SeismicWave.echoTick(v.nearest()), v.echoTick());
        assertEquals(4, v.blocks().length);
    }

    @Test
    @DisplayName("a long vein built from both ends merges into one, and its blocks are all handed back")
    void mergeFromBothEnds() {
        final VeinBook book = new VeinBook(256, true);
        final List<int[]> row = new ArrayList<>();
        for (int x = -20; x <= 20; x++) {
            row.add(new int[] {x, -12, 0});
        }
        feed(book, row.toArray(new int[0][]), 0);
        final List<VeinBook.Vein> veins = book.closeAll();
        assertEquals(1, veins.size(), "a row reached from its middle outward is one vein");
        assertEquals(41, veins.get(0).count());
        assertEquals(41, Arrays.stream(veins.get(0).blocks()).distinct().count());
    }

    @Test
    @DisplayName("a full book refuses more and says so; what it kept is the nearest")
    void capacity() {
        final VeinBook book = new VeinBook(3, true);
        assertTrue(book.add(SurveyShells.pack(0, -2, 0), 1.5f, 0));
        assertTrue(book.add(SurveyShells.pack(10, -2, 0), 10f, 0));
        assertTrue(book.add(SurveyShells.pack(20, -2, 0), 20f, 0));
        assertFalse(book.full());
        assertFalse(book.add(SurveyShells.pack(30, -2, 0), 30f, 0));
        assertTrue(book.full());
        assertEquals(3, book.stored());
        assertEquals(3, book.closeAll().size());
    }
}
