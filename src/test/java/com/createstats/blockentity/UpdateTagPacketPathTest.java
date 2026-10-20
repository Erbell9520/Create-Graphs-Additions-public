package com.createstats.blockentity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateTagPacketPathTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");

    @Test
    void everyHandWrittenUpdateTagIsAlsoReadOnThePacketPath() throws IOException {
        final List<String> unrouted = new ArrayList<>();
        for (final Path file : javaSources()) {
            final String source = read(file);
            final String body = bodyOf(source, "public void handleUpdateTag(");
            if (body == null || delegatesToLoad(body)) {
                continue;
            }
            if (!source.contains("void onDataPacket(")) {
                unrouted.add(file.getFileName().toString());
            }
        }

        assertTrue(unrouted.isEmpty(),
                "These block entities read keys in handleUpdateTag that loadAdditional does not "
                        + "read, but override no onDataPacket, so those keys are applied when the "
                        + "chunk arrives and never again: " + unrouted);
    }

    private static boolean delegatesToLoad(String body) {
        final String stripped = body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "")
                .replaceAll("\\s+", " ").trim();
        return stripped.isEmpty()
                || stripped.matches("(super\\.handleUpdateTag\\([^;]*\\);\\s*)?"
                        + "(this\\.)?loadAdditional\\([^;]*\\);");
    }

    private static String bodyOf(String source, String head) {
        final int at = source.indexOf(head);
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

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            final List<Path> found = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertTrue(found.size() > 100, "found suspiciously few sources: " + found.size());
            return found;
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
