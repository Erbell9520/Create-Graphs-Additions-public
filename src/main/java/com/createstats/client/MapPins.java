package com.createstats.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

@OnlyIn(Dist.CLIENT)
public final class MapPins {
    private static final Path FILE =
            FMLPaths.CONFIGDIR.get().resolve("create_stats").resolve("train_map_pins.txt");

    private static final int MAX_PINS = 512;

    private static final Map<UUID, float[]> PINS = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean dirty;

    private MapPins() {
    }

    public static Map<UUID, float[]> all() {
        if (!loaded) {
            load();
        }
        return PINS;
    }

    public static void adopt(Map<UUID, float[]> live) {
        if (!loaded) {
            load();
        }
        PINS.clear();
        if (live != null) {
            for (final Map.Entry<UUID, float[]> e : live.entrySet()) {
                if (PINS.size() >= MAX_PINS) {
                    break;
                }
                if (e.getKey() != null && e.getValue() != null && e.getValue().length >= 2) {
                    PINS.put(e.getKey(), new float[]{e.getValue()[0], e.getValue()[1]});
                }
            }
        }
        dirty = true;
    }

    public static void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            Files.createDirectories(FILE.getParent());
            final List<String> lines = new ArrayList<>(PINS.size());
            for (final Map.Entry<UUID, float[]> e : PINS.entrySet()) {
                lines.add(String.format(Locale.ROOT, "%s %.2f %.2f",
                        e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
            Files.write(FILE, lines);
        }
        catch (final IOException | RuntimeException ignored) {
        }
    }

    private static void load() {
        loaded = true;
        PINS.clear();
        try {
            if (!Files.isRegularFile(FILE)) {
                return;
            }
            for (final String line : Files.readAllLines(FILE)) {
                final String[] parts = line.trim().split(" ");
                if (parts.length != 3) {
                    continue;
                }
                try {
                    PINS.put(UUID.fromString(parts[0]), new float[]{
                            Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                }
                catch (final IllegalArgumentException ignored) {
                }
                if (PINS.size() >= MAX_PINS) {
                    return;
                }
            }
        }
        catch (final IOException | RuntimeException ignored) {
        }
    }
}
