package com.createstats.repeller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VolumeOutlineTest {
    private static final double HAIR = 1.0e-6;

    private static void onBoundary(RepellerShape shape, double r, List<double[]> lines) {
        for (double[] line : lines) {
            assertEquals(0, line.length % 3);
            for (int i = 0; i < line.length; i += 3) {
                final double x = line[i];
                final double y = line[i + 1];
                final double z = line[i + 2];
                assertTrue(shape.contains(0, 0, 0, r + HAIR, x, y, z), "drawn outside the volume");
                assertFalse(shape.contains(0, 0, 0, r - HAIR, x, y, z), "drawn inside the volume");
            }
        }
    }

    @Test
    void theSpherePreviewLiesOnTheSphereTheSpawnCheckUses() {
        for (int r = RepellerDraw.MIN_RADIUS; r <= RepellerDraw.MAX_RADIUS; r += RepellerDraw.RADIUS_STEP) {
            onBoundary(RepellerShape.SPHERE, r, VolumeOutline.sphere(r, 5, 8, 48));
        }
    }

    @Test
    void theColumnPreviewLiesOnTheColumnTheSpawnCheckUses() {
        for (int r = RepellerDraw.MIN_RADIUS; r <= RepellerDraw.MAX_RADIUS; r += RepellerDraw.RADIUS_STEP) {
            onBoundary(RepellerShape.COLUMN, r, VolumeOutline.column(r, -128.5, 255.5, 7, 8, 48));
        }
    }

    @Test
    void theShapesHaveTheirRingsAndLines() {
        assertEquals(5 + 8, VolumeOutline.sphere(16, 5, 8, 32).size());
        assertEquals(7 + 8, VolumeOutline.column(16, -10, 10, 7, 8, 32).size());
        final double[] ring = VolumeOutline.ring(10, 3, 16);
        assertEquals(ring[0], ring[ring.length - 3], 1e-12, "a ring closes on itself");
        assertEquals(ring[2], ring[ring.length - 1], 1e-12);
    }
}
