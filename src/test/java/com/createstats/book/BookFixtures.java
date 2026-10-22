package com.createstats.book;

import com.createstats.book.content.Section;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Ctx;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class BookFixtures {
    static final Path LANG = Path.of(System.getProperty("createstats.bookLang",
            "src/main/resources/assets/create_stats/lang/en_us.json"));

    private BookFixtures() {
    }

    static Map<String, String> lang() throws IOException {
        final JsonObject o = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        final Map<String, String> out = new HashMap<>();
        for (final String k : o.keySet()) {
            out.put(k, o.get(k).getAsString());
        }
        return out;
    }

    static BookText text(Map<String, String> lang) {
        return new BookText() {
            @Override
            public String get(String key, Object... args) {
                final String v = lang.get(key);
                if (v == null) {
                    return key;
                }
                return args.length == 0 ? v : String.format(Locale.ROOT, v, args);
            }

            @Override
            public String itemName(String registryId) {
                final String path = registryId.substring(registryId.indexOf(':') + 1);
                final String block = lang.get("block.create_stats." + path);
                if (block != null) {
                    return block;
                }
                final String item = lang.get("item.create_stats." + path);
                if (item != null) {
                    return item;
                }

                final Map<String, String> foreign = foreignNames();
                final String ns = registryId.substring(0, Math.max(0, registryId.indexOf(':')));
                for (final String kind : new String[]{"item.", "block."}) {
                    final String name = foreign.get(kind + ns + "." + path);
                    if (name != null) {
                        return name;
                    }
                }
                return registryId;
            }
        };
    }

    private static Map<String, String> foreign;

    static synchronized Map<String, String> foreignNames() {
        if (foreign != null) {
            return foreign;
        }
        foreign = new HashMap<>();
        final List<Path> jars = new java.util.ArrayList<>(List.of(PngBookCanvas.CLIENT_JAR));
        final Path create = Path.of(System.getProperty("user.home"),
                ".gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1");
        if (Files.isDirectory(create)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(create)) {
                walk.filter(f -> f.getFileName().toString().endsWith("-slim.jar")).findFirst().ifPresent(jars::add);
            } catch (IOException ignored) {
            }
        }
        for (final Path jar : jars) {
            if (!Files.isRegularFile(jar)) {
                continue;
            }
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jar.toFile())) {
                for (final String entry : new String[]{"assets/minecraft/lang/en_us.json", "assets/create/lang/en_us.json"}) {
                    final java.util.zip.ZipEntry e = zip.getEntry(entry);
                    if (e == null) {
                        continue;
                    }
                    final JsonObject o = JsonParser.parseReader(new java.io.InputStreamReader(zip.getInputStream(e),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    for (final String k : o.keySet()) {
                        if (o.get(k).isJsonPrimitive()) {
                            foreign.put(k, o.get(k).getAsString());
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return foreign;
    }

    static Ctx.Ponder ponder() {
        return section -> switch (section) {
            case POWER -> List.of("create_stats:surface_cable", "create_stats:cable_tray", "create_stats:bus_duct",
                    "create_stats:busbar_trunk", "create_stats:capacitor_bank", "create_stats:fe_battery_controller",
                    "create_stats:power_connector", "create_stats:advanced_power_connector",
                    "create_stats:industrial_power_connector", "create_stats:copper_wire",
                    "create_stats:insulated_wire", "create_stats:shielded_wire", "create_stats:hv_line",
                    "create_stats:power_distribution_unit", "create_stats:power_track", "create_stats:track_feeder",
                    "create_stats:section_insulator", "create_stats:current_collector",
                    "create_stats:storm_coil", "create_stats:spark_crown", "create_stats:universal_power_coupler");
            case MACHINES -> List.of("create_stats:seismic_scanner");
            case TRANSPORT -> List.of("create_stats:vcc_mk4_aerial");
            default -> List.<String>of();
        };
    }

    static Section sectionOf(String key) {
        for (final Section s : Section.values()) {
            if (s.key().equals(key)) {
                return s;
            }
        }
        throw new IllegalArgumentException(key);
    }
}
