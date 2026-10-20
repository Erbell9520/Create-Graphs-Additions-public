package com.createstats.book;

import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.scene.BlockView;
import com.createstats.book.specimen.BlockSpecimen;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockViewTest {
    private static final double TOLERANCE = 1.0e-3;

    @Test
    void theSceneIsDrawnWhereThePinsArePlaced() {
        final List<String> bad = new ArrayList<>();
        for (final BlockFigure f : BlockFigures.all()) {
            final BlockView v = f.view();
            final PoseStack pose = new PoseStack();
            BlockSpecimen.applyView(pose, v, 0.0f);
            final Matrix4f m = pose.last().pose();
            final List<double[]> points = new ArrayList<>();
            for (final BlockFigure.Pin pin : f.pins()) {
                points.add(new double[]{pin.x(), pin.y(), pin.z()});
            }
            for (final BlockFigure.Placement p : f.placements()) {
                final double[] b = p.sceneBox();
                for (int i = 0; i < 8; i++) {
                    points.add(new double[]{b[(i & 1) == 0 ? 0 : 3], b[(i & 2) == 0 ? 1 : 4], b[(i & 4) == 0 ? 2 : 5]});
                }
            }
            for (final double[] px : points) {
                final Vector3f g = m.transformPosition((float) (px[0] / 16.0), (float) (px[1] / 16.0),
                        (float) (px[2] / 16.0), new Vector3f());
                final double[] q = v.projectPx(px[0], px[1], px[2]);
                if (Math.abs(g.x() - q[0]) > TOLERANCE || Math.abs(g.y() - q[1]) > TOLERANCE) {
                    bad.add(f.id() + " (" + px[0] + ", " + px[1] + ", " + px[2] + "): pose " + g.x() + ", " + g.y()
                            + " but projected " + q[0] + ", " + q[1]);
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void theViewShowsTheFacesItIsTurnedTo() {
        final BlockView v = new BlockView(315.0, 30.0, 40.0, 0.0, 0.0);
        assertTrue(v.facesViewer(0, 1, 0), "the top");
        assertTrue(v.facesViewer(0, 0, 1), "south");
        assertTrue(v.facesViewer(1, 0, 0), "east");
        assertFalse(v.facesViewer(0, -1, 0), "the bottom");
        assertFalse(v.facesViewer(0, 0, -1), "north");
        assertFalse(v.facesViewer(-1, 0, 0), "west");

        assertTrue(v.projectPx(8, 8, 16)[0] < v.projectPx(16, 8, 8)[0]);

        assertTrue(v.projectPx(8, 16, 8)[1] < v.projectPx(8, 0, 8)[1]);
    }

    @Test
    void aFittedViewFillsItsPanelAndNoMore() {
        final List<double[]> boxes = List.of(new double[]{0, 0, 0, 16, 16, 16}, new double[]{24, 0, -24, 40, 8, -8});
        final int w = 200;
        final int h = 120;
        final int margin = 5;
        final BlockView v = BlockView.fit(315.0, 30.0, boxes, w, h, margin);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (final double[] b : boxes) {
            final double[] r = v.boundsPx(b);
            minX = Math.min(minX, r[0]);
            minY = Math.min(minY, r[1]);
            maxX = Math.max(maxX, r[2]);
            maxY = Math.max(maxY, r[3]);
        }
        assertTrue(minX >= margin - 1.0e-6 && maxX <= w - margin + 1.0e-6, "inside left and right");
        assertTrue(minY >= margin - 1.0e-6 && maxY <= h - margin + 1.0e-6, "inside top and bottom");
        final boolean tightX = Math.abs(maxX - minX - (w - 2 * margin)) < 1.0e-6;
        final boolean tightY = Math.abs(maxY - minY - (h - 2 * margin)) < 1.0e-6;
        assertTrue(tightX || tightY, "as large as fits");
        assertEquals(w / 2.0, (minX + maxX) / 2.0, 1.0e-6);
        assertEquals(h / 2.0, (minY + maxY) / 2.0, 1.0e-6);
    }
}
