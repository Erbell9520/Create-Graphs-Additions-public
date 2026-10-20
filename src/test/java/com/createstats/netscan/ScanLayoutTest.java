package com.createstats.netscan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanLayout;
import com.createstats.netscan.scan.ScanLayoutMode;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanNodeKind;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanLayoutTest {
    private static BlockPos at(int x) {
        return new BlockPos(x, 64, 0);
    }

    private static ScanNode node(BlockPos pos, ScanNodeKind kind, boolean source, BlockPos upstream) {
        return new ScanNode(pos, "n" + pos.getX(), "test:n", kind, 32.0f, 0.0f, 0.0f, 0.0f,
                source, false, upstream);
    }

    private static ScanGraph graph(List<ScanNode> nodes, List<ScanEdge> edges) {
        final Map<BlockPos, ScanNode> map = new LinkedHashMap<>();
        for (final ScanNode n : nodes) {
            map.put(n.pos(), n);
        }
        return new ScanGraph(map, edges, 1L, 0L, false, java.util.Set.of());
    }

    private static ScanGraph chain(int length) {
        final List<ScanNode> nodes = new ArrayList<>();
        final List<ScanEdge> edges = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            nodes.add(node(at(i), i == 0 ? ScanNodeKind.SOURCE : ScanNodeKind.TRANSFER, i == 0,
                    i == 0 ? null : at(i - 1)));
            if (i > 0) {
                edges.add(new ScanEdge(at(i - 1), at(i), 1.0f));
            }
        }
        return graph(nodes, edges);
    }

    @Test
    void anEmptyGraphLaysOutWithoutComplaint() {
        final ScanLayout.Result r = ScanLayout.layout(ScanGraph.EMPTY);
        assertTrue(r.positions().isEmpty());
        assertEquals(0, r.depth());
    }

    @Test
    void rankIsStepsFromTheSource() {
        final ScanLayout.Result r = ScanLayout.layout(chain(5));
        for (int i = 0; i < 5; i++) {
            assertEquals(i, r.rank(at(i)), "element " + i + " is " + i + " steps down the line");
        }
        assertEquals(5, r.depth());
    }

    @Test
    void theSourceIsTheRootEvenWhenItIsNotVisitedFirst() {
        final List<ScanNode> nodes = List.of(
                node(at(2), ScanNodeKind.TRANSFER, false, at(1)),
                node(at(1), ScanNodeKind.TRANSFER, false, at(0)),
                node(at(0), ScanNodeKind.SOURCE, true, null),
                node(at(3), ScanNodeKind.MACHINE, false, at(2)));
        final ScanGraph g = graph(nodes, List.of(
                new ScanEdge(at(2), at(1), 1.0f), new ScanEdge(at(1), at(0), 1.0f),
                new ScanEdge(at(2), at(3), 1.0f)));
        final ScanLayout.Result r = ScanLayout.layout(g);
        assertEquals(at(0), r.primaryRoot(), "the diagram starts where the power does");
        assertEquals(0, r.rank(at(0)));
        assertEquals(3, r.rank(at(3)));
    }

    @Test
    void everyNodeGetsAPositionInsideTheUnitSquare() {
        final ScanLayout.Result r = ScanLayout.layout(chain(6));
        assertEquals(6, r.positions().size());
        for (final float[] p : r.positions().values()) {
            assertTrue(p[0] >= 0.0f && p[0] <= 1.0f, "x in range");
            assertTrue(p[1] >= 0.0f && p[1] <= 1.0f, "y in range");
        }
    }

    @Test
    void aChainDrawsAsAStraightLine() {
        final ScanLayout.Result r = ScanLayout.layout(chain(6));
        for (final float[] p : r.positions().values()) {
            assertEquals(0.5f, p[1], 1.0e-5f);
        }
    }

    @Test
    void theSameGraphLaysOutIdenticallyEveryTime() {
        final ScanGraph g = branching();
        final ScanLayout.Result a = ScanLayout.layout(g);
        final ScanLayout.Result b = ScanLayout.layout(g);
        for (final Map.Entry<BlockPos, float[]> e : a.positions().entrySet()) {
            final float[] other = b.position(e.getKey());
            assertNotNull(other);
            assertEquals(e.getValue()[0], other[0], 1.0e-6f);
            assertEquals(e.getValue()[1], other[1], 1.0e-6f);
        }
    }

    @Test
    void theSiblingEdgeOfALoopIsNotOnTheDrivePath() {
        final List<ScanNode> nodes = List.of(
                node(at(0), ScanNodeKind.SOURCE, true, null),
                node(at(1), ScanNodeKind.TRANSFER, false, at(0)),
                node(at(2), ScanNodeKind.TRANSFER, false, at(0)));
        final ScanEdge left = new ScanEdge(at(0), at(1), 1.0f);
        final ScanEdge right = new ScanEdge(at(0), at(2), 1.0f);
        final ScanEdge sibling = new ScanEdge(at(1), at(2), 1.0f);
        final ScanLayout.Result r = ScanLayout.layout(graph(nodes, List.of(left, right, sibling)));
        assertEquals(0, r.rank(at(0)));
        assertEquals(1, r.rank(at(1)));
        assertEquals(1, r.rank(at(2)));
        assertTrue(r.isPrimaryEdge(left));
        assertTrue(r.isPrimaryEdge(right));
        assertFalse(r.isPrimaryEdge(sibling));
    }

    @Test
    void aFragmentTheTreeCannotReachIsStillPlaced() {
        final List<ScanNode> nodes = List.of(
                node(at(0), ScanNodeKind.SOURCE, true, null),
                node(at(1), ScanNodeKind.TRANSFER, false, at(0)),
                node(at(9), ScanNodeKind.OTHER, false, null));
        final ScanLayout.Result r = ScanLayout.layout(
                graph(nodes, List.of(new ScanEdge(at(0), at(1), 1.0f))));
        assertEquals(3, r.positions().size());
        assertNotNull(r.position(at(9)));
    }

    @Test
    void theLayoutRecordsTheHashItWasBuiltFor() {
        final ScanGraph g = chain(4);
        assertEquals(g.contentHash(), ScanLayout.layout(g).graphHash(),
                "the cache key travels with the result, so a stale layout can be noticed");
    }

    @Test
    void liveValuesDoNotChangeTheContentHash() {
        final ScanGraph g = chain(4);
        final int n = g.size();
        final ScanGraph moved = g.withLive(new float[n], new float[n], new float[n], new boolean[n]);
        assertEquals(g.contentHash(), moved.contentHash());
        assertSame(g.edges().size(), moved.edges().size());
    }

    private static ScanGraph weighted() {
        final List<ScanNode> nodes = new ArrayList<>();
        final List<ScanEdge> edges = new ArrayList<>();
        nodes.add(loaded(at(0), ScanNodeKind.SOURCE, true, null, 0.0f));

        nodes.add(loaded(at(1), ScanNodeKind.TRANSFER, false, at(0), 0.0f));
        nodes.add(loaded(at(2), ScanNodeKind.MACHINE, false, at(1), 512.0f));

        nodes.add(loaded(at(3), ScanNodeKind.TRANSFER, false, at(0), 0.0f));
        nodes.add(loaded(at(4), ScanNodeKind.MACHINE, false, at(3), 16.0f));
        edges.add(new ScanEdge(at(0), at(1), 1.0f));
        edges.add(new ScanEdge(at(1), at(2), 1.0f));
        edges.add(new ScanEdge(at(0), at(3), 1.0f));
        edges.add(new ScanEdge(at(3), at(4), 1.0f));
        return graph(nodes, edges);
    }

    private static ScanNode loaded(BlockPos pos, ScanNodeKind kind, boolean source, BlockPos upstream,
                                   float stress) {
        return new ScanNode(pos, "n" + pos.getX(), "test:n", kind, 32.0f, stress, 0.0f, 0.0f,
                source, false, upstream);
    }

    @Test
    void everyModePlacesEveryElementInsideTheUnitSquare() {
        for (final ScanLayoutMode mode : ScanLayoutMode.values()) {
            final ScanLayout.Result r = ScanLayout.layout(weighted(), mode);
            assertEquals(5, r.positions().size(), mode + " places everything");
            for (final Map.Entry<BlockPos, float[]> e : r.positions().entrySet()) {
                final float[] p = e.getValue();
                assertTrue(p[0] >= -1.0e-4f && p[0] <= 1.0f + 1.0e-4f, mode + " x of " + e.getKey());
                assertTrue(p[1] >= -1.0e-4f && p[1] <= 1.0f + 1.0e-4f, mode + " y of " + e.getKey());
            }
        }
    }

    @Test
    void theArrangementNeverChangesWhatTheDiagramClaims() {
        final ScanGraph g = weighted();
        final ScanLayout.Result drive = ScanLayout.layout(g, ScanLayoutMode.DRIVE);
        for (final ScanLayoutMode mode : ScanLayoutMode.values()) {
            final ScanLayout.Result r = ScanLayout.layout(g, mode);
            assertEquals(drive.depth(), r.depth(), mode + " depth");
            assertEquals(drive.primaryRoot(), r.primaryRoot(), mode + " root");
            for (final BlockPos pos : g.nodes().keySet()) {
                assertEquals(drive.rank(pos), r.rank(pos), mode + " rank of " + pos);
                assertEquals(drive.subtreeStress(pos), r.subtreeStress(pos), 1.0e-4f,
                        mode + " subtree of " + pos);
            }
            for (final ScanEdge e : g.edges()) {
                assertEquals(drive.isPrimaryEdge(e), r.isPrimaryEdge(e), mode + " drive path");
            }
        }
    }

    @Test
    void subtreeStressIsWhatABranchCosts() {
        final ScanLayout.Result r = ScanLayout.layout(weighted());
        assertEquals(512.0f, r.subtreeStress(at(2)), 1.0e-4f, "a leaf costs its own draw");
        assertEquals(512.0f, r.subtreeStress(at(1)), 1.0e-4f, "a shaft costs what it carries");
        assertEquals(16.0f, r.subtreeStress(at(3)), 1.0e-4f);
        assertEquals(528.0f, r.subtreeStress(at(0)), 1.0e-4f, "the source carries the whole factory");
        assertEquals(528.0f, r.maxSubtreeStress(), 1.0e-4f);
    }

    @Test
    void carriedIsTheLoadCrossingAConnection() {
        final ScanLayout.Result r = ScanLayout.layout(weighted(), ScanLayoutMode.POWER);
        assertEquals(512.0f, r.carried(new ScanEdge(at(0), at(1), 1.0f)), 1.0e-4f);
        assertEquals(512.0f, r.carried(new ScanEdge(at(1), at(0), 1.0f)), 1.0e-4f);
        assertEquals(16.0f, r.carried(new ScanEdge(at(0), at(3), 1.0f)), 1.0e-4f);
        assertEquals(0.0f, r.carried(new ScanEdge(at(2), at(4), 1.0f)), 1.0e-4f);
    }

    @Test
    void powerPutsTheHeavierBranchFirst() {
        final ScanLayout.Result r = ScanLayout.layout(weighted(), ScanLayoutMode.POWER);

        assertEquals(r.rank(at(1)), r.rank(at(3)), "same rank");
        assertTrue(r.position(at(1))[1] < r.position(at(3))[1],
                "the trunk is drawn above the twig");
    }

    @Test
    void ringPutsTheSourceAtTheCentreAndRanksOnRings() {
        final ScanLayout.Result r = ScanLayout.layout(weighted(), ScanLayoutMode.RING);
        final float[] root = r.position(at(0));
        assertEquals(0.5f, root[0], 1.0e-4f);
        assertEquals(0.5f, root[1], 1.0e-4f);

        final double one = radius(r, at(1));
        assertEquals(one, radius(r, at(3)), 1.0e-3, "one ring per step");
        assertTrue(radius(r, at(2)) > one + 1.0e-3, "two steps out is further out");
        assertEquals(radius(r, at(2)), radius(r, at(4)), 1.0e-3);
    }

    private static double radius(ScanLayout.Result r, BlockPos pos) {
        final float[] p = r.position(pos);
        return Math.hypot(p[0] - 0.5, p[1] - 0.5);
    }

    @Test
    void planIsWorldCoordinatesWithTheAspectKept() {
        final List<ScanNode> nodes = List.of(
                new ScanNode(new BlockPos(0, 64, 0), "a", "t:a", ScanNodeKind.SOURCE, 0, 0, 0, 0,
                        true, false, null),
                new ScanNode(new BlockPos(40, 64, 0), "b", "t:b", ScanNodeKind.TRANSFER, 0, 0, 0, 0,
                        false, false, new BlockPos(0, 64, 0)),
                new ScanNode(new BlockPos(40, 70, 20), "c", "t:c", ScanNodeKind.MACHINE, 0, 0, 0, 0,
                        false, false, new BlockPos(40, 64, 0)));
        final ScanGraph g = graph(nodes, List.of(
                new ScanEdge(new BlockPos(0, 64, 0), new BlockPos(40, 64, 0), 1.0f),
                new ScanEdge(new BlockPos(40, 64, 0), new BlockPos(40, 70, 20), 1.0f)));
        final ScanLayout.Result r = ScanLayout.layout(g, ScanLayoutMode.PLAN);

        final float[] a = r.position(new BlockPos(0, 64, 0));
        final float[] b = r.position(new BlockPos(40, 64, 0));
        final float[] c = r.position(new BlockPos(40, 70, 20));
        assertTrue(b[0] > a[0], "east is right");
        assertEquals(a[1], b[1], 1.0e-4f, "same z, same row");

        assertEquals((b[0] - a[0]) * 0.5f, c[1] - b[1], 1.0e-3f, "aspect kept");

        final int[] span = r.worldSpan();
        assertEquals(40, span[0]);
        assertEquals(6, span[1]);
        assertEquals(20, span[2]);
        assertEquals(64, span[3], "lowest element");
        assertEquals(0.0f, r.elevation(new BlockPos(0, 64, 0)), 1.0e-4f);
        assertEquals(1.0f, r.elevation(new BlockPos(40, 70, 20)), 1.0e-4f);
    }

    @Test
    void everyModeIsStillDeterministic() {
        final ScanGraph g = branching();
        for (final ScanLayoutMode mode : ScanLayoutMode.values()) {
            final ScanLayout.Result a = ScanLayout.layout(g, mode);
            final ScanLayout.Result b = ScanLayout.layout(g, mode);
            for (final Map.Entry<BlockPos, float[]> e : a.positions().entrySet()) {
                final float[] other = b.position(e.getKey());
                assertNotNull(other);
                assertEquals(e.getValue()[0], other[0], 1.0e-6f, mode + " x");
                assertEquals(e.getValue()[1], other[1], 1.0e-6f, mode + " y");
            }
        }
    }

    @Test
    void thePathToTheSourceIsTheChainTheDiagramHighlights() {
        final ScanLayout.Result r = ScanLayout.layout(weighted());
        final List<BlockPos> path = r.pathToRoot(at(2));
        assertEquals(List.of(at(2), at(1), at(0)), path, "nearest first, ending at the source");
        assertEquals(List.of(at(0)), r.pathToRoot(at(0)), "the source's own path is itself");
        assertEquals(at(1), r.parent(at(2)));
        assertTrue(r.children(at(0)).containsAll(List.of(at(1), at(3))));
    }

    @Test
    void anEmptyGraphSurvivesEveryMode() {
        for (final ScanLayoutMode mode : ScanLayoutMode.values()) {
            final ScanLayout.Result r = ScanLayout.layout(ScanGraph.EMPTY, mode);
            assertTrue(r.positions().isEmpty());
            assertEquals(0, r.depth());
            assertEquals(6, r.worldSpan().length, "the span is always the same shape");
            assertTrue(r.pathToRoot(at(0)).size() <= 1);
        }
    }

    private static ScanGraph branching() {
        final List<ScanNode> nodes = new ArrayList<>();
        final List<ScanEdge> edges = new ArrayList<>();
        nodes.add(node(at(0), ScanNodeKind.SOURCE, true, null));
        for (int i = 1; i <= 6; i++) {
            nodes.add(node(at(i), ScanNodeKind.MACHINE, false, at(i / 2)));
            edges.add(new ScanEdge(at(i / 2), at(i), 1.0f));
        }
        return graph(nodes, edges);
    }
}
