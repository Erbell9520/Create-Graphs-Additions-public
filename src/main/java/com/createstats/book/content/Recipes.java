package com.createstats.book.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// read from the shipped recipe files, never typed. typed text is right until someone rebalances
public final class Recipes {
    private Recipes() {
    }

    public static final Map<String, String> TAG_SHOWN_AS = Map.of(
            "c:ingots/copper", "minecraft:copper_ingot",
            "c:sands", "minecraft:sand",
            "c:plates/gold", "create:golden_sheet");

    public record Stack(String id, int count, boolean fluid, int mb) {
        static Stack item(String id, int count) {
            return new Stack(id, count, false, 0);
        }

        static Stack fluid(String id, int mb) {
            return new Stack(id, 1, true, mb);
        }
    }

    public record Result(String id, int count, double chance) {
    }

    public record Step(String type, Stack adds) {
    }

    public record Recipe(String path, String type, List<Stack> inputs, List<Result> results, List<Step> steps,
                         int loops, String transitional) {
        public Recipe {
            inputs = List.copyOf(inputs);
            results = List.copyOf(results);
            steps = List.copyOf(steps);
        }

        public Result made() {
            return results.get(0);
        }

        public boolean sequenced() {
            return !steps.isEmpty();
        }
    }

    private static final Map<String, Recipe> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static Recipe read(String path) {
        return CACHE.computeIfAbsent(path, Recipes::load);
    }

    private static Recipe load(String path) {
        final String resource = "/data/create_stats/recipe/" + path + ".json";
        final JsonObject o;
        try (InputStream in = Recipes.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no recipe " + resource);
            }
            o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + resource, e);
        }
        final String type = o.get("type").getAsString();
        return switch (type) {
            case "minecraft:crafting_shaped", "create:mechanical_crafting" -> shaped(path, type, o);
            case "minecraft:crafting_shapeless" -> new Recipe(path, type, counted(o.getAsJsonArray("ingredients")),
                    List.of(result(o.getAsJsonObject("result"), 100.0)), List.of(), 1, null);
            case "minecraft:blasting", "minecraft:smelting" -> new Recipe(path, type,
                    List.of(ingredient(o.get("ingredient"), 1)), List.of(result(o.getAsJsonObject("result"), 100.0)),
                    List.of(), 1, null);
            case "create_stats:arc_smelting" -> new Recipe(path, type, List.of(
                    ingredient(o.get("first"), o.has("first_count") ? o.get("first_count").getAsInt() : 1),
                    ingredient(o.get("second"), o.has("second_count") ? o.get("second_count").getAsInt() : 1)),
                    List.of(result(o.getAsJsonObject("result"), 100.0)), List.of(), 1, null);
            case "create:sequenced_assembly" -> sequenced(path, type, o);
            case "create:cutting", "create:deploying", "create:pressing", "create:compacting", "create:mixing",
                 "create:filling", "create:milling", "create:crushing" -> new Recipe(path, type,
                    counted(o.getAsJsonArray("ingredients")), results(o.getAsJsonArray("results")), List.of(), 1,
                    null);
            default -> throw new IllegalStateException(resource + " is a " + type + ", which the book cannot read yet");
        };
    }

    private static Recipe shaped(String path, String type, JsonObject o) {
        final JsonObject key = o.getAsJsonObject("key");
        final Map<Character, Integer> uses = new LinkedHashMap<>();
        for (final JsonElement row : o.getAsJsonArray("pattern")) {
            for (final char c : row.getAsString().toCharArray()) {
                if (c != ' ') {
                    uses.merge(c, 1, Integer::sum);
                }
            }
        }
        final List<Stack> inputs = new ArrayList<>();
        for (final Map.Entry<Character, Integer> e : uses.entrySet()) {
            inputs.add(ingredient(key.get(String.valueOf(e.getKey())), e.getValue()));
        }
        return new Recipe(path, type, inputs, List.of(result(o.getAsJsonObject("result"), 100.0)), List.of(), 1, null);
    }

    private static Recipe sequenced(String path, String type, JsonObject o) {
        final List<Step> steps = new ArrayList<>();
        for (final JsonElement e : o.getAsJsonArray("sequence")) {
            final JsonObject step = e.getAsJsonObject();
            final JsonArray in = step.getAsJsonArray("ingredients");

            final Stack adds = in.size() > 1 ? ingredient(in.get(1), 1) : null;
            steps.add(new Step(step.get("type").getAsString(), adds));
        }
        final String transitional = o.getAsJsonObject("transitional_item").get("id").getAsString();

        final List<Result> weighted = results(o.getAsJsonArray("results"));
        double total = 0.0;
        for (final Result r : weighted) {
            total += r.chance();
        }
        final List<Result> results = new ArrayList<>();
        for (final Result r : weighted) {
            results.add(new Result(r.id(), r.count(), total > 0.0 ? r.chance() * 100.0 / total : 100.0));
        }
        return new Recipe(path, type, List.of(ingredient(o.get("ingredient"), 1)), results, steps,
                o.has("loops") ? o.get("loops").getAsInt() : 1, transitional);
    }

    private static List<Stack> counted(JsonArray array) {
        final Map<String, Stack> out = new LinkedHashMap<>();
        for (final JsonElement e : array) {
            final Stack s = ingredient(e, 1);
            out.merge(s.id() + (s.fluid() ? "#fluid" : ""), s, (a, b) -> a.fluid()
                    ? Stack.fluid(a.id(), a.mb() + b.mb()) : Stack.item(a.id(), a.count() + b.count()));
        }
        return new ArrayList<>(out.values());
    }

    private static Stack ingredient(JsonElement e, int count) {
        final JsonObject o = e.isJsonArray() ? e.getAsJsonArray().get(0).getAsJsonObject() : e.getAsJsonObject();
        if (o.has("fluid")) {
            return Stack.fluid(o.get("fluid").getAsString(), o.has("amount") ? o.get("amount").getAsInt() : 1000);
        }
        if (o.has("item")) {
            return Stack.item(o.get("item").getAsString(), count);
        }
        if (o.has("tag")) {
            final String tag = o.get("tag").getAsString();
            final String shown = TAG_SHOWN_AS.get(tag);
            if (shown == null) {
                throw new IllegalStateException("the book has no item to show the tag " + tag + " as");
            }
            return Stack.item(shown, count);
        }
        throw new IllegalStateException("an ingredient the book cannot read: " + o);
    }

    private static Result result(JsonObject o, double chance) {
        return new Result(o.get("id").getAsString(), o.has("count") ? o.get("count").getAsInt() : 1, chance);
    }

    private static List<Result> results(JsonArray array) {
        final List<Result> out = new ArrayList<>();
        for (final JsonElement e : array) {
            final JsonObject o = e.getAsJsonObject();
            out.add(result(o, o.has("chance") ? o.get("chance").getAsDouble() : 100.0));
        }
        return out;
    }
}
