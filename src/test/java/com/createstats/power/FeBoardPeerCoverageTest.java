package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeBoardPeerCoverageTest {
    private static final Path PDU =
            Path.of("src/main/java/com/createstats/blockentity/PowerDistributionUnitBE.java");
    private static final Path CONTROLLER =
            Path.of("src/main/java/com/createstats/blockentity/BatteryControllerBE.java");

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String body(String src, String signature) {
        final int from = src.indexOf(signature);
        assertTrue(from > 0, signature + " is gone or has been renamed");
        int end = src.indexOf("\n    }", from);
        assertTrue(end > from, "cannot find the end of " + signature);
        return src.substring(from, end);
    }

    @Test
    @DisplayName("both board kinds declare themselves boards")
    void bothBoardsArePeers() throws IOException {
        assertTrue(read(PDU).contains("com.createstats.power.FeBoardPeer,"),
                "PowerDistributionUnitBE must implement FeBoardPeer");
        assertTrue(read(CONTROLLER).contains("com.createstats.power.FeBoardPeer,"),
                "BatteryControllerBE must implement FeBoardPeer");
    }

    @Test
    @DisplayName("both answer every half of the conversation")
    void bothAnswerTheWholeInterface() throws IOException {
        for (Path file : new Path[]{PDU, CONTROLLER}) {
            final String src = read(file);
            for (String member : new String[]{
                    "public void acceptExportReport(",
                    "public com.createstats.power.FeReserveReport boardReserveReport()",
                    "public void notePushFrom(",
                    "public int declaredCriticalDemandFePerTick()",

                    "public void acceptHoldTarget("}) {
                assertTrue(src.contains(member),
                        file.getFileName() + " is missing " + member
                                + ", so half the chain can talk and half cannot");
            }
        }
    }

    @Test
    @DisplayName("the critical split asks the interface, never a class name")
    void theSplitAsksTheInterface() throws IOException {
        final String method = body(read(PDU), "private int criticalDemandOf(FeGridNode node) {");
        assertTrue(method.contains("boardPeerOf(node)"),
                "criticalDemandOf must resolve through FeBoardPeer");
        assertTrue(!method.contains("instanceof BatteryControllerBE")
                        && !method.contains("instanceof PowerDistributionUnitBE"),
                "a class name here is the second chain growing back");
    }

    @Test
    @DisplayName("a board on a leg is filed as one, whichever way the leg found it")
    void bothDiscoveryPathsFileTheBoard() throws IOException {
        final String src = read(PDU);

        assertEquals(2, calls(src, "noteBoardNode("),
                "noteBoardNode must be called from both the grid walk and collectTerminalBoards");
        final String note = body(src,
                "private void noteBoardNode(FeGridNode node, int leg, @Nullable BlockPos at) {");
        assertTrue(note.contains("boardPeerOf(node)"), "the filing test must be the interface");
        assertTrue(note.contains("boardNodes.add") && note.contains("branchBoards.add")
                        && note.contains("legCarriesBoard"),
                "all three collections or the serve, the conversation and the ceiling disagree");
    }

    @Test
    @DisplayName("the export allowance is booked in every round that can move FE to a board")
    void everyRoundBooks() throws IOException {
        final String src = read(PDU);

        assertEquals(calls(src, "gateBoardsToSpare("), calls(src, "bookBoardExport("),
                "every gate needs its paired booking, the same rule as legRoom and spendLeg");
        assertEquals(4, calls(src, "gateBoardsToSpare("),
                "Active Auto's tier rounds, the fill pass, and both the Off and Manual rounds");
    }

    @Test
    @DisplayName("the hold target is actually sent, and only when the board is short")
    void theHoldTargetIsSentFromTheConversation() throws IOException {
        final String method = body(read(PDU), "private void talkToBranchBoards() {");

        assertTrue(method.contains("peer.acceptHoldTarget("),
                "talkToBranchBoards must actually ask its peers to hold, or the member is dead code");

        assertTrue(method.contains("outlook.isShort()"),
                "the hold must be sent only while the projection is short");
    }

    @Test
    @DisplayName("the CRITICAL round is never gated")
    void criticalIsNeverGated() throws IOException {
        final String src = read(PDU);
        assertTrue(src.contains("? List.of() : gateBoardsToSpare("),
                "a board's declared CRITICAL is branch cost and must not be held to the spare gate");
    }

    @Test
    @DisplayName("KI-25: a board is found anywhere on the stud's grid, not only the one connector it lands on")
    void boardDiscoveryWalksTheWholeGrid() throws IOException {
        final String method = body(read(PDU),
                "private void collectTerminalBoards(int leg, List<LegGrid> grids,");
        assertTrue(method.contains("grid.network().servedMembers()"),
                "collectTerminalBoards must walk each reachable FeWireNetwork, not just the one "
                        + "connector the stud lands on directly, or a controller two or more "
                        + "connectors out is invisible to it again");
        assertTrue(method.contains("inputPortLinkedTo("),
                "collectTerminalBoards must resolve which input port it found, for KI-26");
    }

    @Test
    @DisplayName("KI-26: a push is attributed to the specific input terminal it landed on")
    void pushIsAttributedToItsPort() throws IOException {
        final String controller = read(CONTROLLER);
        assertTrue(controller.contains("public IEnergyStorage terminalInputHandle(int port)"),
                "terminalInputHandle must be per port, or a push cannot be told which terminal it fed");
        final String stray = body(controller, "private void findStrayPanelInputs() {");
        assertTrue(stray.contains("inputFlow[port] > 0"),
                "findStrayPanelInputs must exempt a port a push has already reached, or a terminal "
                        + "genuinely carrying FE still reads \"panel leg, carries nothing\"");
    }

    @Test
    @DisplayName("KI-28: terminal board flow reaches conduit reporting and survives inline boundaries")
    void terminalBoardFlowFollowsItsLegAndInstruments() throws IOException {
        final String pdu = read(PDU);
        final String discovery = body(pdu,
                "private void collectTerminalBoards(int leg, List<LegGrid> grids,");
        assertTrue(discovery.contains("for (LegGrid grid : grids)")
                        && discovery.contains("grid.instruments()"),
                "terminal boards must be discovered on every LegGrid and retain each boundary chain, "
                        + "or a meter or limiter hides the controller again");

        final String reporting = body(pdu,
                "private void reportFlowsTo(FeWireNetwork network, long anchorKey, int anchorSupply) {");
        assertTrue(reporting.contains("terminalBoardOwnerByNode")
                        && reporting.contains("terminalBoardNetworkByNode"),
                "a terminal board's virtual attachment must become a seed on its owning grid, "
                        + "or cables and diagnostic junctions report zero while it charges");

        final String beyond = body(pdu,
                "private int deliveredBeyond(BlockPos boxPos, com.createstats.power.FeInlineInstrument box) {");
        assertTrue(beyond.contains("terminalBoardNetworkByNode"),
                "an inline meter or limiter must include a terminal board beyond it in its booked flow");
    }

    @Test
    @DisplayName("KI-29: a terminal board is rated by its own wire and conduit, never the bare stud")
    void terminalBoardRateIsItsPathNotTheStud() throws IOException {
        final String discovery = body(read(PDU),
                "private void collectTerminalBoards(int leg, List<LegGrid> grids,");
        final java.util.List<String> args = argumentLists(discovery, "addTerminalBoard(");
        assertEquals(2, args.size(), "expected the direct-wire and the grid call sites");
        for (String list : args) {
            assertTrue(!list.contains("studRate()"),
                    "addTerminalBoard must be given the path's rating, not the bare stud: " + list);
        }
        assertTrue(discovery.contains("grid.pathCeiling()"),
                "a board on a grid must be held to the lowest conduit between the stud and it");
        assertTrue(discovery.contains("linkCeilingTo(leg, grid.anchorKey())"),
                "a board on a grid must be held to the wire that reaches that grid");
    }

    @Test
    @DisplayName("KI-30: a box further down a leg clamps the leg's root, where the ceiling is read")
    void seriesClampLandsOnTheRoot() throws IOException {
        final String follow = body(read(PDU),
                "private void followInstruments(List<LegGrid> found, int from,");
        assertTrue(follow.contains("found.set(from,"),
                "the series clamp must be written to the root entry");
        assertTrue(!follow.contains("found.set(at,"),
                "clamping the entry being continued loses every clamp past the first box");
        assertTrue(follow.contains("here.pathCeiling()"),
                "a continuation's path must include everything upstream of it");
    }

    private static java.util.List<String> argumentLists(String src, String call) {
        final String code = src.replaceAll("(?m)//[^\\n]*", "");
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (int at = code.indexOf(call); at >= 0; at = code.indexOf(call, at + 1)) {
            int depth = 1;
            int end = at + call.length();
            while (end < code.length() && depth > 0) {
                final char c = code.charAt(end++);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
            }
            out.add(code.substring(at + call.length(), end - 1));
        }
        return out;
    }

    private static int calls(String src, String needle) {
        int n = 0;
        for (String line : src.split("\n")) {
            final String trimmed = line.strip();
            if (!trimmed.contains(needle) || trimmed.startsWith("private") || trimmed.startsWith("*")
                    || trimmed.startsWith("//") || trimmed.startsWith("return " + needle)) {
                continue;
            }
            n++;
        }
        return n;
    }
}
