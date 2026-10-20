package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkCrownStationLinkTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");

    private static String read(String relative) throws IOException {
        final Path p = MAIN.resolve(relative);
        assertTrue(Files.exists(p), "missing source: " + p);
        return code(Files.readString(p, StandardCharsets.UTF_8));
    }

    private static String code(String text) {
        return text.replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + signature);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(at, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    @Test
    @DisplayName("KI-183: the screen takes stations after the legs' own devices and never offers one to a leg in full")
    void theScreenNeverTripsForAStation() throws IOException {
        final String screen = body(read("power/PduLegPlan.java"), "public static Screened screen(");
        final int singles = screen.indexOf("for (int leg = 0; leg < legCount; leg++) {\n            final List<Device> here");
        final int stations = screen.indexOf("for (Device d : parallel) {");
        assertTrue(singles >= 0 && stations > singles,
                "stations are screened after every leg's own devices, out of what those leave");
        final String station = body(screen.substring(stations), "for (Device d : parallel) {");
        assertTrue(station.contains("split(demand, feeds, spare)"), "a station is fed what its legs have spare");
        assertFalse(station.contains("legDemand[d.leg()] += demand"),
                "what a station is not fed must never be offered to a leg, or the leg reads at capacity and trips");
        assertTrue(station.contains("legDemand[leg] += share[leg];") && station.contains("legAdmitted[leg] += share[leg];"),
                "a leg is charged a station's share, offered and admitted alike");
        assertTrue(screen.contains("if (d.station()) {"), "a station is told apart by its flag, on one stud or two");
    }

    @Test
    @DisplayName("KI-183: the board finds a crown on every leg, tags it a station, serves each leg its share and says so")
    void theBoardTreatsItAsAStation() throws IOException {
        final String pdu = read("blockentity/PowerDistributionUnitBE.java");
        final String collect = body(pdu, "private List<FeGridNode> collectBranchLoads()");
        assertTrue(collect.contains("legOfNode.put(node.nodeKey(), leg);\n                    noteStationLeg(node, leg, grid);"),
                "the leg that lists a station records its circuit, or no stud of the crown maps to it");
        assertTrue(collect.contains("stationLegNetworks.clear();"), "the stations' legs are rebuilt every walk");
        assertTrue(body(pdu, "private void noteParallelFeed(").contains("noteStationLeg(node, leg, grid);"),
                "every other leg records its circuit too");
        final String auto = body(pdu, "private FeGridSolver.BranchResult serveActiveAuto(");
        assertTrue(auto.contains("stationOf(node) != null\n                    ? com.createstats.power.PduLegPlan.Device.station("),
                "a crown must be screened as a station, on one stud or two");
        assertTrue(auto.contains("parallelCost.put(node.nodeKey(), fit.fed());"),
                "a station is owed what it was fed, which is less than its cost when its studs are short");
        assertTrue(auto.contains("lastStationShares = screened.shares();"), "the serve must hold each leg to its share");
        assertTrue(auto.contains("reasons.put(e.getKey(), PduReason.WIRE_SHORT);"),
                "a station fed short must say so, not \"Running\" (KI-182)");
        final String serve = body(pdu, "private int serveParallelFeeds(");
        assertTrue(serve.contains("lastStationShares.get(key)") && serve.contains("Math.min(owed, legShare)"),
                "each other leg pays its own screened share and no more");
    }

    @Test
    @DisplayName("KI-183: the board reports to every station it feeds, every tick, after the serve")
    void theBoardReports() throws IOException {
        final String pdu = read("blockentity/PowerDistributionUnitBE.java");
        final String tick = body(pdu, "public void tick()");
        final int boards = tick.indexOf("talkToBranchBoards();");
        final int stations = tick.indexOf("talkToStations(managed);");
        assertTrue(boards >= 0 && stations > boards, "the stations are told after the serve, every tick");
        final String talk = body(pdu, "private void talkToStations(");
        assertTrue(talk.contains("station.acceptBoardFeed(new com.createstats.power.FeStationFeed("),
                "the report must reach the station");
        assertTrue(talk.contains("stationStudLegs(station, legs)"), "the report names which stud is on which leg");
        assertTrue(talk.contains("now + com.createstats.power.FeStationFeed.HOLD_TICKS"),
                "a report expires, so a board that is gone stops limiting picks (INV-06)");
        assertTrue(talk.contains("linkedShortInTicks(), linkedReserveRate(),"),
                "the supply outlook travels with the report (the owner's \"kinda 2\")");
    }

    @Test
    @DisplayName("the owner's 2x HV: a controller input takes two HV Lines, its wires adding; the bus and the banks follow")
    void theControllerInputsTakeTwoLines() throws IOException {
        final String bc = read("blockentity/BatteryControllerBE.java");
        final String ceiling = body(bc, "public int portCeiling(int port)");
        assertTrue(ceiling.contains("if (BatteryControllerPorts.isInput(port)) {")
                        && ceiling.contains("return (int) Math.min(inputRate(), wires);"),
                "an input is rated at two lines and its wires add, or a second HV Line on it carries nothing");
        assertTrue(body(bc, "public static int inputRate()").contains("INPUT_LINES * portRate()"),
                "an input is two terminals' worth");
        assertTrue(body(bc, "public static int busCapacity()").contains("BatteryControllerPorts.INPUTS * inputRate()"),
                "the bus must hold a tick of every input (KI-184)");
        assertTrue(body(bc, "public static int reserveChargeRate()").contains("inputRate()"),
                "the banks charge through the inputs");
        assertTrue(body(bc, "public int declaredDemandFePerTick()").contains("Math.min(inputRate(), want)"),
                "a controller fed by a board above takes an input's rating through it");
    }

    @Test
    @DisplayName("KI-183: the crown listens, judges a pick against its studs, and shows the board on its goggles")
    void theCrownListens() throws IOException {
        final String be = read("blockentity/SparkCrownBE.java");
        assertTrue(be.contains("implements FeGridMember, FeManagedLoad, FeWireTerminal,\n        FeStationLoad,"),
                "the crown must be a station, or no board talks to it");
        assertTrue(body(be, "public CrownPicks.Verdict togglePick(").contains("board(server.getGameTime()).pickCarry()"),
                "a pick must be judged against what the board's studs carry");
        assertTrue(body(be, "public void acceptBoardFeed(").contains("boardFeeds[slot] = feed;"),
                "the report must be kept");
        final String write = body(be, "protected void write(");
        assertTrue(write.contains("tag.putIntArray(\"Board\", b.pack());"),
                "the report must reach the client, or the goggles show nothing (INV-15)");
        assertTrue(body(be, "protected void read(").contains("CrownBoard.unpack(tag.getIntArray(\"Board\"))"),
                "the client must read it");
        assertTrue(body(be, "private void sync()").contains("board(level.getGameTime()).discrete()"),
                "a stud opening must go out at once, not with the next number refresh");
        assertTrue(body(be, "public boolean addToGoggleTooltip(").contains("boardGoggles(tooltip, s);"),
                "the goggles must show the board");
        final String toggle = body(read("chunkloader/core/CrownPicks.java"), "public static Verdict toggle(");
        assertTrue(toggle.contains("return Verdict.BOARD_CARRY;"), "the judge must refuse a pick the studs cannot carry");
        assertTrue(toggle.contains("return Verdict.BOARD_SUPPLY;"),
                "and one while the board's supply is forecast short within the minute");
        assertTrue(body(be, "public CrownPicks.Verdict togglePick(").contains("board(server.getGameTime()).pickShortIn()"),
                "a pick must be judged against the board's supply outlook");
        assertTrue(body(be, "public void announce(").contains("pickMessage(v, law)"),
                "a pick on the banks must warn");
        final String goggles = body(be, "private void boardGoggles(");
        assertTrue(goggles.contains("tr(\"goggle.supply_ok\")") && goggles.contains("tr(\"goggle.supply_banks\"")
                        && goggles.contains("tr(\"goggle.supply_short\""),
                "the goggles must show the supply behind the board: keeping up, the banks paying, and for how long");
    }
}
