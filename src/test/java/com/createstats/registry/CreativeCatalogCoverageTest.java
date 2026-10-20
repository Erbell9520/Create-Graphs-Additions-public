package com.createstats.registry;

import com.createstats.client.CatalogBannerSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeCatalogCoverageTest {
    private static final Path TAB = Path.of("src/main/java/com/createstats/registry/ModCreativeTab.java");
    private static final Path BLOCKS = Path.of("src/main/java/com/createstats/registry/ModBlocks.java");
    private static final Path ITEMS = Path.of("src/main/java/com/createstats/registry/ModItems.java");
    private static final Path DOORS = Path.of("src/main/java/com/createstats/block/DoorMaterial.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");

    private static final String CATALOGUE_START = "private static final List<Section> SECTIONS";
    private static final String CATALOGUE_END = "public static List<Section> catalogSections()";

    private static final Set<String> UNLISTED = Set.of(

            "ModBlocks.STATS_ALARM",

            "ModBlocks.STATS_RECORDER",
            "ModItems.MEMORY_COG",

            "ModItems.FLOUR",

            "ModItems.SUPPORT_TERMINAL",

            "ModItems.SUCTION_WAND");

    private static final List<String> FAMILY_WALKS = List.of(
            "ModBlocks.securitySensor(", "SensorKind.all()",
            "ModBlocks.blastShutter(", "ShutterTier.all()",
            "ModItems.storageUpgrade(", "UpgradeKind.values()", "UpgradeDegree.values()",
            "ModItems.filledCakeMold(", "ModBlocks.cake(", "CakeFlavor.values()",
            "ModBlocks.poweredDoor(");

    private static final Pattern FIELD =
            Pattern.compile("\\b([A-Z][A-Z0-9_]*)\\s*=\\s*CreateStats\\.registrate\\(\\)");
    private static final Pattern REFERENCE = Pattern.compile("\\b(ModBlocks|ModItems)\\.([A-Z][A-Z0-9_]*)\\b");

    private static final Pattern DOOR_CONSTANT = Pattern.compile("(?m)^ {4}([A-Z][A-Z0-9_]*)\\s*\\(");
    private static final Pattern DOOR_REFERENCE = Pattern.compile("\\bDoorMaterial\\.([A-Z][A-Z0-9_]*)\\b");
    private static final Pattern SECTION = Pattern.compile("(?:\\bsection|new Section)\\(\\s*\"([a-z_]+)\"");

    private static final Pattern LANG_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");
    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

    @Test
    void everyItemIsListedExactlyOnce() throws IOException {
        final Map<String, Integer> named = count(REFERENCE, catalogue(), m -> m.group(1) + "." + m.group(2));
        final Set<String> fields = new TreeSet<>();
        fields.addAll(itemFields(BLOCKS, "ModBlocks"));
        fields.addAll(itemFields(ITEMS, "ModItems"));
        assertTrue(fields.size() > 100, "The registry scan found only " + fields.size()
                + " item-bearing fields; FIELD has drifted from how ModBlocks and ModItems register");

        final List<String> problems = new ArrayList<>();
        for (String field : fields) {
            final int n = named.getOrDefault(field, 0);
            if (UNLISTED.contains(field)) {
                if (n != 0) {
                    problems.add(field + " is deliberately unlisted, but the catalogue names it");
                }
            } else if (n == 0) {
                problems.add(field + " is in no section, so it is missing from the creative menu");
            } else if (n > 1) {
                problems.add(field + " is named " + n + " times; an item belongs to one section");
            }
        }
        for (String name : named.keySet()) {
            if (!fields.contains(name)) {
                problems.add(name + " is named in the catalogue but registers no item");
            }
        }
        assertTrue(problems.isEmpty(), "Creative catalogue coverage:\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyDoorMaterialHasExactlyOneRow() throws IOException {
        final Map<String, Integer> named = count(DOOR_REFERENCE, catalogue(), m -> m.group(1));
        final Matcher constants = DOOR_CONSTANT.matcher(Files.readString(DOORS, StandardCharsets.UTF_8));
        final List<String> problems = new ArrayList<>();
        int materials = 0;
        while (constants.find()) {
            materials++;
            final int n = named.getOrDefault(constants.group(1), 0);
            if (n != 1) {
                problems.add("DoorMaterial." + constants.group(1) + " is named " + n
                        + " times in the door rows; it must be named once");
            }
        }
        assertTrue(materials >= 18, "Found only " + materials + " DoorMaterial constants; "
                + "DOOR_CONSTANT has drifted from the enum's layout");
        assertTrue(problems.isEmpty(), "Door rows:\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyGeneratedFamilyIsStillWalked() throws IOException {
        final String catalogue = catalogue();
        final List<String> missing = FAMILY_WALKS.stream().filter(walk -> !catalogue.contains(walk)).toList();
        assertTrue(missing.isEmpty(), "Generated families the catalogue no longer reaches, so their "
                + "items are missing from the creative menu: " + missing);
    }

    @Test
    void everySectionIsNamedAndHasItsBanner() throws IOException {
        final Set<String> langKeys = langKeys();
        final List<String> problems = new ArrayList<>();
        final Set<String> keys = new LinkedHashSet<>();
        final Matcher sections = SECTION.matcher(catalogue());
        while (sections.find()) {
            if (!keys.add(sections.group(1))) {
                problems.add("section key '" + sections.group(1) + "' is used twice");
            }
        }
        assertTrue(keys.size() >= 4, "Found only " + keys.size() + " sections; SECTION has drifted "
                + "from how ModCreativeTab declares them");

        for (String key : keys) {
            if (!langKeys.contains("itemGroup.create_stats." + key)) {
                problems.add("'" + key + "' has no itemGroup.create_stats." + key
                        + " in en_us.json, so its banner label prints the raw key");
            }
            final CatalogBannerSpec spec = CatalogBannerSpec.of(key);
            if (spec == null) {
                problems.add("'" + key + "' has no CatalogBannerSpec, so its section opens on an empty row");
                continue;
            }
            final Path strip = ASSETS.resolve(spec.texturePath());
            if (!Files.isRegularFile(strip)) {
                problems.add("'" + key + "' has no " + spec.texturePath() + "; run gen_creative_banners.py");
                continue;
            }
            final int[] size = pngSize(strip);
            if (size[0] != CatalogBannerSpec.FRAME_WIDTH || size[1] != spec.textureHeight()) {
                problems.add("'" + key + "' banner is " + size[0] + "x" + size[1] + " but its spec says "
                        + CatalogBannerSpec.FRAME_WIDTH + "x" + spec.textureHeight() + " (" + spec.frames()
                        + " frames); the script and CatalogBannerSpec have drifted");
            }
        }
        for (CatalogBannerSpec spec : CatalogBannerSpec.values()) {
            if (!keys.contains(spec.key)) {
                problems.add("CatalogBannerSpec." + spec + " has no section '" + spec.key + "'");
            }
        }
        if (!langKeys.contains("itemGroup.create_stats.count")) {
            problems.add("itemGroup.create_stats.count is gone, so a scrollbar notch's tooltip prints the raw key");
        }
        assertTrue(problems.isEmpty(), "Creative sections:\n  " + String.join("\n  ", problems));
    }

    private static int[] pngSize(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png)) {
            final byte[] head = in.readNBytes(24);
            assertTrue(head.length == 24 && head[12] == 'I' && head[13] == 'H' && head[14] == 'D' && head[15] == 'R',
                    png + " is not a PNG");
            final int w = (head[16] & 0xFF) << 24 | (head[17] & 0xFF) << 16 | (head[18] & 0xFF) << 8 | head[19] & 0xFF;
            final int h = (head[20] & 0xFF) << 24 | (head[21] & 0xFF) << 16 | (head[22] & 0xFF) << 8 | head[23] & 0xFF;
            return new int[] {w, h};
        }
    }

    private static String catalogue() throws IOException {
        final String body = Files.readString(TAB, StandardCharsets.UTF_8);
        final int start = body.indexOf(CATALOGUE_START);
        final int end = body.indexOf(CATALOGUE_END);
        assertTrue(start >= 0 && end > start, "ModCreativeTab no longer has the SECTIONS ... catalogSections() "
                + "layout this test reads; update CATALOGUE_START and CATALOGUE_END");
        return COMMENTS.matcher(body.substring(start, end)).replaceAll("");
    }

    private static Set<String> itemFields(Path source, String owner) throws IOException {
        final String body = Files.readString(source, StandardCharsets.UTF_8);
        final Set<String> fields = new TreeSet<>();
        final Matcher m = FIELD.matcher(body);
        while (m.find()) {
            final int end = body.indexOf(".register()", m.end());
            final String statement = body.substring(m.start(), end < 0 ? body.length() : end);
            if (statement.contains("simpleItem()") || statement.contains(".item(")) {
                fields.add(owner + "." + m.group(1));
            }
        }
        return fields;
    }

    private static Set<String> langKeys() throws IOException {
        final Set<String> keys = new TreeSet<>();
        final Matcher m = LANG_KEY.matcher(Files.readString(LANG, StandardCharsets.UTF_8));
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static Map<String, Integer> count(Pattern pattern, String text, Function<MatchResult, String> key) {
        final Map<String, Integer> counts = new TreeMap<>();
        final Matcher m = pattern.matcher(text);
        while (m.find()) {
            counts.merge(key.apply(m.toMatchResult()), 1, Integer::sum);
        }
        return counts;
    }
}
