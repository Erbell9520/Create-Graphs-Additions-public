package com.createstats.vccmk4.air.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CockpitShapeTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/create_stats/models/block");

    private static final double SLIVER = 0.15;

    private record El(double[] from, double[] to, int axis, double angle, double[] origin) {
    }

    private static List<El> elements(Path file) throws IOException {
        JsonObject m = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        List<El> out = new ArrayList<>();
        for (JsonElement je : m.getAsJsonArray("elements")) {
            JsonObject e = je.getAsJsonObject();
            int axis = -1;
            double angle = 0.0;
            double[] origin = {8, 8, 8};
            if (e.has("rotation")) {
                JsonObject r = e.getAsJsonObject("rotation");
                axis = "xyz".indexOf(r.get("axis").getAsString());
                angle = r.get("angle").getAsDouble();
                origin = arr(r.getAsJsonArray("origin"));
            }
            out.add(new El(arr(e.getAsJsonArray("from")), arr(e.getAsJsonArray("to")), axis, angle, origin));
        }
        return out;
    }

    private static double[] arr(JsonArray a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static double outside(El e, double[] p) {
        double[] q = p.clone();
        if (e.axis >= 0 && e.angle != 0.0) {
            double a = Math.toRadians(-e.angle);
            int i = (e.axis + 1) % 3;
            int j = (e.axis + 2) % 3;
            double u = p[i] - e.origin[i];
            double v = p[j] - e.origin[j];
            q[i] = e.origin[i] + u * Math.cos(a) - v * Math.sin(a);
            q[j] = e.origin[j] + u * Math.sin(a) + v * Math.cos(a);
        }
        double d = 0.0;
        for (int k = 0; k < 3; k++) d = Math.max(d, Math.max(e.from[k] - q[k], q[k] - e.to[k]));
        return d;
    }

    private static double outsideAll(List<El> els, double[] p) {
        double best = Double.MAX_VALUE;
        for (El e : els) best = Math.min(best, outside(e, p));
        return best;
    }

    private static List<String> proud(double[][] boxes, List<El> model, String what) {
        List<String> bad = new ArrayList<>();
        int n = 6;
        for (int b = 0; b < boxes.length; b++) {
            double[] box = boxes[b];
            double worst = 0.0;
            double[] at = null;
            for (int ix = 0; ix <= n; ix++) {
                for (int iy = 0; iy <= n; iy++) {
                    for (int iz = 0; iz <= n; iz++) {
                        double[] p = {
                                box[0] + 0.001 + (box[3] - box[0] - 0.002) * ix / n,
                                box[1] + 0.001 + (box[4] - box[1] - 0.002) * iy / n,
                                box[2] + 0.001 + (box[5] - box[2] - 0.002) * iz / n};
                        double d = outsideAll(model, p);
                        if (d > worst) {
                            worst = d;
                            at = p;
                        }
                    }
                }
            }
            if (worst > SLIVER) {
                bad.add(String.format("%s box %d stands %.3f outside the model at (%.2f, %.2f, %.2f)", what, b, worst,
                        at[0], at[1], at[2]));
            }
        }
        return bad;
    }

    @Test
    void theOneByOnesHitboxStaysInsideItsBody() throws IOException {
        List<String> bad = proud(CockpitShape.NORTH, elements(MODELS.resolve("vcc_mk4_aerial_cockpit/body.json")), "1x1");
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void theDecksTwoHalvesStayInsideTheirOwnHalvesOfTheBody() throws IOException {
        List<String> bad = new ArrayList<>();
        bad.addAll(proud(CockpitShape.LEFT, elements(MODELS.resolve("vcc_mk4_aerial_flight_deck/body_left.json")), "left"));
        bad.addAll(proud(CockpitShape.RIGHT, elements(MODELS.resolve("vcc_mk4_aerial_flight_deck/body_right.json")), "right"));
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void thePanelsTheGlareshieldAndTheQuadrantAreAllUnderTheHitbox() {
        double[][] touch = {
                {8.0, 12.4, 6.818 - 0.4142 * (12.4 - 9.682) - 0.3},
                {3.0, 8.0, 10.0 - (8.0 - 6.5) - 0.3},
                {8.0, 15.0, 5.0},
                {13.75, 7.2, 12.0},
                {8.0, 4.0, 9.8},
        };
        for (double[] p : touch) {
            boolean in = false;
            for (double[] b : CockpitShape.NORTH) {
                in |= p[0] >= b[0] && p[0] <= b[3] && p[1] >= b[1] && p[1] <= b[4] && p[2] >= b[2] && p[2] <= b[5];
            }
            assertTrue(in, String.format("(%.2f, %.2f, %.2f) is part of the console a click must reach", p[0], p[1], p[2]));
        }
    }

    @Test
    void everyBoxStaysInItsBlockAndFacingEastTheQuadrantIsOnThePilotsRightTheSouth() {
        for (double[][] set : new double[][][]{CockpitShape.NORTH, CockpitShape.LEFT, CockpitShape.RIGHT}) {
            for (double[] b : set) {
                for (int k = 0; k < 3; k++) {
                    assertTrue(b[k] >= 0.0 && b[k + 3] <= 16.0 && b[k] < b[k + 3], java.util.Arrays.toString(b));
                }
            }
        }
        double[] quadrant = CockpitShape.boxes(CockpitShape.NORTH, 1)[CockpitShape.NORTH.length - 1];
        assertTrue(quadrant[2] > 8.0, "facing east the pilot looks east, so their right hand is south: " + quadrant[2]);
    }
}
