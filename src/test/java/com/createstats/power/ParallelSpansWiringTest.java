package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelSpansWiringTest {
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

    private static String block(String source, String start) {
        return body(source, start);
    }

    @Test
    @DisplayName("only the Spark Crown takes its spans in parallel; every other terminal keeps the default, false")
    void theOptIn() throws IOException {
        final String terminal = read("power/FeWireTerminal.java");
        assertTrue(body(terminal, "default boolean feedsInParallel()").contains("return false;"),
                "the default must stay false, or every built-in connector stops being a junction");
        assertTrue(body(read("blockentity/SparkCrownBE.java"), "public boolean feedsInParallel()").contains("return true;"),
                "the crown does not opt in: its second line adds nothing again (KI-177)");
        final List<String> optIns = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String text = code(Files.readString(file, StandardCharsets.UTF_8));
                if (text.contains("boolean feedsInParallel()") && !file.endsWith("FeWireTerminal.java")) {
                    optIns.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of("SparkCrownBE.java"), optIns,
                "the owner's rule: two wires add only for machines that take two wires, the Spark Crown");
    }

    @Test
    @DisplayName("every way a span reaches a terminal routes a parallel one through reachParallel, ahead of the plain enqueue")
    void everyArrivalIsRouted() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        final String walk = body(net,
                "private static void walkTerminalLinks(FeWireTerminal terminal, Level level, Walk walk, @Nullable BlockPos only)");
        final int parallel = walk.indexOf("if (linkBE instanceof FeWireTerminal parallel && parallel.feedsInParallel())");
        final int plain = walk.indexOf("if (linkBE instanceof FeWireTerminal) {");
        assertTrue(parallel >= 0 && plain > parallel, "a wire straight onto the station is enqueued plainly: a junction");
        assertTrue(walk.indexOf("reachParallel(parallel, linkBE, link, from, from, true, FeGridSolver.NO_CEILING, walk);",
                parallel) > parallel, "the direct branch does not reach it as a parallel station");
        final String junction = block(walk, "if (linkBE instanceof com.createstats.blockentity.DiagnosticJunctionBE junction)");
        assertTrue(junction.contains("reachParallel(parallel, farBE, far, from, link, true, rate, walk)"),
                "a span through a junction channel enqueues the station plainly: " + junction);
        final String contactor = block(walk, "if (linkBE instanceof com.createstats.blockentity.ContactorBE contactor)");
        assertTrue(contactor.contains("reachParallel(parallel, farBE, far.to(), from, link, true, rate, walk)"),
                "a span through a made pole enqueues the station plainly: " + contactor);
        final String breaker = block(net, "if (stud >= 0 && breaker.isPassthrough() && walk.crossPassthrough())");
        assertTrue(breaker.contains("reachParallel(parallel, onward, link.to(), breakerAt, breakerAt, true,"),
                "a breaker in passthrough enqueues the station plainly: " + breaker);
    }

    @Test
    @DisplayName("a station is put on the tree only along its home input; every other arrival is a feed beside it")
    void reachParallelHoldsTheRule() throws IOException {
        final String reach = body(read("power/FeWireNetwork.java"), "private static boolean reachParallel(");
        assertTrue(reach.contains("walk.meetParallel(at);"), "a known station is skipped by the next run against it");
        assertTrue(reach.contains("if (mayBeHome && java.util.Objects.equals(via, sink.homeLink())) {"),
                "enqueued by any arrival: a junction again (KI-178): " + reach);
        assertTrue(reach.contains("return walk.enqueue(from, at, node);"), "never put on the tree at all: " + reach);
        assertTrue(reach.contains("walk.feed(sink, at, from, via, pass);"),
                "its other inputs are dropped: one line again (KI-177): " + reach);
    }

    @Test
    @DisplayName("a station already on the tree is met again by a run against it, and each input is recorded once")
    void aKnownStationIsMetAgain() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        final String spread = body(net, "private static void spreadFromBlock(Level level, BlockPos at, Walk walk, "
                + "boolean includeCable,\n                                        boolean anyFace, boolean includeConnectors)");
        assertTrue(spread.contains("(walk.known(neighbour) && !walk.isParallel(neighbour))"),
                "a run against a station already on the tree is skipped, and its feed with it: " + spread);
        final String start = body(net, "void start(BlockPos at, Object node)");
        assertTrue(start.contains("meetParallel(pos);"), "a station that is the flood's origin is skipped as known");
        final String add = body(net, "void add(long input, long fromKey, int passRate)");
        assertTrue(add.contains("if (inputs.add(input))"), "one junction stud reached twice doubles the span into the station");
    }

    @Test
    @DisplayName("the arrivals are resolved before the connector dedupe, and both budgets are built with them")
    void bothBudgetsSeeTheFeeds() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        final String build = body(net, "private static FeWireNetwork build(Level level, Object origin)");
        final int resolve = build.indexOf("resolveParallel(walk, attachments, flowOwners)");
        final int dedupe = build.indexOf("attachments.remove(host);");
        assertTrue(resolve >= 0 && dedupe > resolve, "resolved after the dedupe: a station with a connector bolted to it is fed twice");
        assertTrue(build.contains("treeArrays(found, attachments, walk.parentOf, walk.passRate, parallelSinks)"),
                "the budgets are built without the stations");
        assertTrue(build.contains("applyPathBudget(level, paths, attachments, flowOwners, tree.leaves())"),
                "a station's leaf is charged at the conduit that reached it, missing its other spans");
        assertTrue(body(net, "private static FePathBudget pathBudgetOf(").contains("tree.feeds()"),
                "MECH-4 built without the feeds: one span again (KI-177)");
        assertTrue(body(net, "private static FeConduitThroat conduitThroatOf(").contains("tree.feeds()"),
                "MECH-2 built without the feeds: a span the station shares is its throat (KI-177)");
        final String resolveBody = body(net, "private static List<ParallelSink> resolveParallel(");
        assertTrue(resolveBody.contains("FeAttachment.station(at, sink.attachedStorage(), sink)"),
                "a circuit the station is not a member of never feeds it: two plants do not add");
        assertTrue(body(net, "private static void applyPathBudget(").contains("leaves.contains(attachment.nodeKey())"),
                "the leaves set is passed and never read");
        final String tree = body(net, "private static TreeArrays treeArrays(");
        assertTrue(tree.contains("feeds.add(new FeFeeds(sink.key(), from, rate))"), "the feeds are never named");
    }

    @Test
    @DisplayName("both flow-graph recomputes move each span's share onto its own wire")
    void theReadoutsGetTheMoves() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        assertTrue(body(net, "public void solveOnce(Level level)").contains("pathBudget.movesInto(now, moveFrom, moveTo, moveFe)"),
                "an unmanaged circuit reads line B as nothing and line A as the whole station");
        assertTrue(body(net, "public void reportManagedFlows(Map<Long, Integer> signedFlow, long anchorKey, int anchorSupply,")
                        .contains("pathBudget.movesInto(gameTime, moveFrom, moveTo, moveFe)"),
                "a managed circuit reads line B as nothing and line A as the whole station");
        assertTrue(body(net, "private static FeWireNetwork build(Level level, Object origin)")
                        .contains("network.moveFe = new int[paths.feedSpanCount()];"),
                "no room for the moves, so none are ever written");
    }

    @Test
    @DisplayName("the station holds its own rating once across every circuit, and reads its home before any flood")
    void theStationsOwnSide() throws IOException {
        final String be = read("blockentity/SparkCrownBE.java");
        final String handler = body(be, "public int receiveEnergy(int maxReceive, boolean simulate)");
        assertTrue(handler.contains("intake.remaining(rate, now)"),
                "its rating is not asked: two plants refill it at four times its draw (KI-178)");
        assertTrue(handler.contains("intake.spend(rate, now, take);"), "its rating is asked and never spent: " + handler);
        final String tick = body(be, "public void tick()");
        final int read = tick.indexOf("readStudKinds();");
        final int build = tick.indexOf("FeWireNetwork.buildFrom(this)");
        assertTrue(read >= 0 && build > read, "its first flood is built on far ends it has not read: " + tick);
        assertTrue(body(be, "public void adoptNetwork(FeWireNetwork network)").contains("this.adoptedHome = homeSlot();"),
                "it cannot tell its circuit was built for another home");
        final String kinds = body(be, "private void readStudKinds()");
        assertTrue(kinds.contains("if (network != null && homeSlot() != adoptedHome)") && kinds.contains("invalidateNetwork();"),
                "a board loading at the far end of a wire never moves it to the board's circuit: " + kinds);
        final String home = body(be, "public BlockPos homeLink()");
        assertTrue(home.contains("FeFeeds.homeSlot(") || home.contains("homeSlot()"), "the home is not the pure rule: " + home);
    }
}
