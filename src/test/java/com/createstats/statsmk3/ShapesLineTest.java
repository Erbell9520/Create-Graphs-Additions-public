package com.createstats.statsmk3;

import com.createstats.statsmk3.ui.Shapes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShapesLineTest {
    private static final int OPAQUE = 0xFFFFFFFF;
    private static final int CLEAR = 0x00FFFFFF;

    @Test
    void lineEmitsCoreAndTwoFeatherQuads() {
        final CountingSurface surface = new CountingSurface();

        Shapes.line(surface, 1.0f, 2.0f, 7.0f, 5.0f, 2.0f, OPAQUE);

        assertEquals(6, surface.triangles);
    }

    @Test
    void invisibleAndDegenerateLinesEmitNothing() {
        final CountingSurface surface = new CountingSurface();

        Shapes.line(surface, 1.0f, 2.0f, 1.0f, 2.0f, 2.0f, OPAQUE);
        Shapes.line(surface, 1.0f, 2.0f, 7.0f, 5.0f, 2.0f, CLEAR);

        assertEquals(0, surface.triangles);
    }

    @Test
    void hairlinePolylineUsesOnlySegmentGeometry() {
        final CountingSurface surface = new CountingSurface();
        final float[] points = {0.0f, 0.0f, 4.0f, 2.0f, 8.0f, 0.0f};

        Shapes.polyline(surface, points, 3, 1.6f, OPAQUE);

        assertEquals(12, surface.triangles);
    }

    @Test
    void thickPolylineAddsTwoBevelTrianglesPerValidJoin() {
        final CountingSurface surface = new CountingSurface();
        final float[] points = {0.0f, 0.0f, 4.0f, 2.0f, 8.0f, 0.0f};

        Shapes.polyline(surface, points, 3, 2.0f, OPAQUE);

        assertEquals(14, surface.triangles);
    }

    @Test
    void degeneratePolylineSegmentDoesNotCreateABevel() {
        final CountingSurface surface = new CountingSurface();
        final float[] points = {0.0f, 0.0f, 0.0f, 0.0f, 8.0f, 0.0f};

        Shapes.polyline(surface, points, 3, 2.0f, OPAQUE);

        assertEquals(6, surface.triangles);
    }
}
