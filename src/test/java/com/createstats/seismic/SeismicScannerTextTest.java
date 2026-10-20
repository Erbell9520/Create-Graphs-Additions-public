package com.createstats.seismic;

import com.createstats.client.statspower.RasterFont;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeismicScannerTextTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path SCREEN = Path.of("src/main/java/com/createstats/client/tuner/TunerScreen.java");
    private static final String P = "block.create_stats.seismic_scanner.";

    private static final int GOGGLE_LINE = 371;
    private static final String GOGGLE_PAD = "    ";

    private static final Map<String, String[]> WIDEST = Map.ofEntries(
            Map.entry("tuner.range", new String[] {"64"}),
            Map.entry("tuner.cost", new String[] {"500,000"}),
            Map.entry("tuner.depth", new String[] {"-128"}),
            Map.entry("tuner.band", new String[] {"-59"}),
            Map.entry("goggle.ready", new String[] {"64"}),
            Map.entry("goggle.charging", new String[] {"100"}),
            Map.entry("goggle.charging_eta", new String[] {"100", "10:00"}),
            Map.entry("goggle.shed", new String[] {"100"}),
            Map.entry("goggle.no_ground", new String[] {"Polished Blackstone Bricks"}),
            Map.entry("goggle.target", new String[] {"Deepslate Redstone Ore"}),
            Map.entry("goggle.radius", new String[] {"64", "-128"}),
            Map.entry("goggle.band", new String[] {"-59"}),
            Map.entry("goggle.surveying", new String[] {"64", "64", "999"}),
            Map.entry("goggle.last", new String[] {"999", "32,768", "64", "59 min"}),
            Map.entry("goggle.last_full", new String[] {"999", "64", "64", "59 min"}),
            Map.entry("goggle.last_none", new String[] {"-128", "59 min"}),
            Map.entry("goggle.last_tap", new String[] {"59 min"}),
            Map.entry("goggle.last_loose", new String[] {"59 min"}),
            Map.entry("goggle.last_untuned", new String[] {"59 min"}),
            Map.entry("goggle.coupled", new String[] {"Polished Blackstone Bricks"}),
            Map.entry("goggle.display_link", new String[] {"Seismic Survey"}),
            Map.entry("goggle.cost", new String[] {"548,752", "12.34"}),
            Map.entry("report.found", new String[] {"Deepslate Redstone Ore", "999", "64", "-128"}),
            Map.entry("report.full", new String[] {"Deepslate Redstone Ore", "999", "64", "64", "64"}),
            Map.entry("report.none", new String[] {"Deepslate Redstone Ore", "-128"}),
            Map.entry("report.reread_one", new String[] {"Deepslate Redstone Ore", "999", "59 min"}),
            Map.entry("report.reread_one_none", new String[] {"Deepslate Redstone Ore", "59 min"}),
            Map.entry("report.reread_many", new String[] {"99", "Deepslate Redstone Ore"}),
            Map.entry("report.too_far", new String[] {"8"}));

    @Test
    @DisplayName("the tuning ring's hub shows the whole radius, its cost, the depth and the note (review P0-2)")
    void ringHub() throws IOException {
        final JsonObject lang = lang();
        final int hub = hubWidth();
        final int cost = width(lang, "tuner.cost");
        final int range = width(lang, "tuner.range");
        assertTrue(range <= hub - 12 - cost, "the range row trims '" + text(lang, "tuner.range") + "' to "
                + (hub - 12 - cost) + " px beside the cost; it needs " + range);
        assertTrue(width(lang, "tuner.depth") <= hub - 30, "the depth line is trimmed at " + (hub - 30) + " px");
        assertTrue(width(lang, "tuner.band") <= hub - 10, "the note is trimmed at " + (hub - 10) + " px");
        assertTrue(width(lang, "tuner.no_ground") <= hub - 10, "the note is trimmed at " + (hub - 10) + " px");
    }

    @Test
    @DisplayName("no goggle line slides the goggle panel over the crosshair, and no reading outgrows it (P0-4)")
    void goggleLines() throws IOException {
        final JsonObject lang = lang();
        for (final String key : lang.keySet()) {
            if (!key.startsWith(P + "goggle.") && !key.startsWith(P + "report.")) {
                continue;
            }
            final String k = key.substring(P.length());
            final int w = RasterFont.widthOf(GOGGLE_PAD + text(lang, k));
            assertTrue(w <= GOGGLE_LINE, key + " is " + w + " GUI px with its pad, over " + GOGGLE_LINE + ": '"
                    + text(lang, k) + "'");
        }
    }

    private static JsonObject lang() throws IOException {
        return JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int hubWidth() throws IOException {
        final Matcher m = Pattern.compile("static final int HUB_W = (\\d+);")
                .matcher(Files.readString(SCREEN, StandardCharsets.UTF_8));
        assertTrue(m.find(), "TunerScreen no longer declares HUB_W: this test measures against it");
        return Integer.parseInt(m.group(1));
    }

    private static int width(JsonObject lang, String key) {
        return RasterFont.widthOf(text(lang, key));
    }

    private static String text(JsonObject lang, String key) {
        assertTrue(lang.has(P + key), "missing lang key " + P + key);
        final String raw = lang.get(P + key).getAsString();
        final String[] args = WIDEST.getOrDefault(key, new String[0]);
        final StringBuilder out = new StringBuilder();
        int next = 0;
        final Matcher m = Pattern.compile("%%|%(\\d+\\$)?s").matcher(raw);
        int at = 0;
        while (m.find()) {
            out.append(raw, at, m.start());
            if (m.group().equals("%%")) {
                out.append('%');
            } else {
                final int i = m.group(1) != null ? Integer.parseInt(m.group(1).replace("$", "")) - 1 : next++;
                assertTrue(i < args.length, key + " has a figure this test has no widest value for");
                out.append(args[i]);
            }
            at = m.end();
        }
        return out.append(raw.substring(at)).toString();
    }
}
