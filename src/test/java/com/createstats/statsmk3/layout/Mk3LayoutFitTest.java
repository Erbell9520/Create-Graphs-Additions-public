package com.createstats.statsmk3.layout;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Mk3LayoutFitTest {
    @Test
    void theSameGridLosesNothing() {
        final Mk3Layout quadrants = quadrants();
        assertEquals(0, quadrants.tilesLostFitting(8, 8));
    }

    @Test
    void halvingAQuadrantFaceKeepsAllFourAndASingleCellKeepsOne() {
        final Mk3Layout quadrants = quadrants();

        assertEquals(0, quadrants.tilesLostFitting(2, 2));

        assertEquals(3, quadrants.tilesLostFitting(1, 1));
    }

    @Test
    void aConsoleFaceOnASingleBlockWallFitsAndOnOneCellLosesTwo() {
        final Mk3Layout console = Mk3Layout.onGrid(5, 4);
        console.add(new Tile(0, 0, 2, 4));
        console.add(new Tile(2, 0, 3, 2));
        console.add(new Tile(2, 2, 3, 2));
        assertEquals(3, console.size());

        assertEquals(0, console.tilesLostFitting(GridSpec.colsFor(1), GridSpec.rowsFor(1)));

        assertEquals(0, console.tilesLostFitting(GridSpec.colsFor(2), GridSpec.rowsFor(1)));

        assertEquals(2, console.tilesLostFitting(1, 1));
    }

    @Test
    void theProbeLeavesTheLayoutItWasAskedAboutUntouched() {
        final Mk3Layout quadrants = quadrants();
        quadrants.tilesLostFitting(1, 1);
        assertEquals(8, quadrants.cols());
        assertEquals(8, quadrants.rows());
        assertEquals(4, quadrants.size());
        assertEquals(4, quadrants.get(3).gx);
        assertEquals(4, quadrants.get(3).gw);
    }

    @Test
    void growingNeverLosesATile() {
        final Random random = new Random(20261001L);
        int checked = 0;
        for (int round = 0; round < 400; round++) {
            final int cols = 1 + random.nextInt(16);
            final int rows = 1 + random.nextInt(12);
            final Mk3Layout layout = Mk3Layout.onGrid(cols, rows);
            for (int attempt = 0; attempt < 40 && layout.size() < GridSpec.MAX_TILES; attempt++) {
                final int gx = random.nextInt(cols);
                final int gy = random.nextInt(rows);
                final int gw = 1 + random.nextInt(cols - gx);
                final int gh = 1 + random.nextInt(rows - gy);
                layout.add(new Tile(gx, gy, gw, gh));
            }
            final List<int[]> targets = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                targets.add(new int[]{cols + random.nextInt(GridSpec.MAX_COLS - cols + 1),
                        rows + random.nextInt(GridSpec.MAX_ROWS - rows + 1)});
            }
            for (final int[] target : targets) {
                assertEquals(0, layout.tilesLostFitting(target[0], target[1]),
                        () -> "a " + cols + " by " + rows + " layout of " + layout.size() + " tiles lost tiles"
                                + " growing to " + target[0] + " by " + target[1]);
                checked++;
            }
        }
        assertEquals(2400, checked);
    }

    private static Mk3Layout quadrants() {
        final Mk3Layout layout = Mk3Layout.onGrid(8, 8);
        layout.add(new Tile(0, 0, 4, 4));
        layout.add(new Tile(4, 0, 4, 4));
        layout.add(new Tile(0, 4, 4, 4));
        layout.add(new Tile(4, 4, 4, 4));
        assertEquals(4, layout.size());
        return layout;
    }
}
