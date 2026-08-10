package com.createstats.manual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

public final class ManualPlayerNotesStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "create_stats_manual_notes.json";
    private static String cachedNotes;

    private ManualPlayerNotesStorage() {
    }

    public static synchronized String get() {
        if (cachedNotes == null) {
            cachedNotes = load();
        }
        return cachedNotes;
    }

    public static synchronized void set(String notes) {
        cachedNotes = notes == null ? "" : notes;
        save();
    }

    private static void save() {
        Path file = filePath();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new ManualNotesEnvelope(cachedNotes), writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static String load() {
        Path file = filePath();
        if (!Files.exists(file)) {
            return "";
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            ManualNotesEnvelope envelope = GSON.fromJson(reader, ManualNotesEnvelope.class);
            return envelope == null || envelope.playerNotes == null ? "" : envelope.playerNotes;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static class ManualNotesEnvelope {
        private final String playerNotes;

        private ManualNotesEnvelope(String playerNotes) {
            this.playerNotes = playerNotes;
        }
    }
}
