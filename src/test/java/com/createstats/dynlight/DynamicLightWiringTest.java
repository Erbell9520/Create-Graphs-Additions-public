package com.createstats.dynlight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightWiringTest {
    private static final Path MIXIN_CONFIG = Path.of("src/main/resources/create_stats.mixins.json");
    private static final Path MODS_TOML = Path.of("src/main/resources/META-INF/neoforge.mods.toml");
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path STAGE_LIGHT = SOURCE_ROOT.resolve("com/createstats/blockentity/StageLightBE.java");
    private static final Path ENGINE = SOURCE_ROOT.resolve("com/createstats/client/dynlight/ClientDynamicLights.java");
    private static final Path VEHICLE_LIGHT = SOURCE_ROOT.resolve("com/createstats/vehiclelight/VehicleLightBE.java");
    private static final List<String> CLIENT_MIXINS =
            List.of("BlockLightEngineMixin", "LevelLightEngineAccessor", "ClientPacketListenerLightMixin");

    private static Set<String> names(JsonObject config, String list) {
        final Set<String> names = new HashSet<>();
        final JsonArray array = config.getAsJsonArray(list);
        if (array != null) {
            for (JsonElement e : array) {
                names.add(e.getAsString());
            }
        }
        return names;
    }

    static String code(Path file) throws IOException {
        return Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    @Test
    void theDynamicLightMixinsLoadOnTheClientOnly() throws IOException {
        final JsonObject config = JsonParser.parseString(Files.readString(MIXIN_CONFIG)).getAsJsonObject();
        final Set<String> common = names(config, "mixins");
        final Set<String> server = names(config, "server");
        final Set<String> client = names(config, "client");
        for (String mixin : CLIENT_MIXINS) {
            assertTrue(client.contains(mixin), mixin + " must be listed under client");
            assertFalse(common.contains(mixin), mixin + " under mixins would load on a dedicated server");
            assertFalse(server.contains(mixin), mixin + " has no business on a server");
        }
    }

    @Test
    void nothingAsksForLambDynamicLightsAnyMore() throws IOException {
        assertFalse(Files.readString(Path.of("build.gradle")).contains("lambdynamiclights"), "build.gradle");
        assertFalse(Files.readString(Path.of("gradle.properties")).contains("lambdynamiclights"), "gradle.properties");
        assertFalse(Files.readString(MODS_TOML).contains("lambdynlights"), "neoforge.mods.toml");
        final List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String code = code(file);
                if (code.contains("dev.lambdaurora") || code.contains("\"lambdynlights\"")) {
                    offenders.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), offenders, "code that still reaches for LambDynamicLights");
    }

    @Test
    void theStageLightReportsInAndTheEngineIsListening() throws IOException {
        final String stageLight = code(STAGE_LIGHT);
        assertTrue(stageLight.contains("ClientLightOwners.loaded(this)"), "StageLightBE.initialize on the client");
        assertTrue(stageLight.contains("ClientLightOwners.unloaded(this)"), "StageLightBE.invalidate on the client");
        final String install = installCall(code(ENGINE));
        assertTrue(install.contains("StageLightLights.loaded(be)"), "the engine's load hook tells the Stage Lights");
        assertTrue(install.contains("StageLightLights.unloaded(be)"), "and its unload hook");
        assertTrue(code(ENGINE).contains("StageLightLights::collect"), "and polls the Stage Lights it hears about");
    }

    @Test
    void theVehicleLightReportsInAndTheEngineIsListening() throws IOException {
        final String lamp = code(VEHICLE_LIGHT);
        assertTrue(lamp.contains("ClientLightOwners.loaded(this)"), "VehicleLightBE.onLoad on the client");
        assertTrue(lamp.contains("ClientLightOwners.unloaded(this)"), "VehicleLightBE.setRemoved on the client");
        final String install = installCall(code(ENGINE));
        assertTrue(install.contains("VehicleLightLights.loaded(be)"), "the engine's load hook tells the Vehicle Lights");
        assertTrue(install.contains("VehicleLightLights.unloaded(be)"), "and its unload hook");
        assertTrue(code(ENGINE).contains("VehicleLightLights::collect"), "and polls the Vehicle Lights it hears about");
    }

    private static String installCall(String engine) {
        final int start = engine.indexOf("ClientLightOwners.install(");
        assertTrue(start >= 0, "the engine installs the owner hooks");
        assertEquals(start, engine.lastIndexOf("ClientLightOwners.install("), "exactly once");
        int depth = 0;
        for (int i = start + "ClientLightOwners.install".length(); i < engine.length(); i++) {
            final char c = engine.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return engine.substring(start, i + 1);
            }
        }
        return engine.substring(start);
    }
}
