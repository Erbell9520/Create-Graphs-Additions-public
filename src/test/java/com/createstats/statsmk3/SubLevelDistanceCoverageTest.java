package com.createstats.statsmk3;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubLevelDistanceCoverageTest {
    private static final Pattern MEASURES = Pattern.compile(
            "\\b(?:distanceToSqr|distanceTo|closerThan|distSqr|distToCenterSqr)\\s*\\(");

    private static final Pattern RAW_POSITION = Pattern.compile(
            "getBlockPos\\s*\\(\\s*\\)|\\bmemberPos\\b|\\bworldPosition\\b");

    private static final Pattern PROJECTED = Pattern.compile("SableBridge\\s*\\.\\s*worldPointOf\\s*\\(");

    private static final Pattern REACH = Pattern.compile(
            "SableBridge\\s*\\.\\s*isPlayerInInteractionRangeFor\\s*\\(");

    @Test
    void theMk3FacePicksItsDetailFromWhereTheShipIs() throws IOException {
        assertMeasuresTheWorld("src/main/java/com/createstats/statsmk3/render/Mk3PanelRenderer.java",
                "public void render(Mk3PanelBlockEntity be");
    }

    @Test
    void thePanelReachCheckMeasuresWhereTheShipIs() throws IOException {
        assertMeasuresTheWorld("src/main/java/com/createstats/blockentity/StatsDisplayPanelBE.java",
                "public boolean isPlayerInInteractionRange(Player player)");
    }

    @Test
    void theInventoryDisplayPicksItsDetailFromWhereTheShipIs() throws IOException {
        assertMeasuresTheWorld("src/main/java/com/createstats/renderer/ProductionDetectorRenderer.java",
                "private static int band(ProductionDetectorBE be, BlockPos pos)");
    }

    @Test
    void theInventoryDisplaySettingsReachIsSableAware() throws IOException {
        assertReachIsSableAware("src/main/java/com/createstats/network/InventoryDisplaySettingsPacket.java",
                "public static void handle(InventoryDisplaySettingsPacket packet, IPayloadContext ctx)");
    }

    @Test
    void theInventoryDisplayResetReachIsSableAware() throws IOException {
        assertReachIsSableAware("src/main/java/com/createstats/network/DetectorResetPacket.java",
                "public static void handle(DetectorResetPacket packet, IPayloadContext ctx)");
    }

    private static void assertReachIsSableAware(String file, String signature) throws IOException {
        final String name = Path.of(file).getFileName().toString();
        final String found = methodBody(Files.readString(Path.of(file), StandardCharsets.UTF_8), signature);
        assertNotNull(found, name + " no longer declares " + signature + "; point this test at wherever "
                + "that reach check went");
        final String body = withoutComments(found);
        assertTrue(REACH.matcher(body).find(), name + ": " + signature + " has to check reach through "
                + "SableBridge.isPlayerInInteractionRangeFor, or a display on an Aero ship refuses every edit");
        assertFalse(MEASURES.matcher(body).find(), name + ": " + signature + " measures a distance itself; "
                + "the position it carries is Sable's plot on a ship");
    }

    private static void assertMeasuresTheWorld(String file, String signature) throws IOException {
        final String name = Path.of(file).getFileName().toString();
        final String found = methodBody(Files.readString(Path.of(file), StandardCharsets.UTF_8), signature);
        assertNotNull(found, name + " no longer declares " + signature + "; point this test at wherever "
                + "that measurement went");
        final String body = withoutComments(found);

        assertTrue(PROJECTED.matcher(body).find(), name + ": " + signature + " has to measure through "
                + "SableBridge.worldPointOf, or a panel on an Aero ship is measured to Sable's plot");

        final List<String> raw = new ArrayList<>();
        for (final String statement : body.split("[;{}]")) {
            if (MEASURES.matcher(statement).find() && RAW_POSITION.matcher(statement).find()) {
                raw.add(statement.strip().replaceAll("\\s+", " "));
            }
        }
        assertTrue(raw.isEmpty(), name + " measures a distance to a raw block position, which on an "
                + "Aero ship is Sable's plot:\n  " + String.join("\n  ", raw));
    }

    private static String withoutComments(String body) {
        return body.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
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
}
