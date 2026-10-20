package com.createstats.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementTreeTest {
    private static final Path DIR = Path.of("src/main/resources/data/create_stats/advancement");
    private static final Path ITEM_MODELS = Path.of("src/main/resources/assets/create_stats/models/item");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final String NS = "create_stats:";

    private static final Set<String> TRIGGERS = Set.of("minecraft:tick", "minecraft:impossible",
            "minecraft:inventory_changed", "minecraft:placed_block", "minecraft:any_block_use",
            "minecraft:using_item", "minecraft:consume_item");

    private static Map<String, JsonObject> load() throws IOException {
        final Map<String, JsonObject> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(DIR)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                final String rel = DIR.relativize(p).toString().replace('\\', '/');
                final String id = NS + rel.substring(0, rel.length() - ".json".length());
                out.put(id, JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        return out;
    }

    @Test
    void oneRootAndEveryParentExists() throws IOException {
        final Map<String, JsonObject> all = load();
        final List<String> roots = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : all.entrySet()) {
            if (!e.getValue().has("parent")) {
                roots.add(e.getKey());
                continue;
            }
            final String parent = e.getValue().get("parent").getAsString();
            assertTrue(all.containsKey(parent), e.getKey() + ": parent " + parent + " has no file");
        }

        assertEquals(List.of(NS + "root"), roots, "the tab has exactly one root");

        assertTrue(all.containsKey(NS + "manifest_yaaa"), "manifest_yaaa is looked up by id in Java");
    }

    @Test
    void everyCriterionIsKnownAndRequired() throws IOException {
        for (Map.Entry<String, JsonObject> e : load().entrySet()) {
            final JsonObject criteria = e.getValue().getAsJsonObject("criteria");
            final Set<String> required = new HashSet<>();
            for (JsonElement group : e.getValue().getAsJsonArray("requirements")) {
                for (JsonElement name : group.getAsJsonArray()) {
                    assertTrue(criteria.has(name.getAsString()),
                            e.getKey() + ": requirement " + name + " names no criterion");
                    required.add(name.getAsString());
                }
            }
            for (Map.Entry<String, JsonElement> c : criteria.entrySet()) {
                final String trigger = c.getValue().getAsJsonObject().get("trigger").getAsString();
                assertTrue(TRIGGERS.contains(trigger), e.getKey() + ": unexpected trigger " + trigger);
                assertTrue(required.contains(c.getKey()), e.getKey() + ": criterion " + c.getKey() + " is never required");
            }
        }
    }

    @Test
    void everyModIdIsARealItem() throws IOException {
        for (Map.Entry<String, JsonObject> e : load().entrySet()) {
            final JsonObject body = e.getValue().deepCopy();
            body.remove("parent");
            final JsonObject display = body.getAsJsonObject("display");
            if (display.has("background")) {
                final String bg = display.remove("background").getAsString();
                assertTrue(bg.startsWith(NS) && Files.exists(ASSETS.resolve(bg.substring(NS.length()))),
                        e.getKey() + ": background " + bg + " is not a texture in this jar");
                assertEquals(NS + "root", e.getKey(), "only the root draws the tab background");
            }
            final List<String> ids = new ArrayList<>();
            collect(body, ids);
            for (String id : ids) {
                assertTrue(Files.exists(ITEM_MODELS.resolve(id.substring(NS.length()) + ".json")),
                        e.getKey() + ": " + id + " has no item model, so it is probably not registered");
            }
        }
    }

    @Test
    void textHasAFallbackThatMatchesTheLangFile() throws IOException {
        final JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonObject> e : load().entrySet()) {
            final JsonObject display = e.getValue().getAsJsonObject("display");
            for (String part : List.of("title", "description")) {
                final JsonObject text = display.getAsJsonObject(part);
                final String key = text.get("translate").getAsString();
                if (text.has("fallback")) {
                    final String fallback = text.get("fallback").getAsString();
                    assertTrue(!fallback.isBlank(), e.getKey() + ": blank " + part);
                    assertTrue(fallback.indexOf('%') < 0, e.getKey() + ": '%' in a fallback is a format specifier");
                    assertTrue(fallback.indexOf('—') < 0, e.getKey() + ": em dash in " + part);
                    if (lang.has(key)) {
                        assertEquals(fallback, lang.get(key).getAsString(), e.getKey() + ": " + key + " drifted");
                    }
                } else {
                    assertTrue(lang.has(key), e.getKey() + ": " + key + " is in neither the lang file nor a fallback");
                }
            }
        }
    }

    @Test
    void everyEventAdvancementIsGrantedFromJava() throws IOException {
        final Path helper = Path.of("src/main/java/com/createstats/advancement/ModAdvancements.java");
        final Map<String, String> constants = new TreeMap<>();
        final java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("public static final String (\\w+) = \"([a-z0-9_/]+)\";")
                .matcher(Files.readString(helper, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        while (m.find()) {
            constants.put(m.group(2), m.group(1));
        }
        final List<String> others = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith(helper.getFileName())).toList()) {
                others.add(Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        final Map<String, JsonObject> all = load();
        final Set<String> events = new HashSet<>();
        for (Map.Entry<String, JsonObject> e : all.entrySet()) {
            boolean impossible = true;
            for (Map.Entry<String, JsonElement> c : e.getValue().getAsJsonObject("criteria").entrySet()) {
                impossible &= "minecraft:impossible".equals(c.getValue().getAsJsonObject().get("trigger").getAsString());
            }
            if (!impossible) {
                continue;
            }
            final String id = e.getKey().substring(NS.length());
            events.add(id);
            final String constant = constants.get(id);
            final String needle = constant != null ? "ModAdvancements." + constant : "\"" + id + "\"";
            assertTrue(others.stream().anyMatch(src -> src.contains(needle)),
                    e.getKey() + " has only impossible criteria and nothing in src/main/java grants it (" + needle + ")");
        }
        for (Map.Entry<String, String> c : constants.entrySet()) {
            assertTrue(events.contains(c.getKey()), "ModAdvancements." + c.getValue() + " = \"" + c.getKey()
                    + "\" names no advancement file with impossible criteria");
        }
    }

    private static void collect(JsonElement el, List<String> out) {
        if (el.isJsonObject()) {
            el.getAsJsonObject().entrySet().forEach(x -> collect(x.getValue(), out));
        } else if (el.isJsonArray()) {
            el.getAsJsonArray().forEach(x -> collect(x, out));
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() && el.getAsString().startsWith(NS)) {
            out.add(el.getAsString());
        }
    }
}
