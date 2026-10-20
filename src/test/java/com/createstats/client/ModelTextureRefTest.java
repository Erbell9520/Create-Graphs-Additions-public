package com.createstats.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTextureRefTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/create_stats/models");
    private static final Path TEXTURES = Path.of("src/main/resources/assets/create_stats/textures");

    private static List<Path> modelFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(MODELS)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static JsonObject textures(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            reader.mark(1);
            final int first = reader.read();
            if (first != 0xFEFF) {
                reader.reset();
            }
            final JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return new JsonObject();
            }
            final JsonElement block = root.getAsJsonObject().get("textures");
            return block != null && block.isJsonObject() ? block.getAsJsonObject() : new JsonObject();
        }
    }

    @Test
    @DisplayName("no model has an empty texture value, which crashes vanilla's parser")
    void noEmptyTextureValues() throws IOException {
        final List<String> bad = new ArrayList<>();
        int scanned = 0;
        for (Path file : modelFiles()) {
            scanned++;
            for (Map.Entry<String, JsonElement> entry : textures(file).entrySet()) {
                final JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsString().isEmpty()) {
                    bad.add(MODELS.relativize(file) + " -> \"" + entry.getKey() + "\": \"\"");
                }
            }
        }

        assertTrue(scanned >= 500, "only " + scanned + " models scanned; the walk has stopped working");
        assertTrue(bad.isEmpty(),
                "empty texture value(s). Vanilla calls charAt(0) on these while parsing, so the whole "
                        + "model fails to load and the block renders as the magenta missing-model cube:\n  "
                        + String.join("\n  ", bad));
    }

    @Test
    @DisplayName("no texture reference points at itself")
    void noSelfReferencingTextures() throws IOException {
        final List<String> bad = new ArrayList<>();
        for (Path file : modelFiles()) {
            for (Map.Entry<String, JsonElement> entry : textures(file).entrySet()) {
                final JsonElement value = entry.getValue();
                if (value.isJsonPrimitive() && value.getAsString().equals("#" + entry.getKey())) {
                    bad.add(MODELS.relativize(file) + " -> \"" + entry.getKey() + "\": \"#"
                            + entry.getKey() + "\"");
                }
            }
        }
        assertTrue(bad.isEmpty(),
                "self-referencing texture(s). Vanilla detects the loop and substitutes the missing "
                        + "texture, so these render as checkerboard for ever after one warning at load:\n  "
                        + String.join("\n  ", bad));
    }

    @Test
    @DisplayName("every texture this mod names in its own namespace exists on disk")
    void ourTexturesExist() throws IOException {
        final List<String> bad = new ArrayList<>();
        for (Path file : modelFiles()) {
            for (Map.Entry<String, JsonElement> entry : textures(file).entrySet()) {
                final JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) {
                    continue;
                }
                final String texture = value.getAsString();

                if (!texture.startsWith("create_stats:")) {
                    continue;
                }
                final Path png = TEXTURES.resolve(texture.substring("create_stats:".length()) + ".png");
                if (!Files.exists(png)) {
                    bad.add(MODELS.relativize(file) + " -> \"" + entry.getKey() + "\": \"" + texture + "\"");
                }
            }
        }
        assertTrue(bad.isEmpty(),
                "texture(s) named in our own namespace with no PNG behind them:\n  "
                        + String.join("\n  ", bad));
    }
}
