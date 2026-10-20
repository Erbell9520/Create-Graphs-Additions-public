package com.createstats.seismic;

import java.util.Locale;
import java.util.Map;

// impedance contrast, density times wave speed
public final class SeismicCatalog {
    public static final String VOIDS = "create_stats:voids";

    public static final String LIQUIDS = "create_stats:liquids";

    public static final String ORE_PREFIX = "c:ores/";

    public static final int KIND_LAVA = 0;
    public static final int KIND_WATER = 1;

    public record Mineral(String mineral, double impedance, int colour) {
    }

    private static final Map<String, Mineral> KNOWN = Map.ofEntries(
            Map.entry("coal", new Mineral("coal", 3.2, 0xB4B4B4)),
            Map.entry("lapis", new Mineral("lazurite", 12.0, 0x4F7BF0)),
            Map.entry("quartz", new Mineral("quartz", 16.0, 0xF2ECE4)),
            Map.entry("copper", new Mineral("chalcopyrite", 23.0, 0xE88A55)),
            Map.entry("zinc", new Mineral("sphalerite", 22.7, 0xB9D8CC)),
            Map.entry("emerald", new Mineral("beryl", 25.0, 0x2EE57A)),
            Map.entry("redstone", new Mineral("cinnabar", 25.5, 0xFF3B30)),
            Map.entry("tin", new Mineral("cassiterite", 48.0, 0xD9D2C8)),
            Map.entry("lead", new Mineral("galena", 28.0, 0x9A9FC8)),
            Map.entry("silver", new Mineral("native silver", 37.8, 0xE6EEF5)),
            Map.entry("nickel", new Mineral("pentlandite", 25.0, 0xD8D0A0)),
            Map.entry("uranium", new Mineral("uraninite", 53.0, 0x9BE35A)),
            Map.entry("iron", new Mineral("magnetite", 38.3, 0xE3B596)),
            Map.entry("diamond", new Mineral("diamond", 61.6, 0x63F0F2)),
            Map.entry("gold", new Mineral("native gold", 62.5, 0xFFE44A)));

    private SeismicCatalog() {
    }

    public static boolean isOre(String id) {
        return id != null && id.startsWith(ORE_PREFIX) && id.length() > ORE_PREFIX.length();
    }

    public static String orePath(String id) {
        return isOre(id) ? id.substring(ORE_PREFIX.length()) : "";
    }

    public static Mineral mineral(String id) {
        return KNOWN.get(orePath(id));
    }

    public static double impedance(String id) {
        if (VOIDS.equals(id)) {
            return 0.0004;
        }
        if (LIQUIDS.equals(id)) {
            return 1.5;
        }
        final Mineral m = mineral(id);
        return m == null ? Double.POSITIVE_INFINITY : m.impedance();
    }

    public static String veinLabel(String id, int kind) {
        if (VOIDS.equals(id)) {
            return "VOID";
        }
        if (LIQUIDS.equals(id)) {
            return kind == KIND_LAVA ? "LAVA" : "WATER";
        }
        final String path = orePath(id);
        if (path.equals("netherite_scrap")) {
            return "DEBRIS";
        }
        return path.replace('/', ' ').replace('_', ' ').toUpperCase(Locale.ROOT);
    }

    public static int colour(String id, int kind) {
        if (VOIDS.equals(id)) {
            return 0x7FD8FF;
        }
        if (LIQUIDS.equals(id)) {
            return kind == KIND_LAVA ? 0xFF8A1E : 0x4A86F0;
        }
        final Mineral m = mineral(id);
        if (m != null) {
            return m.colour();
        }
        if (orePath(id).equals("netherite_scrap")) {
            return 0xC0866E;
        }
        return hashColour(orePath(id));
    }

    public static String subLabel(String id) {
        if (VOIDS.equals(id)) {
            return "air, ~0 MRayl";
        }
        if (LIQUIDS.equals(id)) {
            return "water 1.5, lava 6.8 MRayl";
        }
        final Mineral m = mineral(id);
        if (m == null) {
            return id;
        }
        return String.format(Locale.ROOT, "%s, %.0f MRayl", m.mineral(), m.impedance());
    }

    public static int compare(String a, String b) {
        final int byZ = Double.compare(impedance(a), impedance(b));
        return byZ != 0 ? byZ : a.compareTo(b);
    }

    private static final Map<String, Integer> DENSEST = Map.of(
            "coal", 96, "copper", 48, "iron", 16, "gold", -16, "lapis", 0, "redstone", -59, "diamond", -59,
            "emerald", 232, "netherite_scrap", 15);

    public static Integer densest(String id) {
        return DENSEST.get(orePath(id));
    }

    public static int bowlBottom(int rigY, int radius) {
        return rigY - radius;
    }

    public static boolean bandMissed(String id, int rigY, int radius) {
        final Integer d = densest(id);
        return d != null && (d < bowlBottom(rigY, radius) || d > rigY - 1);
    }

    static int hashColour(String name) {
        int h = 0x811C9DC5;
        for (int i = 0; i < name.length(); i++) {
            h = (h ^ name.charAt(i)) * 0x01000193;
        }
        final double hue = ((h >>> 8) & 0xFFFF) / 65536.0;

        final double s = 0.62;
        final int sector = (int) (hue * 6.0);
        final double f = hue * 6.0 - sector;
        final double p = 1.0 - s;
        final double q = 1.0 - s * f;
        final double t = 1.0 - s * (1.0 - f);
        final double[] rgb = switch (sector % 6) {
            case 0 -> new double[] {1.0, t, p};
            case 1 -> new double[] {q, 1.0, p};
            case 2 -> new double[] {p, 1.0, t};
            case 3 -> new double[] {p, q, 1.0};
            case 4 -> new double[] {t, p, 1.0};
            default -> new double[] {1.0, p, q};
        };
        return ((int) Math.round(rgb[0] * 255) << 16) | ((int) Math.round(rgb[1] * 255) << 8)
                | (int) Math.round(rgb[2] * 255);
    }
}
