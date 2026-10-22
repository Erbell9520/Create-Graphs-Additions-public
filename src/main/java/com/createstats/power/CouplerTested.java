package com.createstats.power;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CouplerTested {
    public static final String RESOURCE = "/assets/create_stats/compat/universal_power_coupler_tested.json";

    @Nullable
    private static volatile List<Entry> cached;

    private CouplerTested() {
    }

    public record Entry(String modId, String name, String version, List<String> cases) {
        public Entry {
            cases = List.copyOf(cases);
        }

        public String howTested() {
            return String.join(", ", this.cases);
        }
    }

    public static List<Entry> all() {
        List<Entry> list = cached;
        if (list != null) {
            return list;
        }
        try (InputStream in = CouplerTested.class.getResourceAsStream(RESOURCE)) {
            list = in == null ? List.of() : parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException unreadable) {
            list = List.of();
        }
        cached = list;
        return list;
    }

    public static List<Entry> parse(Reader json) {
        final JsonElement root = JsonParser.parseReader(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("tested")) {
            return List.of();
        }
        final JsonArray tested = root.getAsJsonObject().getAsJsonArray("tested");
        final List<Entry> out = new ArrayList<>(tested.size());
        for (JsonElement e : tested) {
            if (!e.isJsonObject()) {
                continue;
            }
            final JsonObject o = e.getAsJsonObject();
            if (!o.has("passed") || !o.get("passed").getAsBoolean() || !o.has("cases")) {
                continue;
            }
            final List<String> cases = new ArrayList<>();
            for (JsonElement c : o.getAsJsonArray("cases")) {
                cases.add(c.getAsString());
            }
            if (cases.isEmpty()) {
                continue;
            }
            out.add(new Entry(string(o, "modid"), string(o, "name"), string(o, "version"), cases));
        }
        return List.copyOf(out);
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }
}
