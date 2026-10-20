package com.createstats.vccmk4.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleShapeTest {
    private static final Path MODEL = Path.of("src/main/resources/assets/create_stats/models/block/bta_g1.json");
    private static final double EPS = 1.0e-9;

    private static double[] gameTurn(double x, double z, int turns) {
        double a = Math.toRadians(-90.0 * turns);
        double dx = x - 8.0, dz = z - 8.0;
        double rx = dx * Math.cos(a) + dz * Math.sin(a);
        double rz = -dx * Math.sin(a) + dz * Math.cos(a);
        return new double[]{Math.round((rx + 8.0) * 1.0e6) / 1.0e6, Math.round((rz + 8.0) * 1.0e6) / 1.0e6};
    }

    @Test
    void everyFacingIsTheTurnTheBlockstateGivesTheModel() {
        for (int turns = 0; turns < 4; turns++) {
            for (double[] b : ConsoleShape.NORTH) {
                double[] p = gameTurn(b[0], b[2], turns);
                double[] q = gameTurn(b[3], b[5], turns);
                double[] want = {Math.min(p[0], q[0]), b[1], Math.min(p[1], q[1]), Math.max(p[0], q[0]), b[4], Math.max(p[1], q[1])};
                assertArrayEquals(want, ConsoleShape.turn(b, turns), EPS, "turns " + turns);
            }
        }
    }

    @Test
    void fourQuarterTurnsAreNoneAndNoTurnChangesNothing() {
        for (double[] b : ConsoleShape.NORTH) {
            assertArrayEquals(b, ConsoleShape.turn(b, 0), EPS);
            assertArrayEquals(b, ConsoleShape.turn(b, 4), EPS);
            assertArrayEquals(ConsoleShape.turn(b, 1), ConsoleShape.turn(b, -3), EPS);
        }
    }

    @Test
    void theTowerStandsOnTheSideTheConsoleFaces() {
        String[] facing = {"north", "east", "south", "west"};
        for (int turns = 0; turns < 4; turns++) {
            double[] tower = null;
            for (double[] b : ConsoleShape.boxes(turns)) if (b[4] >= 16.0 - EPS) tower = b;
            assertNotNull(tower, "no box reaches the top of the block");
            boolean onNose = switch (turns) {
                case 0 -> tower[5] <= 8.0;
                case 1 -> tower[0] >= 8.0;
                case 2 -> tower[2] >= 8.0;
                default -> tower[3] <= 8.0;
            };
            assertTrue(onNose, "facing " + facing[turns] + ": the tower is not on the nose side");
        }
    }

    @Test
    void theBoxesAreTheModelsBaseTowerAndCheekSteps() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(MODEL, StandardCharsets.UTF_8)).getAsJsonObject();
        double[] brow = box(model, "brow");
        double[] tower = ConsoleShape.NORTH[1];
        assertEquals(16.0, tower[4], EPS, "the tower box reaches the brow's top");
        assertTrue(brow[0] >= tower[0] && brow[3] <= tower[3] && brow[2] >= tower[2] && brow[5] <= tower[5] && brow[4] <= tower[4],
                "the brow, the console's highest part, lies inside the tower box");
        assertEquals(box(model, "deck")[4], ConsoleShape.NORTH[0][4], EPS, "the base box ends at the deck's top");

        for (int k = 1; k <= 3; k++) {
            double[] cheek = box(model, "cheek_west_" + k);
            double[] step = ConsoleShape.NORTH[k + 1];
            assertEquals(cheek[2], step[2], EPS, "step " + k + " starts where its cheek does");
            assertEquals(cheek[5], step[5], EPS, "step " + k + " ends where its cheek does");
            assertEquals(cheek[4], step[4], EPS, "step " + k + " is as tall as its cheek");
        }
    }

    private static double[] box(JsonObject model, String name) {
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject o = e.getAsJsonObject();
            if (!o.get("name").getAsString().equals(name)) continue;
            double[] out = new double[6];
            for (int i = 0; i < 3; i++) {
                out[i] = o.getAsJsonArray("from").get(i).getAsDouble();
                out[i + 3] = o.getAsJsonArray("to").get(i).getAsDouble();
            }
            return out;
        }
        throw new AssertionError("the model has no element " + name);
    }
}
