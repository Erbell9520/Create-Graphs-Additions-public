package com.createstats.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerOutletGridTest {
    private static final Path BLOCK_MODELS =
            Path.of("src/main/resources/assets/create_stats/models/block");
    private static final Path ITEM_MODELS =
            Path.of("src/main/resources/assets/create_stats/models/item");

    private static final List<String> BLOCKS = List.of(
            "power_outlet_single_dead", "power_outlet_single_live", "power_outlet_single_loaded",
            "power_outlet_duplex_dead", "power_outlet_duplex_live", "power_outlet_duplex_loaded");

    @Test
    void everyBoundAndEveryUvIsAWholeTexel() throws IOException {
        for (String name : BLOCKS) {
            for (JsonObject element : elements(BLOCK_MODELS.resolve(name + ".json"))) {
                assertIntegral(name, "from", element.getAsJsonArray("from"));
                assertIntegral(name, "to", element.getAsJsonArray("to"));
                for (Map.Entry<String, com.google.gson.JsonElement> face
                        : element.getAsJsonObject("faces").entrySet()) {
                    assertIntegral(name, "uv of " + face.getKey(),
                            face.getValue().getAsJsonObject().getAsJsonArray("uv"));
                }
            }
        }
    }

    @Test
    void onlyTheClipsLeaveTheGridAndOnlyInDepth() throws IOException {
        for (String name : BLOCKS) {
            for (JsonObject element : elements(BLOCK_MODELS.resolve(name + ".json"))) {
                final JsonArray to = element.getAsJsonArray("to");
                final double z = to.get(2).getAsDouble();
                if (z != Math.rint(z)) {
                    assertEquals(1.75, z, 1e-9, name + ": the only fractional depth is a clip's");
                }
            }
        }
    }

    @Test
    void theBoresAreWhereTheSchukoPutsThem() throws IOException {
        assertEquals(List.of(8.0), boreCentres("power_outlet_single_live"));
        assertEquals(List.of(4.0, 12.0), boreCentres("power_outlet_duplex_live"));
    }

    @Test
    void bothSocketsShareOneBoreTexture() throws IOException {
        for (String name : BLOCKS) {
            for (JsonObject element : elements(BLOCK_MODELS.resolve(name + ".json"))) {
                final JsonObject faces = element.getAsJsonObject("faces");
                if (faces.size() != 1 || !faces.has("south")) {
                    continue;
                }
                final JsonObject south = faces.getAsJsonObject("south");
                assertEquals("#dish", south.get("texture").getAsString());
                final JsonArray uv = south.getAsJsonArray("uv");
                for (int i = 0; i < 4; i++) {
                    final double v = uv.get(i).getAsDouble();
                    assertTrue(v >= 5.0 && v <= 11.0,
                            name + ": a bore floor sampling outside the canonical bore at " + v);
                }
            }
        }
    }

    @Test
    void theThreeLampStatesDifferOnlyInTheirPlateTexture() throws IOException {
        for (String base : List.of("power_outlet_single", "power_outlet_duplex")) {
            final String reference = geometryOf(base + "_dead");
            for (String lamp : List.of("live", "loaded")) {
                assertEquals(reference, geometryOf(base + "_" + lamp),
                        base + ": the " + lamp + " lamp changed the geometry, not just the plate");
            }
        }
    }

    @Test
    void theItemIconLooksAtTheSocketRatherThanAtItsBack() throws IOException {
        for (String name : List.of("power_outlet", "duplex_power_outlet")) {
            final JsonObject model = read(ITEM_MODELS.resolve(name + ".json"));
            final JsonArray rotation = model.getAsJsonObject("display").getAsJsonObject("gui")
                    .getAsJsonArray("rotation");
            assertEquals(45, rotation.get(1).getAsInt(), name + ": the icon is turned away from the socket");

            double zMin = 16.0;
            double zMax = 0.0;
            for (JsonObject element : elements(ITEM_MODELS.resolve(name + ".json"))) {
                zMin = Math.min(zMin, element.getAsJsonArray("from").get(2).getAsDouble());
                zMax = Math.max(zMax, element.getAsJsonArray("to").get(2).getAsDouble());
            }
            assertEquals(8.0, (zMin + zMax) / 2.0, 0.5, name + ": the item plate is off centre in z");
        }
    }

    private static String geometryOf(String name) throws IOException {
        final StringBuilder out = new StringBuilder();
        for (JsonObject element : elements(BLOCK_MODELS.resolve(name + ".json"))) {
            out.append(element.getAsJsonArray("from")).append(element.getAsJsonArray("to"));
            for (Map.Entry<String, com.google.gson.JsonElement> face
                    : element.getAsJsonObject("faces").entrySet()) {
                out.append(face.getKey()).append(face.getValue().getAsJsonObject().getAsJsonArray("uv"));
            }
        }
        return out.toString();
    }

    private static List<Double> boreCentres(String name) throws IOException {
        final java.util.TreeSet<Double> centres = new java.util.TreeSet<>();
        for (JsonObject element : elements(BLOCK_MODELS.resolve(name + ".json"))) {
            final JsonObject faces = element.getAsJsonObject("faces");
            if (faces.size() != 1 || !faces.has("south")) {
                continue;
            }
            final JsonArray from = element.getAsJsonArray("from");
            final JsonArray to = element.getAsJsonArray("to");

            if (to.get(0).getAsDouble() - from.get(0).getAsDouble() == 6.0) {
                centres.add((from.get(0).getAsDouble() + to.get(0).getAsDouble()) / 2.0);
            }
        }
        return List.copyOf(centres);
    }

    private static void assertIntegral(String model, String what, JsonArray values) {
        for (int i = 0; i < values.size(); i++) {
            final double v = values.get(i).getAsDouble();

            if ("from".equals(what) || "to".equals(what)) {
                if (i == 2 && v == 1.75) {
                    continue;
                }
            }
            assertEquals(Math.rint(v), v, 1e-9,
                    model + ": " + what + "[" + i + "] is " + v + ", which is not a texel edge");
        }
    }

    private static List<JsonObject> elements(Path path) throws IOException {
        final JsonArray array = read(path).getAsJsonArray("elements");
        final List<JsonObject> out = new java.util.ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            out.add(array.get(i).getAsJsonObject());
        }
        return out;
    }

    private static JsonObject read(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing model: " + path);
        return JsonParser.parseString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
