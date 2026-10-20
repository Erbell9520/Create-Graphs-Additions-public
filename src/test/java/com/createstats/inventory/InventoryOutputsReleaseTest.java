package com.createstats.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryOutputsReleaseTest {
    private static final Path BE = Path.of("src/main/java/com/createstats/blockentity/ProductionDetectorBE.java");

    private static String source() throws IOException {
        final String raw = Files.readString(BE, StandardCharsets.UTF_8).replace("\r\n", "\n");

        return raw.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    @Test
    void theTickReleasesTheOutputsOnTheUnpoweredTransition() throws IOException {
        final String tick = body(source(), "public void tick()");
        assertNotNull(tick, "ProductionDetectorBE no longer declares tick(); point this test at the new place");
        assertTrue(tick.matches("(?s).*boolean\\s+powered\\s*=\\s*this\\.feConsumer\\.serviceTick\\(.*"),
                "tick() must take the powered state from the FE consumer's own service tick");
        final String unpowered = block(tick, "if (!powered)");
        assertNotNull(unpowered, "tick() has no `if (!powered)` branch: where are the outputs released?");
        assertTrue(unpowered.contains("releaseOutputs();"),
                "the unpowered branch of tick() must call releaseOutputs(): onFePowerLost has no caller in the tree");
        assertTrue(unpowered.contains("this.lastPowered"),
                "the release must be keyed to the transition (lastPowered), not repeated every unpowered tick");
        assertTrue(unpowered.contains("return;"),
                "an unpowered tick must stop after the release, or it would scan and drive the relays again");
    }

    @Test
    void theReleaseClearsEveryOutputAndTellsTheNeighbours() throws IOException {
        final String release = body(source(), "private void releaseOutputs()");
        assertNotNull(release, "ProductionDetectorBE no longer declares releaseOutputs()");
        for (String line : new String[]{"this.lowOut = false;", "this.highOut = false;", "this.comparator = 0;",
                "setPoweredState(false);", "notifyOutputs();"}) {
            assertTrue(release.contains(line), "releaseOutputs() no longer does `" + line + "`");
        }
    }

    @Test
    void anUnpoweredDisplayReportsNoSignalWhateverItRemembers() throws IOException {
        final String src = source();
        final String signal = body(src, "public int relaySignal(Direction towardReceiver)");
        assertNotNull(signal, "ProductionDetectorBE no longer declares relaySignal(Direction)");
        assertTrue(signal.replaceAll("\\s+", " ").contains("if (!this.lastPowered) { return 0; }"),
                "relaySignal must answer 0 while unpowered");
        final String comparator = body(src, "public int comparatorLevel()");
        assertNotNull(comparator, "ProductionDetectorBE no longer declares comparatorLevel()");
        assertTrue(comparator.replaceAll("\\s+", " ").contains("this.lastPowered ? this.comparator : 0"),
                "comparatorLevel must answer 0 while unpowered");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        return at < 0 ? null : braces(source, source.indexOf('{', at));
    }

    private static String block(String source, String head) {
        final int at = source.indexOf(head);
        return at < 0 ? null : braces(source, source.indexOf('{', at));
    }

    private static String braces(String source, int open) {
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
