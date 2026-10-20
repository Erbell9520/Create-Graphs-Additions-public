package com.createstats.registry;

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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableCoverageTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path LOOT = Path.of("src/main/resources/data/create_stats/loot_table/blocks");

    private static final Pattern REGISTRATION = Pattern.compile("registrate\\(\\)\\s*\\.block\\(\\s*\"([a-z0-9_]+)\"");
    private static final Pattern LANG_NAME = Pattern.compile("\"block\\.create_stats\\.([a-z0-9_]+)\"\\s*:");

    @Test
    @DisplayName("every registered block has a loot table, unless it is registered with noLootTable()")
    void everyBlockDrops() {
        final Set<String> exempt = new TreeSet<>();
        final Set<String> blocks = new TreeSet<>();
        for (Path file : javaFiles()) {
            final String src = read(file);
            final Matcher m = REGISTRATION.matcher(src);
            while (m.find()) {
                final int end = src.indexOf(';', m.end());
                final String statement = src.substring(m.start(), end < 0 ? src.length() : end);
                (statement.contains("noLootTable()") ? exempt : blocks).add(m.group(1));
            }
        }
        assertTrue(blocks.size() > 100, "the registration scan found only " + blocks.size() + " blocks");

        final Matcher lang = LANG_NAME.matcher(read(ASSETS.resolve("lang/en_us.json")));
        int fromLang = 0;
        while (lang.find()) {
            final String id = lang.group(1);
            if (!exempt.contains(id) && Files.isRegularFile(ASSETS.resolve("blockstates/" + id + ".json"))) {
                fromLang += blocks.add(id) ? 1 : 0;
            }
        }

        assertTrue(fromLang >= 6, "the lang scan added only " + fromLang + " loop-registered blocks");

        final List<String> missing = new ArrayList<>();
        for (String id : blocks) {
            final Path table = LOOT.resolve(id + ".json");
            if (!Files.isRegularFile(table)) {
                missing.add(id);
                continue;
            }

            final JsonObject json = JsonParser.parseString(read(table)).getAsJsonObject();
            assertTrue(json.has("pools"), id + ".json has no pools array, so the game rejects it");
        }
        assertTrue(missing.isEmpty(), "blocks that break into nothing (add loot_table/blocks/<id>.json, or register "
                + "them with noLootTable() if they truly drop nothing): " + missing);
    }

    private static List<Path> javaFiles() {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
