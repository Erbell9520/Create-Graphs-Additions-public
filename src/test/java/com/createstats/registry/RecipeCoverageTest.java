package com.createstats.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeCoverageTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path RECIPES = Path.of("src/main/resources/data/create_stats/recipe");

    private static final Pattern REGISTRATION =
            Pattern.compile("registrate\\(\\)\\s*\\.(block|item)\\(\\s*\"([a-z0-9_]+)\"");
    private static final Pattern RESULT =
            Pattern.compile("\"result\"\\s*:\\s*\\{[^}]*?\"id\"\\s*:\\s*\"create_stats:([a-z0-9_]+)\"", Pattern.DOTALL);
    private static final Pattern RESULTS = Pattern.compile("\"results\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern OURS = Pattern.compile("create_stats:([a-z0-9_]+)");

    private static final Map<String, String> ELSEWHERE = Map.ofEntries(
            Map.entry("blueberry", "picked from a Blueberry Bush, which generates in the world"),
            Map.entry("cinnamon", "harvested from a Cinnamon Shrub, which generates in the world"),
            Map.entry("strawberry", "picked from strawberry plants, which generate in the world"),
            Map.entry("vanilla_bean", "picked from a Vanilla Bean Vine, which generates in the world"),
            Map.entry("toast", "made by the Toaster, in code"),
            Map.entry("slag", "the Arc Furnace's by-product"),
            Map.entry("suction_wand", "made by a Vacuum Cleaner when its hose is taken"),
            Map.entry("support_terminal", "the mod author's tooling, handed out by command"),
            Map.entry("creative_main_breaker", "creative only, as Create's creative blocks are"),
            Map.entry("stats_recorder", "retired, kept so existing worlds keep their blocks"),
            Map.entry("memory_cog", "retired, kept so existing worlds keep their items"),
            Map.entry("flour", "retired for Create's Wheat Flour, kept so existing stacks can be crafted into it"),
            Map.entry("vcc_mk2", "experimental, hidden unless the config opts in"),
            Map.entry("hinge", "experimental, hidden unless the config opts in"),
            Map.entry("conductors_lens", "experimental, hidden unless the config opts in"));

    @Test
    @DisplayName("every registered item is made by a recipe, or is listed with the way it is obtained")
    void everyItemIsMade() {
        final Set<String> items = new TreeSet<>();
        for (Path file : files(MAIN, ".java")) {
            final String src = read(file);
            final Matcher m = REGISTRATION.matcher(src);
            while (m.find()) {
                final int end = src.indexOf(';', m.end());
                final String rest = src.substring(m.end(), end < 0 ? src.length() : end);
                if (m.group(1).equals("item") || rest.contains("simpleItem()") || rest.contains(".item(")) {
                    items.add(m.group(2));
                }
            }
        }
        assertTrue(items.size() > 100, "the registration scan found only " + items.size() + " items");

        final Set<String> made = new TreeSet<>();
        for (Path file : files(RECIPES, ".json")) {
            final String json = read(file);
            final Matcher one = RESULT.matcher(json);
            while (one.find()) {
                made.add(one.group(1));
            }
            final Matcher many = RESULTS.matcher(json);
            while (many.find()) {
                final Matcher id = OURS.matcher(many.group(1));
                while (id.find()) {
                    made.add(id.group(1));
                }
            }
        }

        final Set<String> unmade = new TreeSet<>(items);
        unmade.removeAll(made);
        unmade.removeAll(ELSEWHERE.keySet());
        assertTrue(unmade.isEmpty(), "items no recipe makes and nothing else gives a survival player "
                + "(add a recipe, or an ELSEWHERE entry saying how it is obtained): " + unmade);

        final Set<String> stale = new TreeSet<>(ELSEWHERE.keySet());
        stale.retainAll(made);
        assertTrue(stale.isEmpty(), "ELSEWHERE lists items a recipe now makes; remove them: " + stale);
    }

    private static List<Path> files(Path root, String suffix) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(suffix)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
