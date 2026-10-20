package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAdjacencyGuardTest {
    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");

    private static String network() throws IOException {
        return Files.readString(NETWORK, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the connector branch still joins spans and still runs the touching-cable spread")
    void theConnectorBranchStillWiresSpansAndCable() throws IOException {
        final String network = network();
        final int branch = network.indexOf("node instanceof PowerConnectorBE connector");
        assertTrue(branch >= 0, "the flood has no Power Connector branch; this guard has lost its anchor");

        final int chainEnd = network.indexOf("} else if", branch);
        assertTrue(chainEnd > branch, "the walk's shape changed; this guard can no longer find its end");
        final String body = network.substring(branch, chainEnd);
        assertTrue(body.contains("walkTerminalLinks(connector"),
                "the connector branch must still walk its copper wire spans, or two connectors a "
                        + "player deliberately wired together would stop sharing as well");
        assertTrue(body.contains("enqueueTouchingCable("),
                "the connector branch must still run the touching-cable spread, or a connector would "
                        + "stop meeting the cable it is placed against");
    }

    @Test
    @DisplayName("the connector-origin spread does not pick up a bare-adjacent connector")
    void theConnectorSpreadExcludesConnectors() throws IOException {
        final String network = network();
        final int method = network.indexOf("private static void enqueueTouchingCable");
        assertTrue(method >= 0, "enqueueTouchingCable is gone; the connector spread has moved");

        final int methodEnd = network.indexOf("private static", method + 1);
        assertTrue(methodEnd > method, "could not find the end of enqueueTouchingCable");
        final String body = network.substring(method, methodEnd);
        assertTrue(body.contains("spreadFromBlock(level, at, walk, true, false, false)"),
                "enqueueTouchingCable must spread with includeConnectors=false (the final argument), "
                        + "so a connector does not fuse a connector set down beside it. It is the only "
                        + "spread that starts at a connector, so it is the only place the flag is false.");
    }

    @Test
    @DisplayName("the face-adjacency clause is gated on the includeConnectors flag")
    void theAdjacencyClauseIsGated() throws IOException {
        final String network = network();
        assertTrue(network.contains("includeConnectors && be instanceof PowerConnectorBE"),
                "spreadFromBlock must gate its Power Connector arm on includeConnectors. Without the "
                        + "guard the flag is dead and bare-adjacent connectors fuse again: KI-23 "
                        + "reopened with the call site still passing false and nothing reading it.");
    }
}
