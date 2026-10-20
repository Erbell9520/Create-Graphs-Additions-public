package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartHomePlanSeamTest {
    private static final Path SCREEN = Path.of("src/main/java/com/createstats/gui/SmartHomePanelScreen.java");

    private static String source() throws IOException {
        return Files.readString(SCREEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String body(String src, String signature) {
        final int at = src.indexOf(signature);
        assertTrue(at >= 0, "no " + signature);
        final int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("unbalanced " + signature);
    }

    static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    @Test
    @DisplayName("the screen's lit lamps are chosen by LampLight.pools and dimmed by LampLight.level, never by its own arithmetic")
    void litLampsGoThroughTheRules() throws IOException {
        final String lit = stripComments(body(source(), "private List<PlanPainter.Lamp> litLamps("));
        assertTrue(lit.contains("LampLight.pools("), "litLamps no longer asks LampLight which lamps pool");
        assertTrue(lit.contains("LampLight.level("), "litLamps no longer asks LampLight how far the dimmer is turned");
        assertTrue(!lit.contains("State.ON"), "litLamps tests the state itself instead of asking LampLight.pools");
        assertTrue(!lit.contains("/"), "litLamps divides something itself instead of asking LampLight.level");
    }
}
