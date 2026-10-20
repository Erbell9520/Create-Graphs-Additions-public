package com.createstats.book;

import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.scene.BlockView;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.book.specimen.Specimens;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockFigureTest {
    @Test
    void everyPinIsOnAFaceTheViewShows() {
        final List<String> bad = new ArrayList<>();
        for (final BlockFigure f : BlockFigures.all()) {
            final BlockView v = f.view();
            for (final BlockFigure.Pin pin : f.pins()) {
                if (!v.facesViewer(pin.face()[0], pin.face()[1], pin.face()[2])) {
                    bad.add(f.id() + " " + pin.id() + ": its face is turned away from the view");
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void everyPinPointsIntoItsOwnPart() {
        final List<String> bad = new ArrayList<>();
        for (final BlockFigure f : BlockFigures.all()) {
            final SpecimenAnatomy a = f.anatomy();
            for (final SpecimenAnatomy.Hot h : a.controls()) {
                if (h.anchorX() < 0 || h.anchorY() < 0 || h.anchorX() >= a.width() || h.anchorY() >= a.height()) {
                    bad.add(f.id() + " " + h.id() + ": its point (" + h.anchorX() + ", " + h.anchorY()
                            + ") is off the panel");
                }

                if (h.anchorX() < h.x() - 1 || h.anchorX() > h.x() + h.w() || h.anchorY() < h.y() - 1
                        || h.anchorY() > h.y() + h.h()) {
                    bad.add(f.id() + " " + h.id() + ": its point is outside its part's rect " + h);
                }
                if (!h.point()) {
                    bad.add(f.id() + " " + h.id() + ": a block's leader must end on its point");
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void everyPlacementFitsItsPanel() {
        final List<String> bad = new ArrayList<>();
        for (final BlockFigure f : BlockFigures.all()) {
            final BlockView v = f.view();
            for (final BlockFigure.Placement p : f.placements()) {
                final double[] r = v.boundsPx(p.sceneBox());
                final String at = f.id() + " " + p.block() + " at (" + p.x() + ", " + p.y() + ", " + p.z() + ")";
                if (!f.framing().fitted()) {
                    final boolean inside = r[0] >= -0.01 && r[1] >= -0.01 && r[2] <= f.width() + 0.01
                            && r[3] <= f.height() + 0.01;
                    final boolean beyond = r[2] <= 0.01 || r[3] <= 0.01 || r[0] >= f.width() - 0.01
                            || r[1] >= f.height() - 0.01;
                    if (!inside && !beyond) {
                        bad.add(at + " is cut by the panel's edge");
                    }
                    continue;
                }
                if (r[0] < f.margin() - 0.01 || r[1] < f.margin() - 0.01 || r[2] > f.width() - f.margin() + 0.01
                        || r[3] > f.height() - f.margin() + 0.01) {
                    bad.add(at + " reaches past the margin");
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void everyBlockOfOursIsRegistered() throws IOException {
        final Set<String> registered = new HashSet<>(BookCoverageTest.registryIds().values());
        final List<String> bad = new ArrayList<>();
        for (final BlockFigure f : BlockFigures.all()) {
            for (final BlockFigure.Placement p : f.placements()) {
                if (p.block().startsWith("create_stats:") && !registered.contains(p.block())) {
                    bad.add(f.id() + ": " + p.block() + " is not registered");
                }
            }
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void theBookAndThePreviewerReadTheFigureItself() {
        for (final BlockFigure f : BlockFigures.all()) {
            final SpecimenAnatomy a = Specimens.anatomy(f.id()).orElseThrow();
            assertEquals(f.anatomy(), a, f.id());
            assertTrue(a.block(), f.id() + " is drawn as blocks");
            final JsonObject spec = JsonParser.parseString(f.toJson()).getAsJsonObject();
            final JsonObject view = spec.getAsJsonObject("view");
            final BlockView v = f.view();
            for (final Map.Entry<String, Double> e : Map.of("yaw", v.yaw(), "pitch", v.pitch(), "scale", v.scale(),
                    "offsetX", v.offsetX(), "offsetY", v.offsetY()).entrySet()) {
                assertEquals(e.getValue(), view.get(e.getKey()).getAsDouble(), 1.0e-5, f.id() + " " + e.getKey());
            }
            assertEquals(f.placements().size(), spec.getAsJsonArray("placements").size(), f.id());
        }
    }
}
