package com.createstats.lang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LangKeyCoverageTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path LANG_FILE =
            Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path ASSETS_ROOT = Path.of("src/main/resources/assets");

    private static final Pattern TRANSLATABLE = Pattern.compile(
            "Component\\.translatable\\(\\s*\"([^\"]*)\"(\\s*\\+\\s*[^,)]+)?");

    private static final Pattern LANG_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    @Test
    void everyTranslatableKeyReachesTheLangFile() throws IOException {
        final Set<String> known = knownKeys();
        final List<String> broken = new ArrayList<>();

        for (final Path source : sources()) {
            final String body = Files.readString(source, StandardCharsets.UTF_8);
            final String name = SOURCE_ROOT.relativize(source).toString();
            final Matcher m = TRANSLATABLE.matcher(body);
            while (m.find()) {
                final String literal = m.group(1);
                if (literal.isEmpty() || !literal.contains("create_stats")) {
                    continue;
                }
                if (m.group(2) != null) {
                    if (known.stream().noneMatch(k -> k.startsWith(literal))) {
                        broken.add(name + ": \"" + literal + "\" + ... has no en_us.json key under "
                                + "that prefix, so the concatenated key prints raw in game");
                    }
                } else if (!known.contains(literal)) {
                    broken.add(name + ": \"" + literal + "\" is not in en_us.json, so it prints raw "
                            + "in game");
                }
            }
        }

        assertTrue(broken.isEmpty(),
                "Translation keys that reach no lang entry:\n  " + String.join("\n  ", broken));
    }

    private static Set<String> knownKeys() throws IOException {
        final Set<String> keys = new LinkedHashSet<>();
        final List<Path> files = new ArrayList<>();
        files.add(LANG_FILE);
        try (Stream<Path> namespaces = Files.list(ASSETS_ROOT)) {
            namespaces.map(ns -> ns.resolve("lang/en_us.json"))
                    .filter(Files::isRegularFile)
                    .filter(f -> !f.equals(LANG_FILE))
                    .sorted()
                    .forEach(files::add);
        }
        for (Path file : files) {
            final Matcher m = LANG_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (m.find()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
