package com.createstats.repeller;

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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepellerFrequenciesDataTest {
    private static final Path DATA = Path.of("src/main/resources/data");

    private static String folder(String registryNamespace, String registryPath) {
        return ("minecraft".equals(registryNamespace) ? "" : registryNamespace + "/") + registryPath;
    }

    private static Path whereTheLoaderLooks() {
        final var id = RepellerFrequencies.DATA_MAP.id();
        final var registry = RepellerFrequencies.DATA_MAP.registryKey().location();
        return DATA.resolve(id.getNamespace()).resolve("data_maps")
                .resolve(folder(registry.getNamespace(), registry.getPath()))
                .resolve(id.getPath() + ".json");
    }

    private static Map<String, Double> shipped() throws IOException {
        final Path file = whereTheLoaderLooks();
        assertTrue(Files.exists(file), "the loader looks for the station table at " + file + " and it is not there");
        final JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        final Map<String, Double> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("values").entrySet()) {
            out.put(e.getKey(), e.getValue().getAsJsonObject().get("khz").getAsDouble());
        }
        return out;
    }

    @Test
    void theTableIsWhereTheLoaderLooks() throws IOException {
        assertEquals(Path.of("src/main/resources/data/create_stats/data_maps/entity_type/repeller_frequencies.json"),
                whereTheLoaderLooks());
        assertTrue(shipped().size() >= 50, "every naturally spawning vanilla mob is placed");
    }

    @Test
    void everyStationIsInTheBandAndClearOfItsNeighbours() throws IOException {
        final List<Double> khz = new ArrayList<>(shipped().values());
        khz.sort(Double::compare);
        for (int i = 0; i < khz.size(); i++) {
            final double f = khz.get(i);
            assertTrue(f >= 20.0 && f <= 65.0, f + " kHz is off the dial");
            assertEquals(f, Math.round(f * 10.0) / 10.0, 1e-9, "a station is written to one decimal");
            if (i > 0) {
                assertTrue(f - khz.get(i - 1) >= 0.75 - 1e-9,
                        "two stations " + khz.get(i - 1) + " and " + f + " are too close to tell apart on the dial");
            }
        }
    }

    @Test
    void theAnchorsTheDesignQuotesAreWhereItSays() throws IOException {
        final Map<String, Double> table = shipped();

        assertEquals(20.5, table.get("minecraft:ghast"));
        assertEquals(31.5, table.get("minecraft:zombie"));
        assertEquals(64.0, table.get("minecraft:bat"));
        for (Map.Entry<String, Double> e : table.entrySet()) {
            assertTrue(e.getKey().startsWith("minecraft:"), "the mod places vanilla only; packs place their own");
            assertTrue(e.getValue() >= table.get("minecraft:ghast") && e.getValue() <= table.get("minecraft:bat"));
        }
    }
}
