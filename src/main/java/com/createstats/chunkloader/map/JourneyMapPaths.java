package com.createstats.chunkloader.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// read only, from files that already exist. no compile dep on journeymap
public final class JourneyMapPaths {
    private static final Pattern UNSAFE = Pattern.compile("[^\\w\\s\\p{L}]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NON_WORD = Pattern.compile("\\W+");

    public record Folder(String name, long modified) {
    }

    private JourneyMapPaths() {
    }

    public static String safe(String s) {
        return UNSAFE.matcher(s == null ? "" : s).replaceAll("~");
    }

    public static String serverFolder(String entryName, String address) {
        if (entryName == null && address == null) {
            return "offline";
        }
        String name = entryName == null ? "" : NON_WORD.matcher(entryName).replaceAll("~").trim();
        if (name.replace("~", "").isEmpty()) {
            name = NON_WORD.matcher(address == null ? "" : address).replaceAll("~").trim();
        }
        if (name.trim().isEmpty()) {
            name = "unnamed";
        }
        return safe(name.trim());
    }

    public static List<Path> worldDirs(Path gameDir, boolean singlePlayer, String saveFolder, String serverName,
                                       Predicate<Path> exists, List<Folder> mpFolders) {
        final Path data = gameDir.resolve("journeymap").resolve("data");
        final List<Path> out = new ArrayList<>();
        if (singlePlayer) {
            final Path current = data.resolve("sp").resolve(saveFolder);
            final Path legacy = data.resolve("sp").resolve(safe(saveFolder));
            out.add(!exists.test(current) && exists.test(legacy) ? legacy : current);
            return out;
        }
        final Path mp = data.resolve("mp");
        out.add(mp.resolve(serverName));
        final List<Folder> withId = new ArrayList<>();
        for (Folder f : mpFolders) {
            if (f.name().startsWith(serverName + "_")) {
                withId.add(f);
            }
        }
        withId.sort(Comparator.comparingLong(Folder::modified).reversed());
        for (Folder f : withId) {
            out.add(mp.resolve(f.name()));
        }
        return out;
    }

    public static String dimensionFolder(String dimensionId, Predicate<String> legacyExists) {
        switch (dimensionId) {
            case "minecraft:overworld" -> {
                if (legacyExists.test("DIM0")) {
                    return "DIM0";
                }
            }
            case "minecraft:the_end" -> {
                if (legacyExists.test("DIM1")) {
                    return "DIM1";
                }
            }
            case "minecraft:the_nether" -> {
                if (legacyExists.test("DIM-1")) {
                    return "DIM-1";
                }
            }
            default -> {
            }
        }
        final int colon = dimensionId.indexOf(':');
        final String namespace = colon < 0 ? "minecraft" : dimensionId.substring(0, colon);
        return "minecraft".equals(namespace) ? dimensionId.substring(colon + 1) : safe(dimensionId);
    }

    public static Path dayTile(Path worldDir, String dimensionFolder, int rx, int rz) {
        return worldDir.resolve(dimensionFolder).resolve("day").resolve(rx + "," + rz + ".png");
    }
}
