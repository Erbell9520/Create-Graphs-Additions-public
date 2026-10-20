package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkCrownBoardWiringTest {
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
    @DisplayName("KI-180: a station's leaf asks the station for its rating, and no wrap copies it")
    void theLeafIsLive() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        assertTrue(body(net, "private static List<ParallelSink> resolveParallel(")
                        .contains("FeAttachment.station(at, sink.attachedStorage(), sink)"),
                "a station's leaf must name the station, or it keeps its flood-time rating (KI-180)");
        assertFalse(net.contains("new FeAttachment(attachment.machine()"),
                "a flood-time wrap must go through FeAttachment.over, or it freezes a station's leaf again");
        assertFalse(net.contains("attachment.storage(), attachment.rate())"),
                "the role and supply seeds must probe with the live rating");
        final String attachment = read("power/FeAttachment.java");
        assertTrue(body(attachment, "public int transferRate()").contains("rateOf.transferRate()"),
                "the attachment must read its source when asked");
        assertTrue(body(attachment, "public FeAttachment over(").contains("rateOf"),
                "a wrap must keep the source");
    }

    @Test
    @DisplayName("KI-181: the board keeps a two-wire load's other legs, screens it on all of them and pays it on all")
    void theBoardFeedsEveryLeg() throws IOException {
        final String pdu = read("blockentity/PowerDistributionUnitBE.java");
        final String collect = body(pdu, "private List<FeGridNode> collectBranchLoads()");
        assertTrue(body(collect, "if (!seenDevices.add(devicePos.immutable()))").contains("noteParallelFeed(node, leg, grid);"),
                "a load reached on a second leg must be kept as a feed, or the second leg carries nothing");
        assertTrue(collect.contains("parallelFeeds.clear();") || pdu.contains("parallelFeeds.clear();"),
                "the feeds are rebuilt every tick");
        final String auto = body(pdu, "private FeGridSolver.BranchResult serveActiveAuto(");
        assertTrue(auto.contains("PduLegPlan.Device.station(node.nodeKey(), leg,\n"
                        + "                            priorityOrdinalOf(node), total, lock, alsoLegsOf(node))"),
                "the screen must be told a station's other legs, or it feeds the station one leg's rating");
        assertTrue(auto.contains("screened.shares().get(node.nodeKey())")
                        && auto.contains("runAllowance.put(node.nodeKey(), share[legOf(node)]);"),
                "the home leg must be asked for its share only");
        assertTrue(auto.contains("serveParallelFeeds(t, parallelCost,"),
                "Active Auto must pay the rest from the other legs in the station's own round");
        final String across = body(pdu, "private FeGridSolver.BranchResult serveAcrossLegs(");
        assertTrue(across.contains("serveParallelFeeds(-1, null,"), "Off must feed the other legs too");
        assertTrue(across.contains("serveParallelFeeds(FePriority.CRITICAL.ordinal(), null,"),
                "Manual must feed the other legs in the Critical round");
        assertTrue(body(pdu, "private int fillBuffers(").contains("withParallelFeeds(admittedByLeg)"),
                "the fill pass must top the station up on every leg it is fed on");
        final String serve = body(pdu, "private int serveParallelFeeds(");
        assertTrue(serve.contains("legRoom(legOutflow, feed.leg())") && serve.contains("spendLeg(legOutflow, feed.leg(), got)"),
                "each other leg is spent out of its own room (MECH-7)");
        assertTrue(serve.contains("branchFlowByNode.getOrDefault(key, 0)"),
                "what is owed is the cost less what this tick already handed it");
        assertTrue(body(pdu, "private void noteParallelFeed(").contains("grid.instruments().isEmpty()"),
                "a feed past a Power Limiter would bypass its cap (MECH-6)");
    }

    @Test
    @DisplayName("KI-181: studs 1 and 2 are HV, and the controller's terminal is rated to an HV Line under a new key")
    void theRatings() throws IOException {
        final String pdu = read("blockentity/PowerDistributionUnitBE.java");
        assertTrue(body(pdu, "public int legCeiling(int leg)").contains("studRate(leg)"),
                "a leg must be rated by its own stud");
        assertTrue(body(pdu, "private static int linkCeiling(int leg, StudLink link)").contains("studRate(leg)"),
                "a stud wire must be rated by its own stud");
        assertTrue(body(pdu, "public static int studRate(int leg)").contains("leg < HV_STUDS ? hvStudRate()"),
                "studs 1 and 2 are the HV studs");
        final String config = read("ModConfiguration.java");
        assertTrue(config.contains(".defineInRange(\"terminalRate\", 65536,"),
                "the terminal is rated to an HV Line, under a key no install has on disk");
        assertFalse(config.contains("\"portRate\""),
                "the old key keeps 32,768 in every existing config file");
        assertTrue(body(read("blockentity/BatteryControllerBE.java"), "public static int portRate()")
                .contains("return 65536;"), "the fallback must agree with the default (FM-14)");
    }
}
