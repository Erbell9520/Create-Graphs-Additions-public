package com.createstats.seismic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VeinOutlineTest {
    private static int[] cells(int[]... c) {
        final int[] out = new int[c.length];
        for (int i = 0; i < c.length; i++) {
            out[i] = SurveyShells.pack(c[i][0], c[i][1], c[i][2]);
        }
        return out;
    }

    private static int length(int[] runs) {
        int sum = 0;
        for (int i = 4; i < runs.length; i += 5) {
            sum += runs[i];
        }
        return sum;
    }

    @Test
    @DisplayName("one block is a cube's twelve edges")
    void oneBlock() {
        final int[] runs = VeinOutline.edges(cells(new int[] {0, -3, 0}));
        assertEquals(12, VeinOutline.runCount(runs));
        assertEquals(12, length(runs));
    }

    @Test
    @DisplayName("two blocks side by side are one box: twelve runs, the four long ones merged, no seam between")
    void twoBlocks() {
        final int[] runs = VeinOutline.edges(cells(new int[] {0, -3, 0}, new int[] {1, -3, 0}));
        assertEquals(12, VeinOutline.runCount(runs));
        assertEquals(16, length(runs), "four edges of two along x, eight of one");
    }

    @Test
    @DisplayName("a two by two by two blob is a cube twice the size: twelve runs of two")
    void blob() {
        final int[] c = new int[8];
        int k = 0;
        for (int x = 0; x < 2; x++) {
            for (int y = -4; y < -2; y++) {
                for (int z = 0; z < 2; z++) {
                    c[k++] = SurveyShells.pack(x, y, z);
                }
            }
        }
        final int[] runs = VeinOutline.edges(c);
        assertEquals(12, VeinOutline.runCount(runs));
        assertEquals(24, length(runs));
    }

    @Test
    @DisplayName("an L of three blocks draws its inner corner too: the concave crease is an edge")
    void concave() {
        final int[] runs = VeinOutline.edges(cells(new int[] {0, -3, 0}, new int[] {1, -3, 0}, new int[] {0, -3, 1}));

        assertEquals(6 + 16, length(runs));
    }

    @Test
    @DisplayName("two blocks touching only along an edge still show that edge")
    void diagonalPair() {
        final int[] runs = VeinOutline.edges(cells(new int[] {0, -3, 0}, new int[] {1, -4, 0}));
        assertEquals(24 - 1, length(runs), "two cubes' 24 edges, the shared one drawn once");
    }

    @Test
    @DisplayName("runs are axis lines whose ends land on the grid, one per crease")
    void runsAreOnTheGrid() {
        final int[] runs = VeinOutline.edges(cells(new int[] {0, -3, 0}, new int[] {1, -3, 0}, new int[] {1, -2, 0}));
        final Set<String> unit = new HashSet<>();
        for (int i = 0; i < runs.length; i += 5) {
            assertTrue(runs[i + 3] >= 0 && runs[i + 3] <= 2);
            assertTrue(runs[i + 4] >= 1);
            for (int s = 0; s < runs[i + 4]; s++) {
                final int[] p = {runs[i], runs[i + 1], runs[i + 2]};
                p[runs[i + 3]] += s;
                assertTrue(unit.add(runs[i + 3] + ":" + p[0] + "," + p[1] + "," + p[2]), "a unit edge drawn twice");
            }
        }
    }

    @Test
    @DisplayName("cells: an ore vein is drawn block for block, a great void at a coarser grain")
    void coarseCells() {
        assertEquals(1, VeinOutline.cellFor(9));
        assertEquals(1, VeinOutline.cellFor(VeinOutline.FINE_LIMIT));
        assertEquals(2, VeinOutline.cellFor(VeinOutline.FINE_LIMIT + 1));
        assertEquals(4, VeinOutline.cellFor(30_000));
        final int[] blocks = new int[64];
        int k = 0;
        for (int x = 0; x < 4; x++) {
            for (int y = -4; y < 0; y++) {
                for (int z = 0; z < 4; z++) {
                    blocks[k++] = SurveyShells.pack(x, y, z);
                }
            }
        }
        assertEquals(64, VeinOutline.cells(blocks, 1).length);
        assertEquals(8, VeinOutline.cells(blocks, 2).length);
        assertEquals(1, VeinOutline.cells(blocks, 4).length);
        assertEquals(12, VeinOutline.runCount(VeinOutline.edges(VeinOutline.cells(blocks, 4))));
    }
}
