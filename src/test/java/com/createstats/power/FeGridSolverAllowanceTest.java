package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeGridSolverAllowanceTest {
    private static final class Box implements IEnergyStorage {
        int energy;
        final int capacity;

        Box(int energy, int capacity) {
            this.energy = energy;
            this.capacity = capacity;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int taken = Math.max(0, Math.min(maxReceive, capacity - energy));
            if (!simulate) {
                energy += taken;
            }
            return taken;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private static final class Node implements FeGridNode {
        private final long key;
        private final int rate;
        final Box box;

        Node(long key, int rate, Box box) {
            this.key = key;
            this.rate = rate;
            this.box = box;
        }

        @Override
        public long nodeKey() {
            return key;
        }

        @Override
        public int storedEnergy() {
            return 0;
        }

        @Override
        public void setStoredEnergy(int amount) {
        }

        @Override
        public int bufferCapacity() {
            return 0;
        }

        @Override
        public int transferRate() {
            return rate;
        }

        @Override
        @Nullable
        public IEnergyStorage attachedStorage() {
            return box;
        }
    }

    private static List<Node> coldBranch() {
        final List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            nodes.add(new Node(i, 4096, new Box(0, 8000)));
        }
        return nodes;
    }

    private static Map<Long, Integer> allowanceOf(List<Node> nodes, int each) {
        final Map<Long, Integer> caps = new HashMap<>();
        for (Node node : nodes) {
            caps.put(node.nodeKey(), each);
        }
        return caps;
    }

    @Test
    @DisplayName("without an allowance the first cold machines drink the pool, which is the bug")
    void theOldBehaviourStarvesTheRestOfTheBranch() {
        final List<Node> nodes = coldBranch();
        FeGridSolver.serveByPriority(nodes, 400, 0, null);

        int fed = 0;
        for (Node node : nodes) {
            if (node.box.energy > 0) {
                fed++;
            }
        }
        assertEquals(4, fed);
    }

    @Test
    @DisplayName("one greedy cold machine takes everything the mop-up pass has left")
    void theMopUpPassIsWhereItGoesWrong() {
        final List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            nodes.add(new Node(i, 4096, new Box(900, 1000)));
        }
        final Node cold = new Node(3, 4096, new Box(0, 8000));
        nodes.add(cold);
        FeGridSolver.serveByPriority(nodes, 3000, 0, null);
        assertEquals(2700, cold.box.energy,
                "the cold machine swallowed everything the other three did not need");
    }

    @Test
    @DisplayName("with an allowance every device gets what it costs and no more")
    void theCapMakesTheAdmitDecisionMeanSomething() {
        final List<Node> nodes = coldBranch();
        final int moved = FeGridSolver.serveByPriority(nodes, 4000, 0, null, allowanceOf(nodes, 100))
                .delivered();
        assertEquals(400, moved, "four devices at 100 each, and the other 3,600 stays in the buffer");
        for (Node node : nodes) {
            assertEquals(100, node.box.energy);
        }
    }

    @Test
    @DisplayName("the allowance is spent across both passes, not once per pass")
    void theCapSurvivesTheMopUp() {
        final List<Node> nodes = coldBranch();
        FeGridSolver.serveByPriority(nodes, 100_000, 0, null, allowanceOf(nodes, 100));
        for (Node node : nodes) {
            assertEquals(100, node.box.energy, "one allowance per tick, not one per pass");
        }
    }

    @Test
    @DisplayName("a device with no entry in the map is uncapped, so a partial cost model still works")
    void anAbsentKeyMeansNoLimit() {
        final List<Node> nodes = coldBranch();
        final Map<Long, Integer> caps = new HashMap<>();
        caps.put(0L, 100);
        FeGridSolver.serveByPriority(nodes, 4000, 0, null, caps);
        assertEquals(100, nodes.get(0).box.energy);
        assertTrue(nodes.get(1).box.energy > 100, "the rest behave exactly as they did before");
    }

    @Test
    @DisplayName("the fill pass hands out top-ups without touching anything else")
    void deliverIsAPlainBoundedHandout() {
        final List<Node> nodes = coldBranch();

        FeGridSolver.serveByPriority(nodes, 4000, 0, null, allowanceOf(nodes, 100));

        final int filled = FeGridSolver.deliver(nodes, 4000, 0, null, allowanceOf(nodes, 200));
        assertEquals(800, filled);
        for (Node node : nodes) {
            assertEquals(300, node.box.energy);
        }
    }

    @Test
    @DisplayName("a fill pass with nothing spare moves nothing")
    void anEmptyPoolFillsNothing() {
        final List<Node> nodes = coldBranch();
        assertEquals(0, FeGridSolver.deliver(nodes, 0, 0, null, allowanceOf(nodes, 200)));
        for (Node node : nodes) {
            assertEquals(0, node.box.energy);
        }
    }

    @Test
    @DisplayName("a zero allowance delivers nothing rather than falling back to the rate")
    void zeroMeansZero() {
        final List<Node> nodes = coldBranch();
        FeGridSolver.serveByPriority(nodes, 4000, 0, null, allowanceOf(nodes, 0));
        for (Node node : nodes) {
            assertEquals(0, node.box.energy);
        }
    }

    @Test
    @DisplayName("flows are still recorded per node, so the wiring readouts keep working")
    void flowsAreReportedThroughTheCap() {
        final List<Node> nodes = coldBranch();
        final Map<Long, Integer> flows = new HashMap<>();
        FeGridSolver.serveByPriority(nodes, 4000, 0, flows, allowanceOf(nodes, 100));
        assertEquals(4, flows.size());
        for (Node node : nodes) {
            assertEquals(100, flows.get(node.nodeKey()));
        }
    }
}
