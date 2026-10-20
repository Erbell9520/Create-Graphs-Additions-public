package com.createstats.baking;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvenMainsReadoutTest {
    private static final Path OVEN = Path.of("src/main/java/com/createstats/blockentity/OvenBE.java");

    private static final int WIDTH_BUDGET = 48;

    private static final Map<String, String> OVERLAY = new LinkedHashMap<>();

    static {
        OVERLAY.put("String mainsSummary(", "     1  Mains: ");
        OVERLAY.put("String cavitySummary(", "     2  Cavity: ");
        OVERLAY.put("String shelfSummary(", "     3  Shelf: ");
        OVERLAY.put("String bakeSummary(", "     4  Bake: ");
        OVERLAY.put("String nextAction(", "    ");
    }

    private static final String LITERAL = "\"((?:[^\"\\\\]|\\\\.)*)\"";

    @Test
    void mainsStateIsAnsweredOnTheServerAndCarriedOnTheWire() throws IOException {
        final String body = Files.readString(OVEN, StandardCharsets.UTF_8);

        final String wired = methodBody(body, "boolean isWired(");
        final String supply = methodBody(body, "boolean hasSupply(");
        assertNotNull(wired, "OvenBE.isWired() not found");
        assertNotNull(supply, "OvenBE.hasSupply() not found");

        assertTrue(wired.contains("isClientSide()") && wired.contains("clientWired"),
                "KI-43: isWired() must answer from the synced clientWired on a client. It reads:\n"
                        + wired);
        assertTrue(supply.contains("isClientSide()") && supply.contains("clientSupply"),
                "KI-43: hasSupply() must answer from the synced clientSupply on a client. It reads:\n"
                        + supply);
        assertTrue(!wired.contains("network") && !supply.contains("network"),
                "KI-43: neither predicate may touch the server-only FeWireNetwork directly. The "
                        + "server branch belongs in onGrid() and gridHasSupply(), where a client "
                        + "can never reach it");

        final String save = methodBody(body, "void saveAdditional(");
        final String load = methodBody(body, "void loadAdditional(");
        assertNotNull(save, "OvenBE.saveAdditional not found");
        assertNotNull(load, "OvenBE.loadAdditional not found");

        assertTrue(save.contains("putBoolean(\"Wired\"") && save.contains("putBoolean(\"Supply\""),
                "KI-43: saveAdditional must write both mains flags; getUpdateTag delegates to it");
        assertTrue(load.contains("clientWired = tag.getBoolean(\"Wired\")")
                        && load.contains("clientSupply = tag.getBoolean(\"Supply\")"),
                "KI-43: loadAdditional must read both flags back, or the client still draws false");

        final String beat = methodBody(body, "void heartbeat(");
        assertNotNull(beat, "OvenBE.heartbeat not found");

        assertTrue(beat.contains("wiredNow != syncedWired")
                        && beat.contains("supplyNow != syncedSupply"),
                "KI-43: a change of mains state must push. An idle oven with an empty buffer draws "
                        + "nothing and is not started, so no other term in heartbeat's gate fires "
                        + "and plugging it in would go unreported until something else did");
    }

    @Test
    void noOverlayLineIsWideEnoughToSitOverTheCrosshair() throws IOException {
        final String body = Files.readString(OVEN, StandardCharsets.UTF_8);
        final List<String> tooWide = new ArrayList<>();

        for (final Map.Entry<String, String> step : OVERLAY.entrySet()) {
            final String method = methodBody(body, step.getKey());
            assertNotNull(method, "OvenBE." + step.getKey() + " not found");
            final Matcher m = Pattern.compile(LITERAL).matcher(stripComments(method));
            while (m.find()) {
                final int width = step.getValue().length() + rendered(m.group(1));
                if (width > WIDTH_BUDGET) {
                    tooWide.add(step.getKey() + " renders " + width + " wide: \"" + m.group(1) + "\"");
                }
            }
        }

        assertTrue(tooWide.isEmpty(),
                "Goggle overlay lines over " + WIDTH_BUDGET + " characters. Create sizes the tooltip "
                        + "box to its widest line and draws it beside the crosshair:\n  "
                        + String.join("\n  ", tooWide));
    }

    private static int rendered(String literal) {
        return literal.replace("%,d", "x".repeat(5))
                .replace("%d", "x".repeat(4))
                .replace("%s", "x".repeat(5))
                .replace("%%", "x")
                .length();
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
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        return null;
    }

    private static String stripComments(String body) {
        return body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }
}
