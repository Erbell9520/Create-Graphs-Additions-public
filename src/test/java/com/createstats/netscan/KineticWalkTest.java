package com.createstats.netscan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.netscan.scan.KineticWalk;
import com.createstats.netscan.scan.ScanEdge;
import com.createstats.netscan.scan.ScanGraph;
import com.createstats.netscan.scan.ScanNode;
import com.createstats.netscan.scan.ScanNodeKind;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticWalkTest {
    private static final class FakeWorld implements KineticWalk.NeighbourSource {
        private final Map<BlockPos, List<BlockPos>> links = new HashMap<>();
        private final Map<Long, Float> ratios = new HashMap<>();
        private final List<BlockPos> unloaded = new ArrayList<>();
        int probes;

        FakeWorld link(BlockPos a, BlockPos b) {
            return link(a, b, 1.0f);
        }

        FakeWorld link(BlockPos a, BlockPos b, float ratio) {
            this.links.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            this.links.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
            this.ratios.put(key(a, b), ratio);
            this.ratios.put(key(b, a), ratio);
            return this;
        }

        FakeWorld offline(BlockPos pos) {
            this.unloaded.add(pos);
            return this;
        }

        private static long key(BlockPos a, BlockPos b) {
            return a.asLong() * 1_000_003L ^ b.asLong();
        }

        @Override
        public ScanNode probe(BlockPos pos) {
            if (!this.links.containsKey(pos)) {
                return null;
            }
            this.probes++;
            return new ScanNode(pos, "n" + pos.getX(), "test:n", ScanNodeKind.TRANSFER,
                    32.0f, 0.0f, 0.0f, 0.0f, false, false, null);
        }

        @Override
        public List<BlockPos> neighbours(BlockPos pos) {
            return this.links.getOrDefault(pos, List.of());
        }

        @Override
        public float ratio(BlockPos from, BlockPos to) {
            return this.ratios.getOrDefault(key(from, to), 1.0f);
        }

        @Override
        public boolean unloaded(BlockPos pos) {
            return this.unloaded.contains(pos);
        }
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 64, 0);
    }

    private static ScanGraph run(KineticWalk walk, KineticWalk.NeighbourSource src) {
        int guard = 10_000;
        while (walk.step(1, src) && guard-- > 0) {
        }
        assertTrue(guard > 0, "walk did not terminate");
        return walk.build(1L, 0L);
    }

    @Test
    void aChainIsWalkedEndToEnd() {
        final FakeWorld world = new FakeWorld().link(at(0), at(1)).link(at(1), at(2)).link(at(2), at(3));
        final ScanGraph g = run(new KineticWalk(at(0), 64), world);
        assertEquals(4, g.size());
        assertEquals(3, g.edges().size());
        assertFalse(g.truncated());
    }

    @Test
    void theCapIsOnElementsAndSaysSoWhenItBites() {
        final FakeWorld world = new FakeWorld();
        for (int i = 0; i < 40; i++) {
            world.link(at(i), at(i + 1));
        }
        final ScanGraph g = run(new KineticWalk(at(0), 10), world);
        assertEquals(10, g.size(), "the cap is a count of elements, not a distance");
        assertTrue(g.truncated(), "a survey cut short has to admit it");
    }

    @Test
    void aCompleteWalkIsNotMarkedTruncated() {
        final FakeWorld world = new FakeWorld().link(at(0), at(1)).link(at(1), at(2));
        final ScanGraph g = run(new KineticWalk(at(0), 3), world);
        assertEquals(3, g.size());
        assertFalse(g.truncated(), "exactly filling the budget is not the same as overflowing it");
    }

    @Test
    void parentsAlwaysPrecedeChildren() {
        final FakeWorld world = new FakeWorld()
                .link(at(0), at(1)).link(at(0), at(2))
                .link(at(1), at(3)).link(at(2), at(4));
        final ScanGraph g = run(new KineticWalk(at(0), 64), world);
        final List<BlockPos> order = new ArrayList<>(g.nodes().keySet());
        assertEquals(at(0), order.get(0), "the seed comes first");
        assertTrue(order.indexOf(at(1)) < order.indexOf(at(3)));
        assertTrue(order.indexOf(at(2)) < order.indexOf(at(4)));
    }

    @Test
    void aCycleTerminatesAndKeepsTheClosingEdge() {
        final FakeWorld world = new FakeWorld()
                .link(at(0), at(1)).link(at(1), at(2)).link(at(2), at(0));
        final ScanGraph g = run(new KineticWalk(at(0), 64), world);
        assertEquals(3, g.size());
        assertEquals(3, g.edges().size(), "the closing edge survives even though its far end was seen");
        assertEquals(3, world.probes, "no element is read twice");
    }

    @Test
    void gearingRatiosSurviveOntoTheEdge() {
        final FakeWorld world = new FakeWorld().link(at(0), at(1), 2.0f).link(at(1), at(2), -1.0f);
        final ScanGraph g = run(new KineticWalk(at(0), 64), world);
        ScanEdge step = null;
        ScanEdge reversal = null;
        for (final ScanEdge edge : g.edges()) {
            if (edge.from().equals(at(0)) || edge.to().equals(at(0))) {
                step = edge;
            } else {
                reversal = edge;
            }
        }
        assertNotNull(step);
        assertNotNull(reversal);
        assertTrue(step.isRatioChange(), "a doubling is a speed change");
        assertFalse(step.isReversal());
        assertFalse(reversal.isRatioChange(), "a gearbox reverses without changing magnitude");
        assertTrue(reversal.isReversal());
    }

    @Test
    void anUnloadedNeighbourBecomesABoundaryRatherThanAGap() {
        final FakeWorld world = new FakeWorld().link(at(0), at(1)).link(at(1), at(2)).offline(at(2));
        final ScanGraph g = run(new KineticWalk(at(0), 64), world);
        assertEquals(2, g.size());
        assertTrue(g.unloadedBoundary().contains(at(2)),
                "an unloaded element is a known edge of the survey, not the end of the network");
    }

    @Test
    void aSeedThatIsNotKineticProducesNothingAndSaysSo() {
        final KineticWalk walk = new KineticWalk(at(9), 64);
        final ScanGraph g = run(walk, new FakeWorld().link(at(0), at(1)));
        assertTrue(g.isEmpty());
        assertTrue(walk.deadSeed());
    }

    @Test
    void aNullSeedIsSurvivable() {
        final KineticWalk walk = new KineticWalk(null, 64);
        assertFalse(walk.step(4, new FakeWorld()));
        assertTrue(walk.deadSeed());
        assertTrue(walk.build(0L, 0L).isEmpty());
    }

    @Test
    void edgesNeverPointAtAnElementTheSurveyDidNotReach() {
        final FakeWorld world = new FakeWorld();
        for (int i = 0; i < 20; i++) {
            world.link(at(i), at(i + 1));
        }
        final ScanGraph g = run(new KineticWalk(at(0), 5), world);
        for (final ScanEdge edge : g.edges()) {
            assertNotNull(g.node(edge.from()));
            assertNotNull(g.node(edge.to()));
        }
        assertNull(g.node(at(19)));
    }
}
