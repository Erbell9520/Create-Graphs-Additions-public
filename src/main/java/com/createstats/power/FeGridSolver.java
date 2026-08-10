package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** [1] */
public final class FeGridSolver {

    /** What one solve moved, for goggles, GUIs and the grid data source. */
    public record Result(int collected, int delivered, int stored, int capacity, int demand, int shed) {

        public static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0);

        /** Net FE per tick across the grid boundary. Positive means the grid is filling. */
        public int net() {
            return collected - delivered;
        }

        /** [2] */
        public int shortfall() {
            return Math.max(0, demand - delivered);
        }

        /** [3] */
        public int enduranceTicks() {
            final int drain = -net();
            if (drain <= 0) {
                return Integer.MAX_VALUE;
            }
            return stored / drain;
        }
    }

    private FeGridSolver() {
    }

    /**
     * Runs one solve over {@code nodes}. {@code cursor} rotates the fair-share starting point so no
     * machine is permanently last in line; callers pass an incrementing value.
     */
    public static Result solve(List<? extends FeGridNode> nodes, int cursor) {
        return solve(nodes, cursor, null);
    }

    /** [4] */
    public static Result solve(List<? extends FeGridNode> nodes, int cursor, @Nullable Map<Long, Integer> flowOut) {
        if (nodes.isEmpty()) {
            return Result.EMPTY;
        }

        int pool = 0;
        int capacity = 0;
        for (FeGridNode node : nodes) {
            pool += node.storedEnergy();
            capacity += node.bufferCapacity();
        }

        // [5]
        final List<FeGridNode> generators = new ArrayList<>();
        final List<FeGridNode> storage = new ArrayList<>();
        final List<FeGridNode> loads = new ArrayList<>();
        for (FeGridNode node : nodes) {
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null) {
                continue;
            }
            if (attached.canExtract() && attached.canReceive()) {
                storage.add(node);
            } else if (attached.canExtract()) {
                generators.add(node);
            } else if (attached.canReceive()) {
                loads.add(node);
            }
        }

        // Measured before anything moves, so it reflects what the machines wanted this tick rather
        // than what is left of their appetite after being partly served.
        final int loadDemand = simulateDemand(loads);

        // [6]
        final int intake = Math.max(0, capacity - pool) + loadDemand;

        // Phase 1: collect from generators.
        int collected = collectInto(generators, intake, null, flowOut);
        pool += collected;

        // [7]
        final Set<Long> discharged = new HashSet<>();
        if (pool < loadDemand) {
            final int fromStorage = collectInto(storage,
                    Math.min(loadDemand - pool, Math.max(0, intake - collected)), discharged, flowOut);
            collected += fromStorage;
            pool += fromStorage;
        }

        // Phase 3: serve the machines.
        int delivered = distribute(loads, pool, cursor, flowOut);
        pool -= delivered;

        // [8]
        int toStorage = 0;
        if (pool >= capacity && !storage.isEmpty() && !generators.isEmpty()) {
            for (FeGridNode sink : storage) {
                if (discharged.contains(sink.nodeKey())) {
                    continue;
                }
                final IEnergyStorage target = sink.attachedStorage();
                if (target == null) {
                    continue;
                }
                int budget = sink.transferRate();
                for (FeGridNode from : generators) {
                    if (budget <= 0) {
                        break;
                    }
                    final IEnergyStorage origin = from.attachedStorage();
                    if (origin == null) {
                        continue;
                    }
                    final int moved = transfer(origin, target, Math.min(budget, from.transferRate()));
                    budget -= moved;
                    toStorage += moved;
                    if (flowOut != null && moved > 0) {
                        flowOut.merge(sink.nodeKey(), moved, Integer::sum);
                        flowOut.merge(from.nodeKey(), -moved, Integer::sum);
                    }
                }
            }
        }
        collected += toStorage;
        delivered += toStorage;

        // Phase 3: settle. The rounding remainder rides on the first nodes that still have room,
        // so integer division loses nothing.
        int unplaced = pool;
        final int even = unplaced / nodes.size();
        for (FeGridNode node : nodes) {
            final int share = Math.min(even, node.bufferCapacity());
            node.setStoredEnergy(share);
            unplaced -= share;
        }
        for (FeGridNode node : nodes) {
            if (unplaced <= 0) {
                break;
            }
            final int room = node.bufferCapacity() - node.storedEnergy();
            final int extra = Math.min(room, unplaced);
            if (extra > 0) {
                node.setStoredEnergy(node.storedEnergy() + extra);
                unplaced -= extra;
            }
        }

        return new Result(collected, delivered, pool - unplaced, capacity, loadDemand, 0);
    }

    /** Total the given nodes' attached blocks would accept this tick, capped by node transfer rate. */
    private static int simulateDemand(List<FeGridNode> targets) {
        int demand = 0;
        for (FeGridNode node : targets) {
            final IEnergyStorage attached = node.attachedStorage();
            if (attached != null) {
                demand += attached.receiveEnergy(node.transferRate(), true);
            }
        }
        return demand;
    }

    private static int collectInto(List<FeGridNode> from, int budget) {
        return collectInto(from, budget, null, null);
    }

    /** [9] */
    private static int collectInto(List<FeGridNode> from, int budget, @Nullable Set<Long> touched,
                                   @Nullable Map<Long, Integer> flowOut) {
        int collected = 0;
        for (FeGridNode node : from) {
            final int remaining = budget - collected;
            if (remaining <= 0) {
                break;
            }
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null || !attached.canExtract()) {
                continue;
            }
            final int drawn = attached.extractEnergy(Math.min(node.transferRate(), remaining), false);
            if (drawn > 0) {
                collected += drawn;
                if (touched != null) {
                    touched.add(node.nodeKey());
                }
                if (flowOut != null) {
                    flowOut.merge(node.nodeKey(), -drawn, Integer::sum);
                }
            }
        }
        return collected;
    }

    /** [10] */
    private static int transfer(IEnergyStorage from, IEnergyStorage to, int limit) {
        if (limit <= 0) {
            return 0;
        }
        final int offered = from.extractEnergy(limit, true);
        if (offered <= 0) {
            return 0;
        }
        final int wanted = to.receiveEnergy(offered, true);
        if (wanted <= 0) {
            return 0;
        }
        final int drawn = from.extractEnergy(wanted, false);
        if (drawn <= 0) {
            return 0;
        }
        // Clamp to what the sink actually took, so a handler that reneges on its own simulate
        // cannot make this report a gain.
        return Math.min(drawn, to.receiveEnergy(drawn, false));
    }

    /** What one branch solve moved, and how the tiers fared. */
    public record BranchResult(int delivered, int served, int shed) {
        public static final BranchResult EMPTY = new BranchResult(0, 0, 0);
    }

    /** [11] */
    public static BranchResult serveByPriority(List<? extends FeGridNode> loads, int pool, int cursor) {
        if (loads.isEmpty() || pool <= 0) {
            markAll(loads, false);
            return BranchResult.EMPTY;
        }
        // Bucket by tier. Ordinal order is serve order, so a plain array indexed by ordinal keeps
        // the tiers in the right sequence without sorting.
        final FePriority[] tiers = FePriority.values();
        final List<List<FeGridNode>> byTier = new ArrayList<>(tiers.length);
        for (int i = 0; i < tiers.length; i++) {
            byTier.add(new ArrayList<>());
        }
        for (FeGridNode node : loads) {
            byTier.get(priorityOf(node).ordinal()).add(node);
        }

        int remaining = pool;
        int delivered = 0;
        int served = 0;
        int shed = 0;
        boolean anyServed = false;

        for (int tier = 0; tier < tiers.length; tier++) {
            final List<FeGridNode> group = byTier.get(tier);
            if (group.isEmpty()) {
                continue;
            }
            final int movedHere = remaining > 0 ? distribute(group, remaining, cursor) : 0;
            remaining -= movedHere;
            delivered += movedHere;

            if (movedHere > 0) {
                anyServed = true;
                for (FeGridNode node : group) {
                    setShed(node, false);
                }
                served += group.size();
            } else {
                // Nothing reached this tier. It is only a shed if something above it was served;
                // otherwise the whole branch is simply dark.
                final boolean isShed = anyServed;
                for (FeGridNode node : group) {
                    setShed(node, isShed);
                }
                if (isShed) {
                    shed += group.size();
                }
            }
        }
        return new BranchResult(delivered, served, shed);
    }

    /** [12] */
    public static BranchResult serveFlat(List<? extends FeGridNode> loads, int pool, int cursor) {
        markAll(loads, false);
        if (loads.isEmpty() || pool <= 0) {
            return BranchResult.EMPTY;
        }
        final int moved = distribute(loads, pool, cursor);
        return new BranchResult(moved, moved > 0 ? loads.size() : 0, 0);
    }

    /** [13] */
    private static FePriority priorityOf(FeGridNode node) {
        return node.attachedStorage() instanceof FeConsumer fe ? fe.priority() : FePriority.DEFAULT;
    }

    private static void setShed(FeGridNode node, boolean shed) {
        if (node.attachedStorage() instanceof FeConsumer fe) {
            fe.setLoadShed(shed);
        }
    }

    private static void markAll(List<? extends FeGridNode> loads, boolean shed) {
        for (FeGridNode node : loads) {
            setShed(node, shed);
        }
    }

    /** [14] */
    private static int distribute(List<? extends FeGridNode> targets, int pool, int cursor) {
        return distribute(targets, pool, cursor, null);
    }

    /** Same distribute, additionally recording each target's positive flow when {@code flowOut} is supplied. */
    private static int distribute(List<? extends FeGridNode> targets, int pool, int cursor,
                                  @Nullable Map<Long, Integer> flowOut) {
        if (targets.isEmpty() || pool <= 0) {
            return 0;
        }
        final int start = Math.floorMod(cursor, targets.size());
        final int fairShare = Math.max(1, pool / targets.size());
        final int[] given = new int[targets.size()];
        int remaining = pool;

        for (int i = 0; i < targets.size() && remaining > 0; i++) {
            final int index = (start + i) % targets.size();
            final FeGridNode node = targets.get(index);
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null) {
                continue;
            }
            final int offer = Math.min(Math.min(fairShare, node.transferRate()), remaining);
            if (offer <= 0) {
                continue;
            }
            final int taken = attached.receiveEnergy(offer, false);
            given[index] = taken;
            remaining -= taken;
            if (flowOut != null && taken > 0) {
                flowOut.merge(node.nodeKey(), taken, Integer::sum);
            }
        }
        for (int i = 0; i < targets.size() && remaining > 0; i++) {
            final int index = (start + i) % targets.size();
            final FeGridNode node = targets.get(index);
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null) {
                continue;
            }
            final int offer = Math.min(node.transferRate() - given[index], remaining);
            if (offer <= 0) {
                continue;
            }
            final int taken = attached.receiveEnergy(offer, false);
            remaining -= taken;
            if (flowOut != null && taken > 0) {
                flowOut.merge(node.nodeKey(), taken, Integer::sum);
            }
        }
        return pool - remaining;
    }
}
