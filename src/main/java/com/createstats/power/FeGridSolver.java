package com.createstats.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// pooled solve. the old pairwise diffusion halved every hop and was iteration order dependent
public final class FeGridSolver {
    public record Result(int collected, int delivered, int stored, int capacity, int demand, int shed,
                         int charged, int discharged, int reserve, int reserveCapacity, int spilled) {
        public static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        public int net() {
            return collected - delivered - charged;
        }

        public int generated() {
            return Math.max(0, collected - discharged) + charged;
        }

        public int consumed() {
            return delivered + charged;
        }

        public int reserveNet() {
            return charged - discharged;
        }

        public int shortfall() {
            return Math.max(0, demand - delivered);
        }

        public int available() {
            return reserve + stored;
        }

        public int enduranceTicks() {
            final int drain = -reserveNet();
            if (drain <= 0) {
                return Integer.MAX_VALUE;
            }
            return available() / drain;
        }
    }

    public static final int NO_CEILING = Integer.MAX_VALUE;

    private FeGridSolver() {
    }

    public static Result solve(List<? extends FeGridNode> nodes, int cursor) {
        return solve(nodes, cursor, null);
    }

    public static Result solve(List<? extends FeGridNode> nodes, int cursor, boolean cascade) {
        return solve(nodes, cursor, null, cascade);
    }

    public static Result solve(List<? extends FeGridNode> nodes, int cursor, @Nullable Map<Long, Integer> flowOut) {
        return solve(nodes, cursor, flowOut, true);
    }

    public static Result solve(List<? extends FeGridNode> nodes, int cursor,
                               @Nullable Map<Long, Integer> flowOut, boolean cascade) {
        return solve(nodes, cursor, flowOut, cascade, NO_CEILING);
    }

    public static Result solve(List<? extends FeGridNode> nodes, int cursor,
                               @Nullable Map<Long, Integer> flowOut, boolean cascade, int ceiling) {
        if (nodes.isEmpty()) {
            return Result.EMPTY;
        }

        int pool = 0;
        int capacity = 0;
        for (FeGridNode node : nodes) {
            pool += node.storedEnergy();
            capacity += node.bufferCapacity();
        }

        final List<FeGridNode> generators = new ArrayList<>();
        final List<FeGridNode> storage = new ArrayList<>();
        final List<FeGridNode> loads = new ArrayList<>();
        final Set<IEnergyStorage> claimedHandlers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final Set<Long> claimedPools = new HashSet<>();
        for (FeGridNode node : nodes) {
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null || !claimedHandlers.add(attached)) {
                continue;
            }
            switch (FeNodeRole.of(attached, node.transferRate()).kind()) {
                case GENERATOR -> generators.add(node);
                case LOAD -> loads.add(node);

                case STORAGE -> {
                    if (claimTank(attached, claimedPools)) {
                        storage.add(node);
                    }
                }
                case NONE -> {
                }
            }
        }

        final List<FeGridNode> dischargeOrder = cascade ? orderedByRank(storage, false) : storage;
        final List<FeGridNode> chargeOrder = cascade ? orderedByRank(storage, true) : storage;

        int reserve = 0;
        int reserveCapacity = 0;
        for (FeGridNode node : storage) {
            final IEnergyStorage tank = node.attachedStorage();
            if (tank == null) {
                continue;
            }
            reserve = FeRates.plus(reserve, Math.max(0, tank.getEnergyStored()));
            reserveCapacity = FeRates.plus(reserveCapacity,
                    Math.max(0, tank.getMaxEnergyStored()));
        }

        final Map<Long, Integer> asked = new HashMap<>();

        int intakeLeft = ceiling >= NO_CEILING ? NO_CEILING : Math.max(0, ceiling);
        int outflowLeft = intakeLeft;

        final int loadDemand = simulateDemand(loads, false);

        final int servable = simulateDemand(loads, true);

        int collected = spreadInto(generators,
                Math.min(Math.max(0, servable - pool), intakeLeft), flowOut, asked);
        pool += collected;
        intakeLeft -= collected;

        final Set<Long> dischargedNodes = new HashSet<>();
        int discharged = 0;
        if (pool < servable) {
            discharged = collectInto(dischargeOrder, Math.min(servable - pool, intakeLeft),
                    dischargedNodes, flowOut, asked);
            collected += discharged;
            pool += discharged;
            intakeLeft -= discharged;
        }

        int delivered = distribute(loads, Math.min(pool, outflowLeft), cursor, flowOut);
        pool -= delivered;
        outflowLeft -= delivered;

        int charged = chargeStorage(chargeOrder, generators, dischargedNodes, asked, flowOut,
                Math.min(intakeLeft, outflowLeft));
        intakeLeft -= charged;
        outflowLeft -= charged;

