package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorHubRateTest {
    private static final int BASE = 8_192;
    private static final int POWER = BASE * ConnectorTier.POWER.multiplier();
    private static final int SHIELDED = BASE * WireTier.SHIELDED.multiplier();
    private static final int HV = BASE * WireTier.HV.multiplier();
    private static final int NONE = FeGridSolver.NO_CEILING;
    private static final long TICK = 40L;

    private static long key(int index) {
        return 5_000L + index;
    }

    private static long[] keys(int n) {
        final long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = key(i);
        }
        return out;
    }

    private static boolean[] suppliesAt(int n, int... at) {
        final boolean[] out = new boolean[n];
        for (int one : at) {
            out[one] = true;
        }
        return out;
    }

    private static int[] hubAt(int n, int hub, int rate) {
        final int[] out = new int[n];
        java.util.Arrays.fill(out, NONE);
        out[hub] = rate;
        return out;
    }

    private static int drain(FePathBudget budget, int... sinks) {
        int moved = 0;
        for (int sink : sinks) {
            final int room = budget.remaining(key(sink), TICK);
            assertTrue(room < NONE, "a sink behind graded wiring has a figure");
            budget.spend(key(sink), room, TICK);
            moved += room;
        }
        return moved;
    }

    private static final int[] HUB_PARENT = {-1, 0, 1, 1, 1, 1};
    private static final int[] HUB_EDGES = {NONE, HV, SHIELDED, SHIELDED, SHIELDED, SHIELDED};

    @Test
    @DisplayName("a hub passes no more than its own grade, however many spans leave it")
    void aHubIsHeldToItsGrade() {
        final FePathBudget before = new FePathBudget(keys(6), HUB_PARENT, HUB_EDGES, suppliesAt(6, 0));
        assertEquals(HV, drain(before, 2, 3, 4, 5),
                "the old shape: four Shielded spans and an HV Line through a 16,384 connector moved 65,536");
        final FePathBudget after = new FePathBudget(keys(6), HUB_PARENT, HUB_EDGES, suppliesAt(6, 0),
                hubAt(6, 1, POWER));
        assertEquals(POWER, drain(after, 2, 3, 4, 5));
    }

    @Test
    @DisplayName("each spur still gets its share of the hub, and the hub is shared, not split")
    void theHubIsShared() {
        final FePathBudget after = new FePathBudget(keys(6), HUB_PARENT, HUB_EDGES, suppliesAt(6, 0),
                hubAt(6, 1, POWER));
        assertEquals(POWER, after.remaining(key(2), TICK), "one machine alone may take the whole hub");
        after.spend(key(2), 10_000, TICK);
        assertEquals(POWER - 10_000, after.remaining(key(3), TICK));
        assertEquals(POWER - 10_000, after.remaining(key(5), TICK));
    }

    @Test
    @DisplayName("the block bolted to the hub shares its body with what passes through it")
    void theHubsOwnBlockCounts() {
        final FePathBudget after = new FePathBudget(keys(6), HUB_PARENT, HUB_EDGES, suppliesAt(6, 0),
                hubAt(6, 1, POWER));
        after.spend(key(1), 10_000, TICK);
        assertEquals(POWER - 10_000, after.remaining(key(4), TICK));
        assertEquals(POWER - 10_000, after.remaining(key(1), TICK));
    }

    @Test
    @DisplayName("a hub at the root, a dynamo bolted to it, still holds its grade")
    void aRootHubHoldsItsGrade() {
        final int[] parent = {-1, 0, 0, 0, 0};
        final int[] edges = {NONE, SHIELDED, SHIELDED, SHIELDED, SHIELDED};
        final FePathBudget before = new FePathBudget(keys(5), parent, edges, suppliesAt(5, 0));
        assertEquals(4 * SHIELDED, drain(before, 1, 2, 3, 4),
                "the old shape: a root has no up edge, so nothing about it was ever charged");
        final FePathBudget after = new FePathBudget(keys(5), parent, edges, suppliesAt(5, 0), hubAt(5, 0, POWER));
        assertEquals(POWER, drain(after, 1, 2, 3, 4));
    }

    @Test
    @DisplayName("a hub a panel's stud lands on holds its grade once the panel roots the leg on it")
    void anAnchoredHubHoldsItsGrade() {
        final int[] parent = {-1, 0, 0, 0, 0};
        final int[] edges = {NONE, SHIELDED, SHIELDED, SHIELDED, SHIELDED};
        final FePathBudget after = new FePathBudget(keys(5), parent, edges, new boolean[5], hubAt(5, 0, POWER));
        after.rootOn(key(0));
        assertEquals(POWER, drain(after, 1, 2, 3, 4));
    }

    @Test
    @DisplayName("every grade of connector carries its own grade")
    void everyGradeBinds() {
        for (ConnectorTier tier : ConnectorTier.values()) {
            final int grade = BASE * tier.multiplier();
            final FePathBudget after = new FePathBudget(keys(6), HUB_PARENT,
                    new int[] {NONE, NONE, NONE, NONE, NONE, NONE}, suppliesAt(6, 0), hubAt(6, 1, grade));
            assertEquals(grade, drain(after, 2, 3, 4, 5), tier.name());
        }
    }

    @Test
    @DisplayName("the body refills on the next tick, like every allowance here")
    void theBodyRefills() {
        final FePathBudget after = new FePathBudget(keys(6), HUB_PARENT, HUB_EDGES, suppliesAt(6, 0),
                hubAt(6, 1, POWER));
        drain(after, 2, 3);
        assertEquals(0, after.remaining(key(4), TICK));
        assertEquals(POWER, after.remaining(key(4), TICK + 1));
    }

    @Test
    @DisplayName("a body rating alone is enough to make the budget worth fitting")
    void aBodyAloneBinds() {
        final FePathBudget onlyBody = new FePathBudget(keys(6), HUB_PARENT,
                new int[] {NONE, NONE, NONE, NONE, NONE, NONE}, suppliesAt(6, 0), hubAt(6, 1, POWER));
        assertTrue(onlyBody.hasGradedEdges());
        assertFalse(onlyBody.isEmpty());
        final FePathBudget nothing = new FePathBudget(keys(6), HUB_PARENT,
                new int[] {NONE, NONE, NONE, NONE, NONE, NONE}, suppliesAt(6, 0));
        assertFalse(nothing.hasGradedEdges());
        assertTrue(nothing.isEmpty());
    }

    private static int[] at(int n, int... marked) {
        final int[] out = new int[n];
        for (int one : marked) {
            out[one]++;
        }
        return out;
    }

    @Test
    @DisplayName("a hub every FE crosses is the circuit's throat")
    void aHubIsTheThroat() {
        final int[] parent = {-1, 0, 1, 1, 1};
        final int[] edges = {NONE, NONE, NONE, NONE, NONE};
        final FeConduitThroat before = new FeConduitThroat(keys(5), parent, edges, at(5, 0), at(5, 2, 3, 4));
        assertEquals(NONE, before.ceiling(), "the old shape: three spans off one connector have no common edge");
        final FeConduitThroat after = new FeConduitThroat(keys(5), parent, edges, hubAt(5, 1, POWER),
                at(5, 0), at(5, 2, 3, 4));
        assertEquals(POWER, after.ceiling());
    }

    @Test
    @DisplayName("a hub with the supply on one spur and machines on the others is the throat")
    void aHubBetweenSpursIsTheThroat() {
        final int[] parent = {-1, 0, 1, 1, 1};
        final int[] edges = {NONE, NONE, NONE, NONE, NONE};
        final FeConduitThroat after = new FeConduitThroat(keys(5), parent, edges, hubAt(5, 1, POWER),
                at(5, 2), at(5, 3, 4));
        assertEquals(POWER, after.ceiling());
    }

    @Test
    @DisplayName("a hub is no throat when a supply and a sink share one spur beyond it")
    void aSpurThatPowersItselfIsNoThroat() {
        final int[] parent = {-1, 0, 1, 1, 2, 2};
        final int[] edges = {NONE, NONE, NONE, NONE, NONE, NONE};
        final FeConduitThroat after = new FeConduitThroat(keys(6), parent, edges, hubAt(6, 1, POWER),
                at(6, 2), at(6, 3, 5));
        assertEquals(NONE, after.ceiling());
    }

    @Test
    @DisplayName("named as a panel's anchor, a hub is its leg's throat")
    void anAnchoredHubIsTheLegsThroat() {
        final int[] parent = {-1, 0, 0, 0};
        final int[] edges = {NONE, NONE, NONE, NONE};
        final FeConduitThroat after = new FeConduitThroat(keys(4), parent, edges, hubAt(4, 0, POWER),
                new int[4], at(4, 1, 2, 3));
        assertEquals(NONE, after.ceiling(), "no supply of its own: nothing to find");
        assertEquals(POWER, after.ceilingFrom(key(0)));
    }

    @Test
    @DisplayName("a connector that is its circuit's only supply and only sink carries nothing anywhere")
    void aLoneConnectorIsNoThroat() {
        final FeConduitThroat after = new FeConduitThroat(keys(2), new int[] {-1, 0}, new int[] {NONE, NONE},
                hubAt(2, 0, POWER), at(2, 0), at(2, 0));
        assertEquals(NONE, after.ceiling());
    }

    @Test
    @DisplayName("a graded edge thinner than the hub still wins")
    void aThinnerEdgeStillWins() {
        final int[] parent = {-1, 0, 1, 1};
        final int[] edges = {NONE, BASE, NONE, NONE};
        final FeConduitThroat after = new FeConduitThroat(keys(4), parent, edges, hubAt(4, 1, POWER),
                at(4, 0), at(4, 2, 3));
        assertEquals(BASE, after.ceiling());
    }

    @Test
    @DisplayName("the cable a connector touches counts beside its wires")
    void cableCounts() {
        assertEquals(BASE, FeRates.connectorThroughput(BASE, ConnectorTier.POWER, List.of(WireTier.COPPER)),
                "the old reading: one Copper Wire off to a lamp was taken for the only way in");
        assertEquals(POWER, FeRates.connectorThroughput(BASE, ConnectorTier.POWER, List.of(WireTier.COPPER),
                BASE * 2), "fed by a Bus Duct, the machine bolted to it may take the connector's grade");
        assertEquals(BASE / 2, FeRates.connectorThroughput(BASE, ConnectorTier.ADVANCED, List.of(), BASE / 2),
                "on a Surface Cable with no wires, the cable is its whole feed");
        assertEquals(BASE * ConnectorTier.INDUSTRIAL.multiplier(),
                FeRates.connectorThroughput(BASE, ConnectorTier.INDUSTRIAL, List.of(), 0),
                "nothing meeting it at all: its grade, as before");
    }

    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");
    private static final Path CONNECTOR = Path.of("src/main/java/com/createstats/blockentity/PowerConnectorBE.java");

    private static String code(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
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

    private static String connectorBranch(String net) {
        final String test = "if (node instanceof PowerConnectorBE connector) {";
        final int at = net.indexOf(test);
        assertTrue(at >= 0, "no connector branch in the walk");
        final int next = net.indexOf("} else if (", at);
        assertTrue(next > at, "the connector branch has no end");
        return net.substring(at, next);
    }

    @Test
    @DisplayName("both budgets are built with the body ratings, and a connector's is its grade")
    void bothBudgetsSeeTheBody() throws IOException {
        final String net = code(NETWORK);
        assertTrue(body(net, "private static FeConduitThroat conduitThroatOf(").contains("bodyRates(memberByKey, keys)"),
                "MECH-2 built without the connector's body: a hub is never a throat");
        assertTrue(body(net, "private static FePathBudget pathBudgetOf(").contains("bodyRates(memberByKey, keys)"),
                "MECH-4 built without the connector's body: KI-05 again");
        final String rate = body(net, "static int bodyRate(");
        assertTrue(rate.contains("instanceof PowerConnectorBE") && rate.contains("FeRates.connector("),
                "a connector's body is rated at its grade");
        assertTrue(connectorBranch(net).contains("connector.measureCableFeed()"),
                "the flood re-reads the cable a connector touches each time it walks it");
    }

    @Test
    @DisplayName("the connector's rating counts its cable, and its goggles' figures are sent")
    void theConnectorWiresItsReadout() throws IOException {
        final String be = code(CONNECTOR);
        assertTrue(body(be, "public int transferRate()").contains("cableFeed"),
                "transferRate ignores the cable it touches");
        final String save = body(be, "protected void saveAdditional(");
        for (String field : new String[] {"\"CableFeed\"", "\"Carried\"", "\"BlockFlow\"", "\"ServesBlock\"",
                "\"BranchCeiling\""}) {
            assertTrue(save.contains(field), field + " never reaches the client, so its goggle line reads Java's zero");
        }
        assertTrue(body(be, "public ConnectorReadout readout()").contains("tier.getDisplayName()"),
                "KI-125: the card is titled by the connector's own tier");
    }
}
