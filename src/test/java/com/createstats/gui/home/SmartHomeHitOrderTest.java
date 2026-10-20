package com.createstats.gui.home;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartHomeHitOrderTest {
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

    private static int hitAdded(String body, String kind) {
        int from = 0;
        while (true) {
            final int add = body.indexOf("hits.add(new Hit(", from);
            if (add < 0) {
                return -1;
            }
            final int end = body.indexOf(");", add);
            if (end > add && body.substring(add, end).contains("Kind." + kind + ",")) {
                return add;
            }
            from = add + 1;
        }
    }

    @Test
    @DisplayName("a row's slot hit is added after the row's own, so a click on the slot switches the fixture")
    void slotWinsOverRow() throws IOException {
        final String row = body(source(), "private int drawRow(");
        final int open = hitAdded(row, "ROW_OPEN");
        final int toggle = hitAdded(row, "ROW_TOGGLE");
        assertTrue(open >= 0, "the row's own hit is gone from drawRow");
        assertTrue(toggle >= 0, "the slot's switch hit is gone from drawRow");
        assertTrue(toggle > open, "the slot's hit must be added after the row's: hits resolve last first");
    }

    @Test
    @DisplayName("clicks resolve from the last hit added, which is what the order above relies on")
    void clicksResolveLastFirst() throws IOException {
        final String click = body(source(), "public boolean mouseClicked(");
        assertTrue(click.contains("for (int i = hits.size() - 1; i >= 0; i--)"),
                "mouseClicked no longer walks the hits from the last one added");
    }
}
