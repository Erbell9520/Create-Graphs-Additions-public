package com.createstats.power;

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

class GoggleSyncCoverageTest {
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/blockentity");

    private static final Pattern READS = Pattern.compile(
            "FeGoggle\\.(?:stored|draw)\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*[,)]");

    @Test
    void everyGoggleFieldRidesTheUpdateTag() throws IOException {
        final List<String> stale = new ArrayList<>();

        for (final Path source : sources()) {
            final String body = Files.readString(source, StandardCharsets.UTF_8);
            if (!body.contains("addToGoggleTooltip")) {
                continue;
            }
            final String updateTag = methodBody(body, "CompoundTag getUpdateTag(");
            if (updateTag == null || updateTag.contains("saveAdditional(")) {
                continue;
            }
            final String handle = methodBody(body, "void handleUpdateTag(");
            final String name = source.getFileName().toString().replace(".java", "");

            for (final String field : fieldsRead(body)) {
                if (!updateTag.contains(field)) {
                    stale.add(name + "." + field + " is drawn by the goggle overlay and never put on "
                            + "getUpdateTag, so it reads as zero on every client");
                } else if (handle == null || !handle.contains(field)) {
                    stale.add(name + "." + field + " is put on getUpdateTag and never read back in "
                            + "handleUpdateTag, so the client still draws zero");
                }
            }
        }

        assertTrue(stale.isEmpty(),
                "Goggle fields that never reach the client:\n  " + String.join("\n  ", stale));
    }

    private static Set<String> fieldsRead(String body) {
        final Set<String> fields = new LinkedHashSet<>();
        final Matcher m = READS.matcher(body);
        while (m.find()) {
            final String read = m.group(1);

            if (!read.equals(read.toUpperCase(java.util.Locale.ROOT))) {
                fields.add(read);
            }
        }
        return fields;
    }

    private static String methodBody(String source, String signature) {
        final int at = source.indexOf(signature);
        if (at < 0) {
            return null;
        }
        final int open = source.indexOf('{', at);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        return null;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(BLOCK_ENTITIES)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }
}
