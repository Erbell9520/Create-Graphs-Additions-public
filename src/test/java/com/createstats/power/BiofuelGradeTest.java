package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiofuelGradeTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/create_stats/recipe");
    private static final Path TAGS = Path.of("src/main/resources/data/create_stats/tags/item/biofuel");

    private static final Set<BiofuelGrade> PRESSES = EnumSet.of(BiofuelGrade.GREEN,
            BiofuelGrade.FIBRE, BiofuelGrade.CROP, BiofuelGrade.PLANK);

    @Test
    @DisplayName("a Mechanical Saw's six planks are never worth more than the log they came from")
    void sawingWoodCreatesNothing() {
        assertTrue(6 * BiofuelGrade.PLANK.burnTicks() <= BiofuelGrade.LOG.burnTicks(),
                "six planks are worth more than the log: a saw prints FE. KI-49.");
    }

    @Test
    @DisplayName("vanilla's nine-to-one blocks are worth exactly their nine")
    void vanillaPressesAreNeutral() {
        assertEquals(9 * BiofuelGrade.GREEN.burnTicks(), BiofuelGrade.PACKED.burnTicks(),
                "a melon is nine melon slices");
        assertEquals(9 * BiofuelGrade.FIBRE.burnTicks(), BiofuelGrade.DENSE.burnTicks(),
                "a dried kelp block is nine dried kelp");
        assertEquals(9 * BiofuelGrade.CROP.burnTicks(), BiofuelGrade.BALE.burnTicks(),
                "a hay bale is nine wheat");
    }

    @Test
    @DisplayName("every split recipe with a band on both ends loses, or at worst breaks even")
    void splittingLosesOrBreaksEven() {
        assertTrue(4 * BiofuelGrade.GREEN.burnTicks() <= BiofuelGrade.CROP.burnTicks(),
                "a pumpkin cut into four pumpkin seeds");
        assertTrue(3 * BiofuelGrade.GREEN.burnTicks() <= BiofuelGrade.PACKED.burnTicks(),
                "a moss block cut into three moss carpets");
        assertTrue(3 * BiofuelGrade.GREEN.burnTicks() <= BiofuelGrade.CROP.burnTicks(),
                "a fleece of wool cut into three carpets");
        assertTrue(2 * BiofuelGrade.PLANK.burnTicks() <= BiofuelGrade.DENSE.burnTicks(),
                "a bamboo block sawn into two bamboo planks");
        assertTrue(4 * BiofuelGrade.PLANK.burnTicks() <= BiofuelGrade.LOG.burnTicks(),
                "a log crafted by hand into four planks");
    }

    @Test
    @DisplayName("every band presses at the same eleven percent, and none of them rounds")
    void thePressBonusIsUniform() {
        for (BiofuelGrade grade : BiofuelGrade.values()) {
            assertEquals(0, grade.burnTicks() % BiofuelGrade.GREEN.burnTicks(),
                    grade + " is not a whole number of cuttings, so pressedBricks() rounds and the "
                            + "press quietly gains or loses on it");
        }
        for (BiofuelGrade grade : PRESSES) {
            final long in = (long) BiofuelGrade.PRESS_BATCH * grade.burnTicks();
            final long out = (long) grade.pressedBricks() * BiofuelGrade.PRESSED.burnTicks();
            assertEquals(10L * in, 9L * out,
                    grade + " presses at " + out + " ticks out of " + in + " in, which is not the "
                            + "10/9 every other band gets");
        }
    }

    @Test
    @DisplayName("a band presses only while pressing it actually compacts anything")
    void onlyBulkPresses() {
        for (BiofuelGrade grade : BiofuelGrade.values()) {
            assertEquals(PRESSES.contains(grade), grade.compacts(),
                    grade + " disagrees with the recipes on whether it presses. Nine of it would be "
                            + grade.pressedBricks() + " bricks.");
        }
        assertTrue(BiofuelGrade.LOG.pressedBricks() > BiofuelGrade.PRESS_BATCH,
                "nine logs must come out as more items than went in, or the reason a log has no "
                        + "press recipe has stopped being true");
    }

    @Test
    @DisplayName("the brick is ten cuttings, which is the number the whole press is built on")
    void theBrickIsTenCuttings() {
        assertEquals(10 * BiofuelGrade.GREEN.burnTicks(), BiofuelGrade.PRESSED.burnTicks());
        assertEquals(BiofuelValue.COMPACTED_BURN_TICKS, BiofuelGrade.PRESSED.burnTicks());
    }

    @Test
    @DisplayName("the shipped compacting recipes are the ladder's own numbers")
    void theRecipesMatchTheLadder() throws IOException {
        final List<String> wrong = new ArrayList<>();
        for (BiofuelGrade grade : BiofuelGrade.values()) {
            final String band = grade.name().toLowerCase(java.util.Locale.ROOT);
            final Path recipe = RECIPES.resolve("compacted_biofuel_from_" + band + ".json");
            if (!grade.compacts()) {
                if (Files.exists(recipe)) {
                    wrong.add(band + " does not compact, and yet ships a press recipe: " + recipe);
                }
                continue;
            }
            if (!Files.exists(recipe)) {
                wrong.add(band + " compacts and has no recipe, so the press silently refuses it: "
                        + recipe);
                continue;
            }
            final String json = Files.readString(recipe, StandardCharsets.UTF_8);
            final int slots = countOf(json, "\"create_stats:biofuel/" + band + "\"");
            if (slots != BiofuelGrade.PRESS_BATCH) {
                wrong.add(band + " takes " + slots + " items, not " + BiofuelGrade.PRESS_BATCH
                        + ", so its bonus is not the one pressedBricks() assumes");
            }
            final Matcher count = Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(json);
            final int made = count.find() ? Integer.parseInt(count.group(1)) : 1;
            if (made != grade.pressedBricks()) {
                wrong.add(band + " presses into " + made + " bricks, and the ladder says "
                        + grade.pressedBricks() + ". At " + BiofuelGrade.PRESSED.burnTicks()
                        + " ticks each that is " + made * BiofuelGrade.PRESSED.burnTicks()
                        + " out of " + BiofuelGrade.PRESS_BATCH * grade.burnTicks() + " in.");
            }
        }
        assertTrue(wrong.isEmpty(), "the press and the ladder disagree:\n" + String.join("\n", wrong));
    }

    @Test
    @DisplayName("every grade has the band tag its name builds, or the band matches nothing at all")
    void everyGradeHasItsTag() {
        final List<String> missing = new ArrayList<>();
        for (BiofuelGrade grade : BiofuelGrade.values()) {
            final Path tag = TAGS.resolve(grade.name().toLowerCase(java.util.Locale.ROOT) + ".json");
            if (!Files.exists(tag)) {
                missing.add(grade + " -> " + tag);
            }
        }
        assertTrue(missing.isEmpty(),
                "these grades name a tag file that does not exist, so nothing is ever in them and "
                        + "every item they should have priced falls through to the composter:\n"
                        + String.join("\n", missing));
    }

    @Test
    @DisplayName("no item can be in two bands, or its press recipe depends on iteration order")
    void bandsAreDisjoint() throws IOException {
        final java.util.Map<String, String> owner = new java.util.HashMap<>();
        final List<String> clashes = new ArrayList<>();
        for (BiofuelGrade grade : BiofuelGrade.values()) {
            final String band = grade.name().toLowerCase(java.util.Locale.ROOT);
            final String json = Files.readString(TAGS.resolve(band + ".json"), StandardCharsets.UTF_8);
            final Matcher entry = Pattern.compile("\"(#?[a-z0-9_.-]+:[a-z0-9_./-]+)\"").matcher(json);
            while (entry.find()) {
                final String id = entry.group(1);
                final String had = owner.put(id, band);
                if (had != null) {
                    clashes.add(id + " is named by both " + had + " and " + band);
                }
            }
        }
        for (java.util.Map.Entry<String, String> a : owner.entrySet()) {
            if (!a.getKey().startsWith("#")) {
                continue;
            }
            for (java.util.Map.Entry<String, String> b : owner.entrySet()) {
                if (a.getValue().equals(b.getValue()) || !b.getKey().startsWith("#")) {
                    continue;
                }
                if (b.getKey().startsWith(a.getKey() + "/")) {
                    clashes.add(b.getKey() + " in " + b.getValue() + " is inside " + a.getKey()
                            + " in " + a.getValue() + ", so every item in it is in two bands");
                }
            }
        }
        assertTrue(clashes.isEmpty(),
                "these bands overlap, so an item in the overlap has two press recipes with "
                        + "different output counts and gets whichever Create reaches first:\n"
                        + String.join("\n", clashes));
    }

    private static int countOf(String text, String needle) {
        return text.split(Pattern.quote(needle), -1).length - 1;
    }
}
