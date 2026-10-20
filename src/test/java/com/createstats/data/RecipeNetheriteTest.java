package com.createstats.data;

import com.google.gson.JsonElement;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeNetheriteTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/create_stats/recipe");

    @Test
    @DisplayName("no recipe under data/create_stats/recipe mentions netherite")
    void noRecipeMentionsNetherite() throws IOException {
        final List<Path> files = recipeFiles();
        assertFalse(files.isEmpty(), "no recipe files found at " + RECIPES.toAbsolutePath());
        final List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            final List<String> hits = new ArrayList<>();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                collect(JsonParser.parseReader(reader), "", hits);
            }
            for (String hit : hits) {
                offenders.add(RECIPES.relativize(file) + " " + hit);
            }
        }
        assertTrue(offenders.isEmpty(), "netherite in a recipe:\n" + String.join("\n", offenders));
    }

    static void collect(JsonElement element, String path, List<String> hits) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                collect(entry.getValue(), path + "/" + entry.getKey(), hits);
            }
        } else if (element.isJsonArray()) {
            int i = 0;
            for (JsonElement child : element.getAsJsonArray()) {
                collect(child, path + "[" + i++ + "]", hits);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            final String value = element.getAsString();
            if (value.toLowerCase(java.util.Locale.ROOT).contains("netherite")) {
                hits.add(path + " = " + value);
            }
        }
    }

    private static List<Path> recipeFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(RECIPES)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }
}
