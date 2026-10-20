package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiofuelValueTest {
    private static final Path DATA = Path.of("src/main/resources/data");

    private static final Path FURNACE_FUELS = DATA.resolve("neoforge/data_maps/item/furnace_fuels.json");

    private static final java.util.Set<String> BUILT_IN_MAPS = java.util.Set.of(
            "furnace_fuels.json", "compostables.json");

    @Test
    @DisplayName("a band beats both the composter and the furnace, whatever they claim")
    void theBandWins() {
        assertEquals(600, BiofuelValue.burnTicks(BiofuelGrade.LOG, BiofuelValue.NOT_COMPOSTABLE, 300),
                "a log, which a furnace prices the same as one of its own six planks");
        assertEquals(100, BiofuelValue.burnTicks(BiofuelGrade.PLANK, BiofuelValue.NOT_COMPOSTABLE, 300),
                "a plank, which is a sixth of that log and used to be all of it");
        assertEquals(360, BiofuelValue.burnTicks(BiofuelGrade.DENSE, 0.5f, 4001),
                "a dried kelp block: nine dried kelp, not four thousand ticks of anything");
        assertEquals(20, BiofuelValue.burnTicks(BiofuelGrade.GREEN, 0.65f, 100), "azalea");
    }

    @Test
    @DisplayName("sawing a log into planks cannot make FE out of nothing")
    void sawingWoodCreatesNoFe() {
        final int log = BiofuelValue.burnTicks(BiofuelGrade.LOG, BiofuelValue.NOT_COMPOSTABLE, 300);
        final int plank = BiofuelValue.burnTicks(BiofuelGrade.PLANK, BiofuelValue.NOT_COMPOSTABLE, 300);
        assertTrue(6 * plank <= log,
                "six planks (" + 6 * plank + ") are worth more than the log they were sawn from ("
                        + log + "), so a Mechanical Saw prints FE. KI-49.");
        assertTrue(4 * plank <= log, "and the crafting grid's four must not gain either");
    }

    @Test
    @DisplayName("an unbanded compostable is priced off its compost chance, near the band it resembles")
    void compostFallback() {
        assertEquals(65, BiofuelValue.burnTicks(null, 0.65f, 0), "a crop, against CROP's 80");
        assertEquals(30, BiofuelValue.burnTicks(null, 0.3f, 0), "grass or leaves, against GREEN's 20");
        assertEquals(100, BiofuelValue.burnTicks(null, 1.0f, 0), "a cake, if anyone burns one");
    }

    @Test
    @DisplayName("an unbanded furnace fuel is priced off its burn time, capped at a plank")
    void furnaceFallbackIsCapped() {
        assertEquals(60, BiofuelValue.burnTicks(null, BiofuelValue.NOT_COMPOSTABLE, 60),
                "under the cap, taken as it stands");
        assertEquals(BiofuelGrade.PLANK.burnTicks(),
                BiofuelValue.burnTicks(null, BiofuelValue.NOT_COMPOSTABLE, 20_000),
                "a lava bucket's worth of burn time is still only a plank here");
    }

    @Test
    @DisplayName("something the gate accepted with no data anywhere still burns")
    void theFloorHolds() {
        assertEquals(BiofuelValue.UNKNOWN_TICKS,
                BiofuelValue.burnTicks(null, BiofuelValue.NOT_COMPOSTABLE, 0));
        assertTrue(BiofuelValue.UNKNOWN_TICKS > 0, "a floor of zero is not a floor");
        assertEquals(BiofuelGrade.LOG.burnTicks(),
                BiofuelValue.burnTicks(BiofuelGrade.LOG, BiofuelValue.NOT_COMPOSTABLE, 0),
                "hellbark: no burn time, no compost value, and still a log");
    }

    @Test
    @DisplayName("a compost value too small to round to a tick still buys one")
    void nothingAcceptedBurnsForNoTime() {
        assertEquals(1, BiofuelValue.burnTicks(null, 0.001f, 0));
    }

    @Test
    @DisplayName("FE is duration times the one fixed rate, and it does not overflow")
    void totalIsDurationTimesRate() {
        assertEquals(300L * BiofuelValue.OUTPUT_FE_PER_TICK, BiofuelValue.totalFe(300));
        assertEquals(0L, BiofuelValue.totalFe(0));
        assertEquals(0L, BiofuelValue.totalFe(-5));

        assertTrue(BiofuelValue.totalFe(Integer.MAX_VALUE) > 0L);
    }

    @Test
    @DisplayName("a tick's output is clamped to the room in the buffer, never negative")
    void tickOutputIsClampedByRoom() {
        assertEquals(BiofuelValue.OUTPUT_FE_PER_TICK, BiofuelValue.tickOutput(100_000));
        assertEquals(BiofuelValue.OUTPUT_FE_PER_TICK,
                BiofuelValue.tickOutput(BiofuelValue.OUTPUT_FE_PER_TICK));
        assertEquals(7, BiofuelValue.tickOutput(7), "a nearly full buffer makes what fits");
        assertEquals(0, BiofuelValue.tickOutput(0), "a full buffer holds the fire");
        assertEquals(0, BiofuelValue.tickOutput(-40), "never negative");
    }

    @Test
    @DisplayName("the Compacted Biofuel constant matches the burn time the game will actually read")
    void theCompactedBrickAgreesWithItsDataMap() throws IOException {
        final Path map = FURNACE_FUELS;
        assertTrue(Files.exists(map), "the furnace fuel data map is missing: " + map);
        final String json = new String(Files.readAllBytes(map), StandardCharsets.UTF_8);
        final Matcher m = Pattern
                .compile("\"create_stats:compacted_biofuel\"\\s*:\\s*\\{[^}]*?\"burn_time\"\\s*:\\s*(\\d+)",
                        Pattern.DOTALL)
                .matcher(json);
        assertTrue(m.find(), "no burn_time for compacted_biofuel in " + map);
        assertEquals(BiofuelValue.COMPACTED_BURN_TICKS, Integer.parseInt(m.group(1)),
                "BiofuelValue.COMPACTED_BURN_TICKS and furnace_fuels.json disagree, so every readout "
                        + "quoting the constant is lying about what the item is worth");
    }

    @Test
    @DisplayName("every data map file sits where NeoForge's loader actually looks")
    void dataMapsAreWhereTheLoaderReadsThem() throws IOException {
        assertTrue(Files.exists(FURNACE_FUELS),
                "the brick's furnace_fuels.json must be at " + FURNACE_FUELS
                        + ", the map's own namespace, not this mod's");
        final java.util.List<String> bad = misplacedDataMaps(DATA);
        assertTrue(bad.isEmpty(), "data map files NeoForge will never read:\n" + String.join("\n", bad));
    }

    static java.util.List<String> misplacedDataMaps(Path data) throws IOException {
        final java.util.List<String> bad = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(data)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                final Path rel = data.relativize(file);
                int at = -1;
                for (int i = 0; i < rel.getNameCount(); i++) {
                    if (rel.getName(i).toString().equals("data_maps")) {
                        at = i;
                        break;
                    }
                }
                if (at < 0) {
                    continue;
                }
                final String shown = rel.toString().replace('\\', '/');
                if (at != 1) {
                    bad.add(shown + ": data_maps must be the directory right after the namespace");
                } else if (rel.getNameCount() < 4) {
                    bad.add(shown + ": needs a registry path between data_maps and the file");
                } else if (BUILT_IN_MAPS.contains(rel.getFileName().toString())
                        && !rel.getName(0).toString().equals("neoforge")) {
                    bad.add(shown + ": a built-in map belongs to the neoforge namespace");
                }
            }
        }
        return bad;
    }
}
