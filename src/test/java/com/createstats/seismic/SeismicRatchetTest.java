package com.createstats.seismic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicRatchetTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/create_stats/models/block/seismic_scanner");

    private static final double TOUCH = 0.01;

    @Test
    @DisplayName("winding through two whole teeth, the drawn pawl never passes through a drawn tooth")
    void neverThrough() {
        final List<double[][]> wheel = shapes("ratchet.json");
        final List<double[][]> pawl = shapes("pawl.json");
        final double eighth = 1.0 / SeismicGeometry.TEETH;
        for (double h = 0.0; h <= 2 * eighth; h += eighth / 400.0) {
            final double drum = SeismicGeometry.drumDegrees(h);
            final double lift = SeismicGeometry.PAWL_RIDE_DEG * SeismicGeometry.pawlRide(h);
            assertFalse(crosses(turned(pawl, lift, SeismicGeometry.PAWL_Y, SeismicGeometry.PAWL_Z),
                            turned(wheel, drum, SeismicGeometry.DRUM_Y, SeismicGeometry.DRUM_Z), TOUCH),
                    "the pawl's nose is inside a tooth at height " + h + " (drum " + drum + ", pawl " + lift + ")");
        }
    }

    @Test
    @DisplayName("at rest the nose sits against a steep face: the weight's pull, turning the drum back, is stopped")
    void itHolds() {
        final List<double[][]> wheel = shapes("ratchet.json");
        final List<double[][]> pawl = shapes("pawl.json");
        for (int k = 0; k < SeismicGeometry.TEETH; k++) {
            final double h = k / (double) SeismicGeometry.TEETH;

            assertTrue(crosses(turned(pawl, 0.0, SeismicGeometry.PAWL_Y, SeismicGeometry.PAWL_Z),
                            turned(wheel, SeismicGeometry.drumDegrees(h) + 2.0, SeismicGeometry.DRUM_Y, SeismicGeometry.DRUM_Z), 0.05),
                    "nothing stops the weight's pull at tooth " + k);
        }
    }

    @Test
    @DisplayName("the lever's lift clears every tooth at every turn, so the weight falls free")
    void theLeverFreesIt() {
        final List<double[][]> wheel = shapes("ratchet.json");
        final List<double[][]> pawl = turned(shapes("pawl.json"), SeismicGeometry.PAWL_LIFT_DEG,
                SeismicGeometry.PAWL_Y, SeismicGeometry.PAWL_Z);
        for (double d = 0.0; d < 360.0; d += 0.5) {
            assertFalse(crosses(pawl, turned(wheel, d, SeismicGeometry.DRUM_Y, SeismicGeometry.DRUM_Z), 0.0),
                    "a lifted pawl still meets a tooth with the drum at " + d);
        }
    }

    private static List<double[][]> shapes(String file) {
        final JsonObject model;
        try {
            model = JsonParser.parseString(Files.readString(MODELS.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final List<double[][]> out = new ArrayList<>();
        for (final JsonElement e : model.getAsJsonArray("elements")) {
            final JsonObject o = e.getAsJsonObject();
            final JsonArray f = o.getAsJsonArray("from");
            final JsonArray t = o.getAsJsonArray("to");
            final double y0 = f.get(1).getAsDouble();
            final double z0 = f.get(2).getAsDouble();
            final double y1 = t.get(1).getAsDouble();
            final double z1 = t.get(2).getAsDouble();
            double[][] q = {{y0, z0}, {y1, z0}, {y1, z1}, {y0, z1}};
            if (o.has("rotation")) {
                final JsonObject r = o.getAsJsonObject("rotation");
                if ("x".equals(r.get("axis").getAsString())) {
                    final JsonArray origin = r.getAsJsonArray("origin");
                    q = turn(q, r.get("angle").getAsDouble(), origin.get(1).getAsDouble(), origin.get(2).getAsDouble());
                }
            }
            out.add(q);
        }
        return out;
    }

    private static List<double[][]> turned(List<double[][]> shapes, double degrees, double oy, double oz) {
        final List<double[][]> out = new ArrayList<>();
        for (final double[][] q : shapes) {
            out.add(turn(q, degrees, oy, oz));
        }
        return out;
    }

    private static double[][] turn(double[][] q, double degrees, double oy, double oz) {
        final double a = Math.toRadians(degrees);
        final double c = Math.cos(a);
        final double s = Math.sin(a);
        final double[][] out = new double[q.length][];
        for (int i = 0; i < q.length; i++) {
            final double y = q[i][0] - oy;
            final double z = q[i][1] - oz;
            out[i] = new double[] {oy + y * c - z * s, oz + y * s + z * c};
        }
        return out;
    }

    private static boolean crosses(List<double[][]> a, List<double[][]> b, double depth) {
        for (final double[][] p : a) {
            for (final double[][] q : b) {
                if (overlap(p, q, depth)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlap(double[][] p, double[][] q, double depth) {
        for (final double[][] poly : new double[][][] {p, q}) {
            for (int i = 0; i < poly.length; i++) {
                final double[] a = poly[i];
                final double[] b = poly[(i + 1) % poly.length];
                final double ey = b[0] - a[0];
                final double ez = b[1] - a[1];
                final double n = Math.hypot(ey, ez);
                if (n < 1e-12) {
                    continue;
                }
                final double ay = -ez / n;
                final double az = ey / n;
                double pMin = Double.MAX_VALUE;
                double pMax = -Double.MAX_VALUE;
                double qMin = Double.MAX_VALUE;
                double qMax = -Double.MAX_VALUE;
                for (final double[] v : p) {
                    final double d = v[0] * ay + v[1] * az;
                    pMin = Math.min(pMin, d);
                    pMax = Math.max(pMax, d);
                }
                for (final double[] v : q) {
                    final double d = v[0] * ay + v[1] * az;
                    qMin = Math.min(qMin, d);
                    qMax = Math.max(qMax, d);
                }
                if (pMax < qMin + depth || qMax < pMin + depth) {
                    return false;
                }
            }
        }
        return true;
    }
}
