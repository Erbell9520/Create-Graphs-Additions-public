package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeServePolicyTest {
    private static final class Box implements IEnergyStorage {
        int energy;
        final int capacity;

        Box(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int accepted = Math.min(maxReceive, capacity - energy);
            if (accepted <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += accepted;
            }
            return accepted;
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
        private final IEnergyStorage attached;
        private int stored;

        Node(long key, IEnergyStorage attached) {
            this.key = key;
            this.attached = attached;
        }

        @Override
        public long nodeKey() {
            return key;
        }

        @Override
        public int storedEnergy() {
            return stored;
        }

        @Override
        public void setStoredEnergy(int amount) {
            stored = amount;
        }

        @Override
        public int bufferCapacity() {
            return 16_000;
        }

        @Override
        public int transferRate() {
            return 4_096;
        }

        @Override
        @Nullable
        public IEnergyStorage attachedStorage() {
            return attached;
        }
    }

    private static final class TablePolicy implements FeServePolicy {
        final Map<Long, FePriority> tiers = new HashMap<>();
        final Map<Long, Boolean> shed = new HashMap<>();

        @Override
        public int tierOrdinalOf(FeGridNode node) {
            return tiers.getOrDefault(node.nodeKey(), FePriority.DEFAULT).ordinal();
        }

        @Override
        public void markShed(FeGridNode node, boolean isShed) {
            shed.put(node.nodeKey(), isShed);
        }
    }

    @Test
    void aPolicyTierOrdersTheServeForALoadThatCarriesNoneItself() {
        final Box lamp = new Box(50_000);
        final Box foreign = new Box(50_000);
        final Node lampNode = new Node(1L, lamp);
        final Node foreignNode = new Node(2L, foreign);

        final TablePolicy policy = new TablePolicy();
        policy.tiers.put(1L, FePriority.OPTIONAL);
        policy.tiers.put(2L, FePriority.CRITICAL);

        final FeGridSolver.BranchResult result = FeGridSolver.serveByPriority(
                List.of(lampNode, foreignNode), 2_000, 0, null, null, policy);

        assertEquals(2_000, foreign.energy, "the Critical load takes the pool before anything below it");
        assertEquals(0, lamp.energy, "the Optional load gets what is left, which is nothing");
        assertEquals(1, result.shed(), "and is reported shed");
    }

    @Test
    void everyKindOfLoadIsToldItWasShed() {
        final Node critical = new Node(1L, new Box(50_000));
        final Node optional = new Node(2L, new Box(50_000));
        final TablePolicy policy = new TablePolicy();
        policy.tiers.put(1L, FePriority.CRITICAL);
        policy.tiers.put(2L, FePriority.OPTIONAL);

        FeGridSolver.serveByPriority(List.of(critical, optional), 2_000, 0, null, null, policy);

        assertFalse(policy.shed.get(1L), "the served tier is marked not shed");
        assertTrue(policy.shed.get(2L), "and the starved one is marked shed, with no latch to hold it");
    }

    @Test
    void aDarkBranchShedsNobody() {
        final Node a = new Node(1L, new Box(50_000));
        final Node b = new Node(2L, new Box(50_000));
        final TablePolicy policy = new TablePolicy();
        policy.tiers.put(1L, FePriority.CRITICAL);
        policy.tiers.put(2L, FePriority.OPTIONAL);

        final FeGridSolver.BranchResult result =
                FeGridSolver.serveByPriority(List.of(a, b), 0, 0, null, null, policy);

        assertEquals(0, result.shed(), "a branch with no pool at all sheds nobody");
        assertFalse(policy.shed.get(1L));
        assertFalse(policy.shed.get(2L));
    }

    @Test
    void aFlatServeClearsEveryKindOfLoad() {
        final Node node = new Node(1L, new Box(50_000));
        final TablePolicy policy = new TablePolicy();
        policy.markShed(node, true);

        FeGridSolver.serveFlat(List.of(node), 1_000, 0, null, policy);

        assertFalse(policy.shed.get(1L), "a flat serve clears the flag it finds");
    }

    @Test
    void theDefaultPolicyIsTheOldRuleExactly() {
        final FeConsumer fe = new FeConsumer(settings());
        fe.setPriority(FePriority.OPTIONAL);
        final Node consumer = new Node(1L, fe);
        final Node foreign = new Node(2L, new Box(50_000));

        assertEquals(FePriority.OPTIONAL.ordinal(), FeServePolicy.DEFAULT.tierOrdinalOf(consumer));
        assertEquals(FePriority.DEFAULT.ordinal(), FeServePolicy.DEFAULT.tierOrdinalOf(foreign));

        FeServePolicy.DEFAULT.markShed(consumer, true);
        assertTrue(fe.isLoadShed(), "the consumer is latched");

        FeServePolicy.DEFAULT.markShed(foreign, true);
    }

    @Test
    void anOutOfRangeTierCannotIndexPastTheBuckets() {
        // mis-tier, never throw. it's inside a BE tick

        final Box box = new Box(50_000);
        final Node node = new Node(1L, box);
        final List<FeGridNode> loads = new ArrayList<>(List.of(node));

        final FeGridSolver.BranchResult high = FeGridSolver.serveByPriority(loads, 1_000, 0, null, null,
                new FeServePolicy() {
                    @Override
                    public int tierOrdinalOf(FeGridNode n) {
                        return Integer.MAX_VALUE;
                    }

                    @Override
                    public void markShed(FeGridNode n, boolean shed) {
                    }
                });
        assertEquals(1_000, high.delivered(), "clamped to the lowest tier and still served");

        box.energy = 0;
        final FeGridSolver.BranchResult low = FeGridSolver.serveByPriority(loads, 1_000, 0, null, null,
                new FeServePolicy() {
                    @Override
                    public int tierOrdinalOf(FeGridNode n) {
                        return Integer.MIN_VALUE;
                    }

                    @Override
                    public void markShed(FeGridNode n, boolean shed) {
                    }
                });
        assertEquals(1_000, low.delivered(), "clamped to the highest tier and still served");
    }

    private static FeConsumer.Settings settings() {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public int capacity() {
                return 100_000;
            }

            @Override
            public int idleDrain() {
                return 0;
            }

            @Override
            public int activeDrain() {
                return 0;
            }

            @Override
            public int burstCost() {
                return 0;
            }

            @Override
            public int rebootThresholdPct() {
                return 0;
            }
        };
    }
}
