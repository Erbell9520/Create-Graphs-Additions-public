package com.createstats.book.screen;

import com.createstats.CreateStats;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
final class BookMemory {
    private static final String FILE_NAME = "create_stats_students_manual.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Place(String entry, int sheet) {
    }

    private BookMemory() {
    }

    static Place ribbon(UUID player) {
        final JsonObject root = read();
        final JsonObject ribbons = root.has("ribbons") ? root.getAsJsonObject("ribbons") : null;
        if (ribbons == null || player == null || !ribbons.has(player.toString())) {
            return null;
        }
        try {
            final JsonObject o = ribbons.getAsJsonObject(player.toString());
            return new Place(o.get("entry").getAsString(), o.get("sheet").getAsInt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void setRibbon(UUID player, Place place) {
        if (player == null || place == null) {
            return;
        }
        final JsonObject root = read();
        final JsonObject ribbons = root.has("ribbons") ? root.getAsJsonObject("ribbons") : new JsonObject();
        final JsonObject o = new JsonObject();
        o.addProperty("entry", place.entry());
        o.addProperty("sheet", place.sheet());
        ribbons.add(player.toString(), o);
        root.add("ribbons", ribbons);
        try {
            final Path file = file();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CreateStats.LOGGER.warn("Student's Manual: could not save the ribbon", e);
        }
    }

    private static JsonObject read() {
        final Path file = file();
        if (!Files.isRegularFile(file)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return new JsonObject();
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }
}
