package com.createstats.client;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PonderSchematicTest {
    private static final Path PLUGIN =
            Path.of("src/main/java/com/createstats/client/CreateStatsPonderPlugin.java");
    private static final Path PONDER =
            Path.of("src/main/resources/assets/create_stats/ponder");

    private static final Pattern STORYBOARD =
            Pattern.compile("addStoryBoard\\s*\\([^,]+,\\s*\"([^\"]+)\"");

    @Test
    void everyRegisteredSceneNamesASchematicThatExists() throws IOException {
        final List<String> paths = schematicPaths();
        assertFalse(paths.isEmpty(), "found no addStoryBoard registrations at all; has the "
                + "registration call been renamed? This test is then checking nothing.");

        final List<String> missing = new ArrayList<>();
        for (String path : new LinkedHashSet<>(paths)) {
            if (!Files.isRegularFile(PONDER.resolve(path + ".nbt"))) {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(),
                "these scenes are registered against schematics that do not exist, so they will open "
                        + "with no world at all and no error to say so: " + missing
                        + ". Host on an existing one, the way every setBlock-built scene here does.");
    }

    @Test
    void reportsSchematicsNoSceneReferences() throws IOException {
        final Set<String> used = new LinkedHashSet<>(schematicPaths());
        final List<String> unused = new ArrayList<>();
        try (var files = Files.walk(PONDER)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                final String name = file.getFileName().toString();
                if (!name.endsWith(".nbt")) {
                    continue;
                }
                final String rel = PONDER.relativize(file).toString()
                        .replace('\\', '/')
                        .replace(".nbt", "");
                if (!used.contains(rel)) {
                    unused.add(rel);
                }
            }
        }
        if (!unused.isEmpty()) {
            System.out.println("[ponder] schematics no scene references (" + unused.size() + "): "
                    + unused);
        }
    }

    private static List<String> schematicPaths() throws IOException {
        final String source = Files.readString(PLUGIN, StandardCharsets.UTF_8);
        final List<String> out = new ArrayList<>();
        final Matcher m = STORYBOARD.matcher(source);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
