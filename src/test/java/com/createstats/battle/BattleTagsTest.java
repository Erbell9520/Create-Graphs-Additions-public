package com.createstats.battle;

import com.createstats.battle.core.BattleSystem;
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

class BattleTagsTest {
    private static final Path DIR = Path.of("src/main/resources/data/create_stats/tags/block/battle");

    @Test
    void everySystemHasATagFileAndTheTwoFiltersToo() {
        List<String> missing = new ArrayList<>();
        for (BattleSystem s : BattleSystem.values()) {
            if (!Files.isRegularFile(DIR.resolve(s.key() + ".json"))) missing.add(s.key());
        }
        for (String filter : new String[]{"natural", "ignore"}) {
            if (!Files.isRegularFile(DIR.resolve(filter + ".json"))) missing.add(filter);
        }
        assertTrue(missing.isEmpty(), "tag files missing under " + DIR + ": " + missing);
    }

    @Test
    void everyForeignIdIsOptional() throws IOException {
        List<String> faults = new ArrayList<>();
        List<Path> files;
        try (var stream = Files.list(DIR)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(files.size() >= BattleSystem.values().length + 2, "found only " + files.size() + " tag files");
        for (Path file : files) {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray values = json.getAsJsonArray("values");
            for (JsonElement e : values) {
                String id;
                boolean required;
                if (e.isJsonPrimitive()) {
                    id = e.getAsString();
                    required = true;
                } else {
                    JsonObject o = e.getAsJsonObject();
                    id = o.get("id").getAsString();
                    required = !o.has("required") || o.get("required").getAsBoolean();
                }
                String bare = id.startsWith("#") ? id.substring(1) : id;
                String namespace = bare.contains(":") ? bare.substring(0, bare.indexOf(':')) : "minecraft";
                boolean ours = namespace.equals("create_stats") || namespace.equals("minecraft");
                if (!ours && required) faults.add(file.getFileName() + ": " + id + " must be \"required\": false");
            }
        }
        assertTrue(faults.isEmpty(), String.join("\n", faults));
    }
}
