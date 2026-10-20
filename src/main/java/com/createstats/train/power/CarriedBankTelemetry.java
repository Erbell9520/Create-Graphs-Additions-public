package com.createstats.train.power;

import com.createstats.blockentity.CapacitorBankBE;
import com.createstats.power.FeBankMerge;
import com.createstats.power.FeRates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// carried banks have no handler and no tick, so derive flow from charge
public final class CarriedBankTelemetry {
    public record Cell(BlockPos pos, int energy, int capacity, int led) {
        public Cell(BlockPos pos, int energy, int capacity) {
            this(pos, energy, capacity, -1);
        }
    }

    public record Bank(BlockPos controller, List<BlockPos> members, int stored, int capacity,
                       int inRate, int outRate, int ledLevel) {
    }

    private static final float KEEP = 0.75f;
    private static final float TAKE = 0.25f;
    private static final float LED_DEADBAND = 0.65f;

    private final Map<BlockPos, Integer> lastEnergy = new HashMap<>();
    private final Map<BlockPos, Float> inFiltered = new HashMap<>();
    private final Map<BlockPos, Float> outFiltered = new HashMap<>();
    private final Map<BlockPos, Integer> lastLed = new HashMap<>();
    private boolean moved;
    private boolean flowing;

    public List<Bank> step(List<Cell> cells, int maxFootprint, int maxHeight) {
        final Map<BlockPos, Cell> byPos = new HashMap<>();
        for (Cell cell : cells) {
            byPos.put(cell.pos(), cell);
        }
        moved = false;
        flowing = false;
        final List<Bank> out = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        for (Cell start : cells) {
            if (!seen.add(start.pos())) {
                continue;
            }
            final List<BlockPos> cluster = flood(start.pos(), byPos, seen);
            for (List<BlockPos> members : FeBankMerge.partition(cluster, maxFootprint, maxHeight)) {
                out.add(bank(members, byPos));
            }
        }

        final Set<BlockPos> controllers = new HashSet<>();
        for (Bank bank : out) {
            controllers.add(bank.controller());
        }
        inFiltered.keySet().retainAll(controllers);
        outFiltered.keySet().retainAll(controllers);
        lastLed.keySet().retainAll(controllers);
        lastEnergy.keySet().retainAll(byPos.keySet());
        return out;
    }

    public boolean isSettled() {
        return !moved && !flowing;
    }

    private static List<BlockPos> flood(BlockPos start, Map<BlockPos, Cell> byPos, Set<BlockPos> seen) {
        final List<BlockPos> cluster = new ArrayList<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        cluster.add(start);
        while (!queue.isEmpty()) {
            final BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                final BlockPos next = at.relative(direction);
                if (byPos.containsKey(next) && seen.add(next)) {
                    cluster.add(next);
                    queue.add(next);
                }
            }
        }
        return cluster;
    }

    private Bank bank(List<BlockPos> members, Map<BlockPos, Cell> byPos) {
        final List<BlockPos> ordered = new ArrayList<>(members);
        ordered.sort(FeBankMerge.ORDER);
        final BlockPos controller = ordered.get(0);
        int stored = 0;
        int capacity = 0;
        int rawIn = 0;
        int rawOut = 0;
        for (BlockPos pos : ordered) {
            final Cell cell = byPos.get(pos);
            stored = FeRates.plus(stored, Math.max(0, cell.energy()));
            capacity = FeRates.plus(capacity, Math.max(0, cell.capacity()));
            final Integer before = lastEnergy.put(pos, cell.energy());
            if (before != null && before != cell.energy()) {
                moved = true;
                final int delta = cell.energy() - before;
                if (delta > 0) {
                    rawIn = FeRates.plus(rawIn, delta);
                } else {
                    rawOut = FeRates.plus(rawOut, -delta);
                }
            }
        }
        final float in = inFiltered.getOrDefault(controller, 0.0f) * KEEP + rawIn * TAKE;
        final float out = outFiltered.getOrDefault(controller, 0.0f) * KEEP + rawOut * TAKE;
        final int inRate = roundUp(in);
        final int outRate = roundUp(out);

        inFiltered.put(controller, inRate == 0 ? 0.0f : in);
        outFiltered.put(controller, outRate == 0 ? 0.0f : out);
        flowing |= inRate != 0 || outRate != 0;
        return new Bank(controller, List.copyOf(ordered), stored, capacity, inRate, outRate,
                led(controller, byPos.get(controller).led(), stored, capacity));
    }

    private int led(BlockPos controller, int assembled, int stored, int capacity) {
        final float scaled = capacity <= 0 ? 0.0f
                : Math.max(0.0f, Math.min(1.0f, (float) stored / capacity)) * CapacitorBankBE.LED_SEGMENTS;
        int lit = Math.max(0, Math.min(CapacitorBankBE.LED_SEGMENTS, Math.round(scaled)));
        Integer last = lastLed.get(controller);
        if (last == null && assembled >= 0) {
            last = assembled;
        }
        if (last != null && Math.abs(scaled - last) < LED_DEADBAND) {
            lit = last;
        }
        lastLed.put(controller, lit);
        return lit;
    }

    private static int roundUp(float v) {
        return v > 0.0f ? (int) Math.ceil(v - 0.001f) : 0;
    }
}
