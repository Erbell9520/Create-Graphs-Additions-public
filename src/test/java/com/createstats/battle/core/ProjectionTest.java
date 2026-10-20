package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {
    @Test
    void aShipThatFitsGetsOneBlockACell() {
        Projection.Frame f = Projection.frame(-10, 29, -4, 4, -3, 12, 256, 128);
        assertEquals(1, f.k());
        assertEquals(40, f.planW());
        assertEquals(9, f.planH());
        assertEquals(16, f.profileH());
        assertEquals(0, f.x(-10));
        assertEquals(39, f.x(29));
        assertEquals(0, f.profileY(12), "the top of the ship is the first profile row");
        assertEquals(15, f.profileY(-3));
    }

    @Test
    void aLongShipIsScaledTheSameInBothViews() {
        Projection.Frame f = Projection.frame(0, 599, 0, 40, 0, 300, 256, 128);
        assertEquals(3, f.k(), "600 long needs 3 blocks a cell; 301 tall also needs 3");
        assertTrue(f.planW() <= 256 && f.profileH() <= 128);
    }

    @Test
    void theWorstStateWinsACell() {
        byte[] cells = new byte[4];
        Projection.put(cells, 2, 1, 1, Projection.STRUCTURE);
        Projection.put(cells, 2, 1, 1, Projection.LIFT);
        assertEquals(Projection.STRUCTURE, cells[3], "lift does not cover structure");
        Projection.put(cells, 2, 1, 1, Projection.LOST);
        assertEquals(Projection.LOST, cells[3], "a lost block shows through the hull above it");
        byte[] pooled = Projection.pool(new byte[]{0, 1, 2, 0, 0, 0, 5, 0}, 4, 2, 2);
        assertArrayEquals(new byte[]{1, 5}, pooled);
    }

    @Test
    void theFaceKeepsTheShapeCentredAndReadsRecentAsLost() {
        byte[] plan = new byte[6 * 3];
        for (int i = 0; i < plan.length; i++) plan[i] = Projection.STRUCTURE;
        plan[4] = Projection.RECENT;
        byte[] face = Projection.face(plan, 6, 3);
        int lost = 0;
        int structure = 0;
        for (byte b : face) {
            if (b == Projection.LOST) lost++;
            if (b == Projection.STRUCTURE) structure++;
        }
        assertEquals(64, lost, "6 by 3 enlarged 8 times: one cell becomes 8 by 8");
        assertEquals(17 * 64, structure);
        assertEquals(Projection.EMPTY, face[0], "48 wide in 64, centred: the corner stays empty");
        assertEquals(Projection.LOST, face[8 + 4 * 8], "the recent loss sits where its cell landed");
        byte[] big = new byte[300 * 30];
        big[0] = Projection.BURNING;
        byte[] shrunk = Projection.face(big, 300, 30);
        assertEquals(Projection.LOST, shrunk[(24 - 6) / 2 * 64 + (64 - 60) / 2], "pooled by 5, burning reads as lost");
    }

    @Test
    void twoBitPackingRoundTrips() {
        byte[] cells = {0, 1, 2, 3, 3, 2, 1};
        byte[] packed = Projection.pack2(cells);
        assertEquals(2, packed.length);
        assertArrayEquals(cells, Projection.unpack2(packed, cells.length));
        assertEquals(384, Projection.pack2(new byte[Projection.FACE_W * Projection.FACE_H]).length, "the face is 384 bytes");
    }
}