        final int toppedUp = spreadInto(generators,
                Math.min(Math.max(0, capacity - pool), intakeLeft), flowOut, asked);
        collected += toppedUp;
        pool += toppedUp;
        intakeLeft -= toppedUp;

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

        if (unplaced > 0 && !storage.isEmpty()) {
            for (FeGridNode sink : chargeOrder) {
                if (unplaced <= 0) {
                    break;
                }
                final IEnergyStorage target = sink.attachedStorage();
                if (target == null) {
                    continue;
                }
                final int tucked = target.receiveEnergy(Math.min(unplaced, sink.transferRate()), false);
                if (tucked > 0) {
                    charged += tucked;
                    unplaced -= tucked;
                    if (flowOut != null) {
                        flowOut.merge(sink.nodeKey(), tucked, Integer::sum);
                    }
                }
            }
        }

        return new Result(collected, delivered, pool - unplaced, capacity, loadDemand, 0,
                charged, discharged, reserve, reserveCapacity, unplaced);
    }

    private static int chargeStorage(List<FeGridNode> sinks, List<FeGridNode> sources,
                                     Set<Long> dischargedNodes, Map<Long, Integer> asked,
                                     @Nullable Map<Long, Integer> flowOut, int ceilingLeft) {
        if (sinks.isEmpty() || sources.isEmpty() || ceilingLeft <= 0) {
            return 0;
        }
        int charged = 0;
        for (FeGridNode sink : sinks) {
            if (charged >= ceilingLeft) {
                break;
            }
            if (dischargedNodes.contains(sink.nodeKey())) {
                continue;
            }
            final IEnergyStorage target = sink.attachedStorage();
            if (target == null) {
                continue;
            }

            int budget = Math.min(Math.min(Math.min(sink.transferRate(), ceilingLeft - charged),
                    sink.deliveryAllowance()), target.receiveEnergy(sink.transferRate(), true));
            for (FeGridNode from : sources) {
                if (budget <= 0) {
                    break;
                }
                final IEnergyStorage origin = from.attachedStorage();
                if (origin == null) {
                    continue;
                }

                final int allowance = remainingAllowance(from, asked);
                if (allowance <= 0) {
                    continue;
                }
                final int limit = Math.min(budget, allowance);
                final int moved = transfer(origin, target, limit);
                book(asked, from.nodeKey(), limit);
                sink.bookDelivery(moved);
                budget -= moved;
                charged += moved;
                if (flowOut != null && moved > 0) {
                    flowOut.merge(sink.nodeKey(), moved, Integer::sum);
                    flowOut.merge(from.nodeKey(), -moved, Integer::sum);
                }
            }
        }
        return charged;
    }

    private static boolean claimTank(IEnergyStorage handler, Set<Long> claimedPools) {
        if (!(handler instanceof FeReserve pooled)) {
            return true;
        }
        final long key = pooled.reservePoolKey();
        return key == FeReserve.NOT_POOLED || claimedPools.add(key);
    }

    private static int remainingAllowance(FeGridNode node, Map<Long, Integer> asked) {
        return Math.max(0, node.transferRate() - asked.getOrDefault(node.nodeKey(), 0));
    }

    private static void book(Map<Long, Integer> asked, long key, int amount) {
        if (amount > 0) {
            asked.merge(key, amount, Integer::sum);
        }
    }

    public static Result observe(List<? extends FeGridNode> nodes) {
        if (nodes.isEmpty()) {
            return Result.EMPTY;
        }
        int stored = 0;
        int capacity = 0;
        int reserve = 0;
        int reserveCapacity = 0;
        int demand = 0;

        final Set<IEnergyStorage> claimedHandlers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final Set<Long> claimedPools = new HashSet<>();
        for (FeGridNode node : nodes) {
            stored += node.storedEnergy();
            capacity += node.bufferCapacity();
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null || !claimedHandlers.add(attached)) {
                continue;
            }
            switch (FeNodeRole.of(attached, node.transferRate()).kind()) {
                case STORAGE -> {
                    if (claimTank(attached, claimedPools)) {
                        reserve = FeRates.plus(reserve, Math.max(0, attached.getEnergyStored()));
                        reserveCapacity = FeRates.plus(reserveCapacity,
                                Math.max(0, attached.getMaxEnergyStored()));
                    }
                }
                case LOAD -> demand += attached.receiveEnergy(node.transferRate(), true);
                default -> {
                }
            }
        }
        return new Result(0, 0, stored, capacity, demand, 0, 0, 0, reserve, reserveCapacity, 0);
    }

    private static int simulateDemand(List<FeGridNode> targets, boolean wired) {
        int demand = 0;
        for (FeGridNode node : targets) {
            final IEnergyStorage attached = node.attachedStorage();
            if (attached != null) {
                final int ask = wired
                        ? Math.min(node.transferRate(), Math.max(0, node.deliveryAllowance()))
                        : node.transferRate();
                if (ask > 0) {
                    demand = FeRates.plus(demand, attached.receiveEnergy(ask, true));
                }
            }
        }
        return demand;
    }

    private static int spreadInto(List<FeGridNode> from, int budget, @Nullable Map<Long, Integer> flowOut,
                                  Map<Long, Integer> asked) {
        if (budget <= 0 || from.isEmpty()) {
            return 0;
        }
        int eligible = 0;
        for (FeGridNode node : from) {
            final IEnergyStorage attached = node.attachedStorage();
            if (attached != null && attached.canExtract() && remainingAllowance(node, asked) > 0) {
                eligible++;
            }
        }
        int collected = 0;
        if (eligible > 1) {
            final int share = Math.max(1, budget / eligible);
            for (FeGridNode node : from) {
                final int remaining = budget - collected;
                if (remaining <= 0) {
                    break;
                }
                final IEnergyStorage attached = node.attachedStorage();
                if (attached == null || !attached.canExtract()) {
                    continue;
                }
                final int allowance = Math.min(Math.min(remainingAllowance(node, asked), share), remaining);
                if (allowance <= 0) {
                    continue;
                }
                book(asked, node.nodeKey(), allowance);
                final int drawn = attached.extractEnergy(allowance, false);
                if (drawn > 0) {
                    collected += drawn;
                    if (flowOut != null) {
                        flowOut.merge(node.nodeKey(), -drawn, Integer::sum);
                    }
                }
            }
        }
        return collected + collectInto(from, budget - collected, null, flowOut, asked);
    }

    private static int collectInto(List<FeGridNode> from, int budget, @Nullable Set<Long> touched,
                                   @Nullable Map<Long, Integer> flowOut, Map<Long, Integer> asked) {
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
            final int allowance = Math.min(remainingAllowance(node, asked), remaining);
            if (allowance <= 0) {
                continue;
            }
            book(asked, node.nodeKey(), allowance);
            final int drawn = attached.extractEnergy(allowance, false);
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

    private static int transfer(IEnergyStorage from, IEnergyStorage to, int limit) {
        if (limit <= 0) {
            return 0;
        }
        final int offered = from.extractEnergy(limit, true);
        if (offered <= 0) {
            return 0;
        }
        final int taken = to.receiveEnergy(offered, false);
        if (taken <= 0) {
            return 0;
        }
        final int drawn = from.extractEnergy(taken, false);
        if (drawn >= taken) {
            return taken;
        }

        if (to.canExtract()) {
            to.extractEnergy(taken - drawn, false);
        }
        return Math.max(0, drawn);
    }

    public record BranchResult(int delivered, int served, int shed) {
        public static final BranchResult EMPTY = new BranchResult(0, 0, 0);
    }

    // separate path on purpose. sorting + the flat body still hands the low tier a slice
    public static BranchResult serveByPriority(List<? extends FeGridNode> loads, int pool, int cursor) {
        return serveByPriority(loads, pool, cursor, null);
    }

    public static BranchResult serveByPriority(List<? extends FeGridNode> loads, int pool, int cursor,
                                               @Nullable Map<Long, Integer> flowOut) {
        return serveByPriority(loads, pool, cursor, flowOut, null);
    }

    public static BranchResult serveByPriority(List<? extends FeGridNode> loads, int pool, int cursor,
                                               @Nullable Map<Long, Integer> flowOut,
                                               @Nullable Map<Long, Integer> allowance) {
        return serveByPriority(loads, pool, cursor, flowOut, allowance, FeServePolicy.DEFAULT);
    }

    public static BranchResult serveByPriority(List<? extends FeGridNode> loads, int pool, int cursor,
                                               @Nullable Map<Long, Integer> flowOut,
                                               @Nullable Map<Long, Integer> allowance,
                                               FeServePolicy policy) {
        if (loads.isEmpty() || pool <= 0) {
            markAll(loads, false, policy);
            return BranchResult.EMPTY;
        }

        final FePriority[] tiers = FePriority.values();
        final List<List<FeGridNode>> byTier = new ArrayList<>(tiers.length);
        for (int i = 0; i < tiers.length; i++) {
            byTier.add(new ArrayList<>());
        }
        for (FeGridNode node : loads) {
            final int tier = Math.max(0, Math.min(tiers.length - 1, policy.tierOrdinalOf(node)));
            byTier.get(tier).add(node);
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
            final int movedHere = remaining > 0
                    ? distribute(group, remaining, cursor, flowOut, allowance) : 0;
            remaining -= movedHere;
            delivered += movedHere;

            if (movedHere > 0) {
                anyServed = true;
                for (FeGridNode node : group) {
                    policy.markShed(node, false);
                }
                served += group.size();
            } else {
                final boolean isShed = anyServed;
                for (FeGridNode node : group) {
                    policy.markShed(node, isShed);
                }
                if (isShed) {
                    shed += group.size();
                }
            }
        }
        return new BranchResult(delivered, served, shed);
    }

    public static BranchResult serveFlat(List<? extends FeGridNode> loads, int pool, int cursor) {
        return serveFlat(loads, pool, cursor, null);
    }

    public static BranchResult serveFlat(List<? extends FeGridNode> loads, int pool, int cursor,
                                         @Nullable Map<Long, Integer> flowOut) {
        return serveFlat(loads, pool, cursor, flowOut, FeServePolicy.DEFAULT);
    }

    public static BranchResult serveFlat(List<? extends FeGridNode> loads, int pool, int cursor,
                                         @Nullable Map<Long, Integer> flowOut,
                                         FeServePolicy policy) {
        markAll(loads, false, policy);
        if (loads.isEmpty() || pool <= 0) {
            return BranchResult.EMPTY;
        }
        final int moved = distribute(loads, pool, cursor, flowOut);
        return new BranchResult(moved, moved > 0 ? loads.size() : 0, 0);
    }

    public static int reserveRankOf(FeGridNode node) {
        return node.attachedStorage() instanceof FeReserve reserve
                ? FeReserve.clamp(reserve.reserveRank()) : FeReserve.DEFAULT_RANK;
    }

    private static List<FeGridNode> orderedByRank(List<FeGridNode> storage, boolean coreFirst) {
        if (storage.size() < 2) {
            return storage;
        }
        final List<FeGridNode> ordered = new ArrayList<>(storage);
        ordered.sort((a, b) -> {
            final int rankA = reserveRankOf(a);
            final int rankB = reserveRankOf(b);
            if (rankA != rankB) {
                return coreFirst ? Integer.compare(rankA, rankB) : Integer.compare(rankB, rankA);
            }
            return Long.compare(a.nodeKey(), b.nodeKey());
        });
        return ordered;
    }

    private static void markAll(List<? extends FeGridNode> loads, boolean shed, FeServePolicy policy) {
        for (FeGridNode node : loads) {
            policy.markShed(node, shed);
        }
    }

    private static int distribute(List<? extends FeGridNode> targets, int pool, int cursor) {
        return distribute(targets, pool, cursor, null);
    }

    private static int distribute(List<? extends FeGridNode> targets, int pool, int cursor,
                                  @Nullable Map<Long, Integer> flowOut) {
        return distribute(targets, pool, cursor, flowOut, null);
    }

    public static int deliver(List<? extends FeGridNode> targets, int pool, int cursor,
                              @Nullable Map<Long, Integer> flowOut,
                              @Nullable Map<Long, Integer> allowance) {
        return distribute(targets, pool, cursor, flowOut, allowance);
    }

    private static int distribute(List<? extends FeGridNode> targets, int pool, int cursor,
                                  @Nullable Map<Long, Integer> flowOut,
                                  @Nullable Map<Long, Integer> allowance) {
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
            final int cap = capFor(node, allowance);

            final int room = Math.min(Math.min(node.transferRate(), cap), node.deliveryAllowance());
            final int offer = Math.min(Math.min(fairShare, room), remaining);
            if (offer <= 0) {
                continue;
            }
            final int taken = attached.receiveEnergy(offer, false);
            given[index] = taken;
            remaining -= taken;
            if (taken > 0) {
                node.bookDelivery(taken);
                if (flowOut != null) {
                    flowOut.merge(node.nodeKey(), taken, Integer::sum);
                }
            }
        }
        for (int i = 0; i < targets.size() && remaining > 0; i++) {
            final int index = (start + i) % targets.size();
            final FeGridNode node = targets.get(index);
            final IEnergyStorage attached = node.attachedStorage();
            if (attached == null) {
                continue;
            }
            final int cap = capFor(node, allowance);

            final int room = Math.min(Math.min(node.transferRate(), cap) - given[index],
                    node.deliveryAllowance());
            final int offer = Math.min(room, remaining);
            if (offer <= 0) {
                continue;
            }
            final int taken = attached.receiveEnergy(offer, false);
            remaining -= taken;
            if (taken > 0) {
                node.bookDelivery(taken);
                if (flowOut != null) {
                    flowOut.merge(node.nodeKey(), taken, Integer::sum);
                }
            }
        }
        return pool - remaining;
    }

    private static int capFor(FeGridNode node, @Nullable Map<Long, Integer> allowance) {
        if (allowance == null) {
            return NO_CEILING;
        }
        final Integer cap = allowance.get(node.nodeKey());
        return cap == null ? NO_CEILING : Math.max(0, cap);
    }
}
