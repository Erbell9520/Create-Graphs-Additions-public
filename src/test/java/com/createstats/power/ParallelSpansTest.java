package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelSpansTest {
    private static final int BASE = 8_192;
    private static final int HV = BASE * WireTier.HV.multiplier();
    private static final int COPPER = BASE * WireTier.COPPER.multiplier();
    private static final int DUCT = 16_384;
    private static final int INDUSTRIAL = BASE * ConnectorTier.INDUSTRIAL.multiplier();
    private static final int NONE = FeGridSolver.NO_CEILING;

    private static final int FULL_STATION = 100_707;
    private static final long TICK = 7L;

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

    private static int[] bodies(int n) {
        final int[] out = new int[n];
        java.util.Arrays.fill(out, NONE);
        return out;
    }

    private static List<FeFeeds> feed(int sink, int[] from, int[] rate) {
        final long[] far = new long[from.length];
        for (int i = 0; i < from.length; i++) {
            far[i] = key(from[i]);
        }
        return List.of(new FeFeeds(key(sink), far, rate));
    }

    @Test
    @DisplayName("one plant, two HV Lines into the station (the ring), flooded from the station: 131,072, and a full station holds")
    void theRingFromTheStation() {
        final int[] parent = {-1, 0, 1, 2};
        final int[] rate = {NONE, HV, NONE, NONE};
        final FePathBudget before = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 2));
        assertEquals(HV, before.remaining(key(0), TICK), "the old shape: one span, KI-177");

        final FePathBudget after = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 2), null,
                feed(0, new int[] {3}, new int[] {HV}));
        assertEquals(2 * HV, after.remaining(key(0), TICK), "two HV Lines side by side");
        assertTrue(after.remaining(key(0), TICK) >= FULL_STATION, "a full station (100,707 FE/t) is carried whole");
        after.spend(key(0), FULL_STATION, TICK);
        assertEquals(2 * HV - FULL_STATION, after.remaining(key(0), TICK), "booked once, not once per span");
        assertEquals(FULL_STATION - HV, after.deliveredThrough(key(0), key(3), TICK),
                "line A is filled first and line B carries the rest");
        after.spend(key(0), 2 * HV - FULL_STATION, TICK);
        assertEquals(0, after.remaining(key(0), TICK), "and never more than both lines");
        assertEquals(HV, after.deliveredThrough(key(0), key(3), TICK));
    }

    @Test
    @DisplayName("the same ring flooded from the plant: the same 131,072")
    void theRingFromThePlant() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, NONE, NONE, HV};
        final FePathBudget after = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 0), null,
                feed(3, new int[] {2}, new int[] {HV}));
        assertEquals(2 * HV, after.remaining(key(3), TICK));
    }

    @Test
    @DisplayName("two thin wires add to twice a thin wire, never more")
    void twoThinWires() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, NONE, NONE, COPPER};
        final FePathBudget after = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 0), null,
                feed(3, new int[] {2}, new int[] {COPPER}));
        assertEquals(2 * COPPER, after.remaining(key(3), TICK));
        after.spend(key(3), 2 * COPPER, TICK);
        assertEquals(0, after.remaining(key(3), TICK));
    }

    @Test
    @DisplayName("a bottleneck upstream of both spans still caps their sum")
    void aSharedBottleneck() {
        final int[] parent = {-1, 0, 1, 1, 2};
        final int[] rate = {NONE, DUCT, NONE, NONE, HV};
        final FePathBudget after = new FePathBudget(keys(5), parent, rate, suppliesAt(5, 0), null,
                feed(4, new int[] {3}, new int[] {HV}));
        assertEquals(DUCT, after.remaining(key(4), TICK));
        after.spend(key(4), DUCT, TICK);
        assertEquals(0, after.remaining(key(4), TICK), "the duct is spent once whichever line carried it");
    }

    @Test
    @DisplayName("each line is held to its own path: a thin run behind line B limits line B only")
    void eachLineToItsOwnPath() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, NONE, COPPER, HV};
        final FePathBudget after = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 0), null,
                feed(3, new int[] {2}, new int[] {HV}));
        assertEquals(HV + COPPER, after.remaining(key(3), TICK));
    }

    @Test
    @DisplayName("both spans from one Industrial Connector: the connector's own body carries both, so its grade binds (KI-05)")
    void oneHubBothSpans() {
        final int[] parent = {-1, 0, 1};
        final int[] rate = {NONE, NONE, HV};
        final int[] body = bodies(3);
        body[1] = INDUSTRIAL;
        final FePathBudget after = new FePathBudget(keys(3), parent, rate, suppliesAt(3, 0), body,
                feed(2, new int[] {1}, new int[] {HV}));
        assertEquals(INDUSTRIAL, after.remaining(key(2), TICK));
    }

    @Test
    @DisplayName("two plants, one line each: each circuit holds the station to its own line, and the two add")
    void twoGrids() {
        final FePathBudget one = new FePathBudget(keys(3), new int[] {-1, 0, 1}, new int[] {NONE, NONE, HV},
                suppliesAt(3, 0), null, List.of());
        final FePathBudget two = new FePathBudget(keys(3), new int[] {-1, 0, 1}, new int[] {NONE, NONE, HV},
                suppliesAt(3, 0), null, List.of());
        assertEquals(2 * HV, one.remaining(key(2), TICK) + two.remaining(key(2), TICK));
    }

    @Test
    @DisplayName("a station no supply reaches, or one a board's stud lands on, crosses no conductor of the circuit")
    void unreachedOrAnchored() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, NONE, NONE, HV};
        final FePathBudget dark = new FePathBudget(keys(4), parent, rate, new boolean[4], null,
                feed(3, new int[] {2}, new int[] {HV}));
        assertEquals(NONE, dark.remaining(key(3), TICK), "a circuit with no supply charges nothing, as before");
        dark.rootOn(key(3));
        assertEquals(NONE, dark.remaining(key(3), TICK), "rooted on the station itself, nothing lies between");
    }

    @Test
    @DisplayName("rooted on a board's anchor elsewhere, both lines are paths to the anchor")
    void anchoredElsewhere() {
        final int[] parent = {-1, 0, 0, 2};
        final int[] rate = {NONE, HV, COPPER, NONE};
        final FePathBudget leg = new FePathBudget(keys(4), parent, rate, new boolean[4], null,
                feed(1, new int[] {3}, new int[] {HV}));
        leg.rootOn(key(0));
        assertEquals(HV + COPPER, leg.remaining(key(1), TICK));
    }

    @Test
    @DisplayName("a machine on line B's connector shares line B's path with the station, and never pays for span A")
    void aNeighbourOnLineB() {
        final int[] parent = {-1, 0, 0, 1};
        final int[] rate = {NONE, NONE, HV, HV};
        final FePathBudget after = new FePathBudget(keys(4), parent, rate, suppliesAt(4, 0), null,
                feed(3, new int[] {2}, new int[] {HV}));
        assertEquals(HV, after.remaining(key(2), TICK), "B's own block: its span from the plant, nothing of the station's");
        after.spend(key(3), 2 * HV, TICK);
        assertEquals(0, after.remaining(key(2), TICK), "the station's line B share crossed the plant span too");
    }

    @Test
    @DisplayName("naming no feeds, or feeds for a node the tree never had, changes nothing for any node")
    void noFeedsIsTheOldBudget() {
        final Random random = new Random(177L);
        for (int trial = 0; trial < 500; trial++) {
            final int n = 2 + random.nextInt(12);
            final int[] parent = new int[n];
            final int[] rate = new int[n];
            final int[] body = bodies(n);
            final boolean[] supply = new boolean[n];
            parent[0] = -1;
            rate[0] = NONE;
            for (int i = 1; i < n; i++) {
                parent[i] = random.nextInt(i);
                rate[i] = random.nextInt(3) == 0 ? NONE : 1 + random.nextInt(40);
                if (random.nextInt(4) == 0) {
                    body[i] = 1 + random.nextInt(40);
                }
            }
            supply[random.nextInt(n)] = true;
            final FePathBudget plain = new FePathBudget(keys(n), parent, rate, supply, body);
            final FePathBudget named = new FePathBudget(keys(n), parent, rate, supply, body,
                    List.of(new FeFeeds(99_999L, new long[] {key(0)}, new int[] {5})));
            for (int i = 0; i < n; i++) {
                assertEquals(plain.remaining(key(i), TICK), named.remaining(key(i), TICK));
                final int spend = 1 + random.nextInt(10);
                plain.spend(key(i), spend, TICK);
                named.spend(key(i), spend, TICK);
            }
            for (int i = 0; i < n; i++) {
                assertEquals(plain.remaining(key(i), TICK), named.remaining(key(i), TICK));
            }
        }
    }

    @Test
    @DisplayName("on random trees the station takes exactly the maximum flow its inputs can carry from the supply")
    void randomTreesAgainstAnIndependentMaxFlow() {
        final Random random = new Random(20261008L);
        for (int trial = 0; trial < 3_000; trial++) {
            final int n = 2 + random.nextInt(10);

            final int[] parent = new int[n + 1];
            final int[] rate = new int[n + 1];
            final int[] body = bodies(n + 1);
            parent[0] = -1;
            rate[0] = NONE;
            for (int i = 1; i < n; i++) {
                parent[i] = random.nextInt(i);
                rate[i] = random.nextInt(4) == 0 ? NONE : 1 + random.nextInt(60);
            }
            for (int i = 0; i < n; i++) {
                if (random.nextInt(3) == 0) {
                    body[i] = 1 + random.nextInt(80);
                }
            }
            parent[n] = random.nextInt(n);
            rate[n] = random.nextInt(5) == 0 ? NONE : 1 + random.nextInt(60);
            if (random.nextInt(4) == 0) {
                body[n] = 1 + random.nextInt(120);
            }
            final int supply = random.nextInt(n);
            final int extra = 1 + random.nextInt(3);
            final int[] from = new int[extra];
            final int[] feedRate = new int[extra];
            for (int j = 0; j < extra; j++) {
                from[j] = random.nextInt(n);
                feedRate[j] = random.nextInt(5) == 0 ? NONE : 1 + random.nextInt(60);
            }
            final FePathBudget budget = new FePathBudget(keys(n + 1), parent, rate, suppliesAt(n + 1, supply), body,
                    feed(n, from, feedRate));
            final long expected = maxFlow(parent, rate, body, supply, n, from, feedRate);
            final int got = budget.remaining(key(n), TICK);
            assertEquals(expected >= NONE ? NONE : (int) expected, got, "trial " + trial);
            if (got < NONE) {
                budget.spend(key(n), got, TICK);
                assertEquals(0, budget.remaining(key(n), TICK), "trial " + trial + ": the room it named is all it had");

                int viaFeeds = 0;
                final java.util.Set<Integer> fars = new java.util.HashSet<>();
                for (int j = 0; j < extra; j++) {
                    if (fars.add(from[j])) {
                        viaFeeds += budget.deliveredThrough(key(n), key(from[j]), TICK);
                    }
                }
                assertTrue(viaFeeds <= got, "trial " + trial + ": the feeds carried no more than was delivered");
            }
        }
    }

    @Test
    @DisplayName("the home input: a board's stud, then a silent far end, then the first direct link, never a junction or contactor, else the back face")
    void theHomeInput() {
        final int u = FeFeeds.UNWIRED;
        final int p = FeFeeds.PASS;
        final int d = FeFeeds.DIRECT;
        final int s = FeFeeds.SILENT;
        final int board = FeFeeds.BOARD;
        assertEquals(-1, FeFeeds.homeSlot(new int[] {u, u}), "nothing wired: the back face is home");
        assertEquals(0, FeFeeds.homeSlot(new int[] {d, d}), "stud A first");
        assertEquals(1, FeFeeds.homeSlot(new int[] {u, d}));
        assertEquals(1, FeFeeds.homeSlot(new int[] {d, board}),
                "a board's stud is home, so the board drives the circuit the station is a member of");
        assertEquals(0, FeFeeds.homeSlot(new int[] {board, board}));
        assertEquals(1, FeFeeds.homeSlot(new int[] {d, s}),
                "a breaker's output, a Coupler's socket, another mod's connector: flooded by nothing but the station");
        assertEquals(1, FeFeeds.homeSlot(new int[] {s, board}));
        assertEquals(0, FeFeeds.homeSlot(new int[] {s, s}), "two silent ones: the first, and the second is KI-179");
        assertEquals(1, FeFeeds.homeSlot(new int[] {p, d}), "a junction or contactor stud is never home");
        assertEquals(-1, FeFeeds.homeSlot(new int[] {p, p}), "both through boxes: the back face is home");
    }

    @Test
    @DisplayName("the station's own rating is one figure across every circuit that feeds it, per tick, and a simulate spends nothing")
    void oneIntakeAcrossCircuits() {
        final FeIntakeBudget intake = new FeIntakeBudget();
        final int rate = 2 * 100_707;
        assertEquals(rate, intake.remaining(rate, TICK));
        intake.spend(rate, TICK, 131_072);
        assertEquals(rate - 131_072, intake.remaining(rate, TICK), "the second circuit sees what the first left");
        intake.spend(rate, TICK, 500_000);
        assertEquals(0, intake.remaining(rate, TICK), "never below nothing");
        assertEquals(rate, intake.remaining(rate, TICK + 1), "and the next tick refills it");
        assertEquals(NONE, intake.remaining(NONE, TICK + 2), "a block with no rating has no ceiling here either");
    }

    private static int[] countsAt(int n, int... at) {
        final int[] out = new int[n];
        for (int one : at) {
            out[one]++;
        }
        return out;
    }

    @Test
    @DisplayName("the ring has no throat: neither span carries everything once both feed the station")
    void theRingHasNoThroat() {
        final int[] parent = {-1, 0, 1, 2};
        final int[] rate = {NONE, HV, NONE, NONE};
        final FeConduitThroat before = new FeConduitThroat(keys(4), parent, rate, countsAt(4, 2), countsAt(4, 0));
        assertEquals(HV, before.ceiling(), "the old shape: span A was the circuit's throat, KI-177's MECH-2 half");
        final FeConduitThroat after = new FeConduitThroat(keys(4), parent, rate, null, countsAt(4, 2), countsAt(4, 0),
                feed(0, new int[] {3}, new int[] {HV}));
        assertEquals(NONE, after.ceiling());
    }

    @Test
    @DisplayName("a bottleneck upstream of both spans is still the circuit's throat")
    void aBottleneckIsStillTheThroat() {
        final int[] parent = {-1, 0, 1, 1, 2};
        final int[] rate = {NONE, DUCT, NONE, NONE, HV};
        final FeConduitThroat after = new FeConduitThroat(keys(5), parent, rate, null, countsAt(5, 0),
                countsAt(5, 4), feed(4, new int[] {3}, new int[] {HV}));
        assertEquals(DUCT, after.ceiling());
    }

    @Test
    @DisplayName("a connector on the ring is no throat: the station is reached round the other side of it")
    void aConnectorOnTheRing() {
        final int[] parent = {-1, 0, 1, 0};
        final int[] rate = {NONE, NONE, HV, NONE};
        final int[] body = bodies(4);
        body[1] = INDUSTRIAL;
        body[3] = 2 * BASE;
        final FeConduitThroat before = new FeConduitThroat(keys(4), parent, rate, body, countsAt(4, 0),
                countsAt(4, 2));
        assertEquals(INDUSTRIAL, before.ceiling(), "the old shape: span A and connector A were both in series");
        final FeConduitThroat after = new FeConduitThroat(keys(4), parent, rate, body, countsAt(4, 0), countsAt(4, 2),
                feed(2, new int[] {3}, new int[] {HV}));
        assertEquals(NONE, after.ceiling());
    }

    @Test
    @DisplayName("a station only fed on one circuit is held there to that span, as any only load is")
    void aStationFedOnceHere() {
        final FeConduitThroat two = new FeConduitThroat(keys(3), new int[] {-1, 0, 1}, new int[] {NONE, NONE, HV},
                null, countsAt(3, 0), countsAt(3, 2), List.of());
        assertEquals(HV, two.ceiling());
    }

    @Test
    @DisplayName("from a board's anchor, the ring has no throat either")
    void fromAnAnchor() {
        final int[] parent = {-1, 0, 0, 2};
        final int[] rate = {NONE, HV, COPPER, NONE};
        final FeConduitThroat before = new FeConduitThroat(keys(4), parent, rate, new int[4], countsAt(4, 1));
        assertEquals(HV, before.ceilingFrom(key(0)));
        final FeConduitThroat after = new FeConduitThroat(keys(4), parent, rate, null, new int[4], countsAt(4, 1),
                feed(1, new int[] {3}, new int[] {HV}));
        assertEquals(NONE, after.ceilingFrom(key(0)));
    }

    @Test
    @DisplayName("on random circuits the throat is exactly the weakest conductor whose removal separates every supply from every sink")
    void randomCircuitsAgainstBruteForce() {
        final Random random = new Random(178L);
        for (int trial = 0; trial < 4_000; trial++) {
            final boolean withFeeds = trial % 3 != 0;
            final int n = 2 + random.nextInt(9);
            final int total = withFeeds ? n + 1 : n;
            final int[] parent = new int[total];
            final int[] rate = new int[total];
            final int[] body = bodies(total);
            final int[] sup = new int[total];
            final int[] rec = new int[total];
            parent[0] = -1;
            rate[0] = NONE;
            for (int i = 1; i < n; i++) {
                parent[i] = random.nextInt(i);
                rate[i] = random.nextInt(3) == 0 ? NONE : 1 + random.nextInt(50);
            }
            for (int i = 0; i < n; i++) {
                if (random.nextInt(3) == 0) {
                    body[i] = 1 + random.nextInt(50);
                }
                if (random.nextInt(3) == 0) {
                    sup[i] = 1;
                }
                if (random.nextInt(3) == 0) {
                    rec[i] = 1;
                }
            }
            List<FeFeeds> feeds = List.of();
            int[] fs = new int[0];
            int[] ff = new int[0];
            int[] fr = new int[0];
            if (withFeeds) {
                parent[n] = random.nextInt(n);
                rate[n] = random.nextInt(4) == 0 ? NONE : 1 + random.nextInt(50);
                rec[n] = 1;
                final int extra = 1 + random.nextInt(2);
                fs = new int[extra];
                ff = new int[extra];
                fr = new int[extra];
                for (int j = 0; j < extra; j++) {
                    fs[j] = n;
                    ff[j] = random.nextInt(n);
                    fr[j] = random.nextInt(4) == 0 ? NONE : 1 + random.nextInt(50);
                }
                feeds = feed(n, ff, fr);
            }
            final FeConduitThroat throat = new FeConduitThroat(keys(total), parent, rate, body, sup, rec, feeds);
            assertEquals(brute(total, parent, rate, body, sup, rec, fs, ff, fr, -1), throat.ceiling(),
                    "trial " + trial);
            final int anchor = random.nextInt(total);
            assertEquals(brute(total, parent, rate, body, sup, rec, fs, ff, fr, anchor), throat.ceilingFrom(key(anchor)),
                    "trial " + trial + " from anchor " + anchor);
        }
    }

    private static int brute(int n, int[] parent, int[] rate, int[] body, int[] sup, int[] rec, int[] fs, int[] ff,
                             int[] fr, int anchor) {
        final int[] s = sup.clone();
        if (anchor >= 0) {
            s[anchor]++;
        }
        int totalS = 0;
        int totalR = 0;
        for (int i = 0; i < n; i++) {
            totalS += s[i];
            totalR += rec[i];
        }
        if (totalS == 0 || totalR == 0) {
            return NONE;
        }

        final List<int[]> edges = new ArrayList<>();
        for (int i = 1; i < n; i++) {
            if (parent[i] >= 0) {
                edges.add(new int[] {i, parent[i], rate[i]});
            }
        }
        for (int j = 0; j < fs.length; j++) {
            edges.add(new int[] {fs[j], ff[j], fr[j]});
        }
        int found = NONE;
        for (int e = 0; e < edges.size(); e++) {
            if (edges.get(e)[2] >= NONE) {
                continue;
            }
            if (!anyPieceMixed(n, edges, e, -1, s, rec)) {
                found = Math.min(found, edges.get(e)[2]);
            }
        }
        for (int v = 0; v < n; v++) {
            if (body[v] >= NONE) {
                continue;
            }
            final boolean crosses = totalS - s[v] > 0 || totalR - rec[v] > 0;
            if (crosses && !anyPieceMixed(n, edges, -1, v, s, rec)) {
                found = Math.min(found, body[v]);
            }
        }
        return found;
    }

    private static boolean anyPieceMixed(int n, List<int[]> edges, int skipEdge, int skipNode, int[] s, int[] r) {
        final int[] comp = new int[n];
        java.util.Arrays.fill(comp, -1);
        int pieces = 0;
        for (int start = 0; start < n; start++) {
            if (start == skipNode || comp[start] >= 0) {
                continue;
            }
            final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(start);
            comp[start] = pieces;
            int hasS = 0;
            int hasR = 0;
            while (!queue.isEmpty()) {
                final int at = queue.poll();
                hasS += s[at];
                hasR += r[at];
                for (int e = 0; e < edges.size(); e++) {
                    if (e == skipEdge) {
                        continue;
                    }
                    final int[] edge = edges.get(e);
                    final int other = edge[0] == at ? edge[1] : edge[1] == at ? edge[0] : -1;
                    if (other < 0 || other == skipNode || comp[other] >= 0) {
                        continue;
                    }
                    comp[other] = pieces;
                    queue.add(other);
                }
            }
            if (hasS > 0 && hasR > 0) {
                return true;
            }
            pieces++;
        }
        return false;
    }

    private static long maxFlow(int[] parent, int[] rate, int[] body, int supply, int station, int[] from,
                                int[] feedRate) {
        final int n = station;

        final List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 1; i < n; i++) {
            adj.get(i).add(parent[i]);
            adj.get(parent[i]).add(i);
        }
        final int[] up = new int[n];
        final long[] upRoom = new long[n];
        java.util.Arrays.fill(up, -2);
        up[supply] = -1;
        final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(supply);
        final List<Integer> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            final int at = queue.poll();
            order.add(at);
            for (int next : adj.get(at)) {
                if (up[next] == -2) {
                    up[next] = at;

                    upRoom[next] = inf(parent[next] == at ? rate[next] : rate[at]);
                    queue.add(next);
                }
            }
        }

        final long[] hung = new long[n];
        hung[parent[station]] = sat(hung[parent[station]], inf(rate[station]));
        for (int j = 0; j < from.length; j++) {
            hung[from[j]] = sat(hung[from[j]], inf(feedRate[j]));
        }
        final long[] flow = new long[n];
        for (int k = order.size() - 1; k >= 0; k--) {
            final int v = order.get(k);
            long in = hung[v];
            for (int c : adj.get(v)) {
                if (up[c] == v) {
                    in = sat(in, Math.min(upRoom[c], flow[c]));
                }
            }
            flow[v] = Math.min(inf(body[v]), in);
        }
        return Math.min(inf(body[station]), flow[supply]);
    }

    private static long inf(int rate) {
        return rate >= NONE ? Long.MAX_VALUE / 4 : rate;
    }

    private static long sat(long a, long b) {
        return Math.min(Long.MAX_VALUE / 4, a + b);
    }
}
