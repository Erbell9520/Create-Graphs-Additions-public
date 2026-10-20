package com.createstats.power.ponder;

import com.createstats.power.FeGridNode;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeReserve;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FePonderGrid {
    private FePonderGrid() {
    }

    public enum Kind {
        DYNAMO,

        MACHINE,

        BANK
    }

    public record Part(String name, Kind kind, int buffer, int rate, int draw, int start) {
    }

    public static final class Script {
        final int length;
        final Map<Integer, List<int[]>> rpm = new HashMap<>();
        final Map<Integer, List<int[]>> link = new HashMap<>();
        final Map<Integer, List<int[]>> fill = new HashMap<>();

        public Script(int length) {
            this.length = length;
        }

        public Script rpm(int tick, int part, int rpm) {
            this.rpm.computeIfAbsent(tick, k -> new ArrayList<>()).add(new int[] {part, rpm});
            return this;
        }

        public Script link(int tick, int part, boolean on) {
            this.link.computeIfAbsent(tick, k -> new ArrayList<>()).add(new int[] {part, on ? 1 : 0});
            return this;
        }

        public Script set(int tick, int part, int energy) {
            this.fill.computeIfAbsent(tick, k -> new ArrayList<>()).add(new int[] {part, energy});
            return this;
        }
    }

    public static final class Recording {
        public final int length;
        public final List<Part> parts;

        public final int[][] energy;

        public final int[][] flow;

        public final boolean[][] working;
        public final boolean[][] onGrid;
        public final int[][] rpm;

        Recording(List<Part> parts, int length) {
            this.parts = List.copyOf(parts);
            this.length = length;
            final int n = parts.size();
            energy = new int[n][length];
            flow = new int[n][length];
            working = new boolean[n][length];
            onGrid = new boolean[n][length];
            rpm = new int[n][length];
        }

        public int at(int tick) {
            return Math.max(0, Math.min(length - 1, tick));
        }

        public int index(String name) {
            for (int i = 0; i < parts.size(); i++) if (parts.get(i).name().equals(name)) return i;
            throw new IllegalArgumentException("no part " + name);
        }

        public float fill(int i, int t) {
            final int cap = parts.get(i).buffer();
            return cap <= 0 ? 0f : energy[i][at(t)] / (float) cap;
        }

        public int firstFlow(int i, boolean into, int from) {
            for (int t = Math.max(0, from); t < length; t++) {
                if (into ? flow[i][t] > 0 : flow[i][t] < 0) return t;
            }
            return -1;
        }
    }

    public static Recording run(List<Part> parts, Script script, double fePerRpm) {
        return run(parts, script, fePerRpm, 16_000);
    }

    public static Recording run(List<Part> parts, Script script, double fePerRpm, int conduit) {
        final int n = parts.size();
        final Recording rec = new Recording(parts, script.length);
        final Handler[] handlers = new Handler[n];
        final Node[] nodes = new Node[n];
        final boolean[] on = new boolean[n];
        final int[] speed = new int[n];
        for (int i = 0; i < n; i++) {
            handlers[i] = parts.get(i).kind() == Kind.BANK ? new Tank(parts.get(i), i) : new Handler(parts.get(i), i);
            nodes[i] = new Node(i, handlers[i], conduit);
        }
        final Map<Long, Integer> flowOut = new HashMap<>();
        for (int t = 0; t < script.length; t++) {
            for (int[] e : script.link.getOrDefault(t, List.of())) on[e[0]] = e[1] == 1;
            for (int[] e : script.rpm.getOrDefault(t, List.of())) speed[e[0]] = e[1];
            for (int[] e : script.fill.getOrDefault(t, List.of())) handlers[e[0]].energy = e[1];

            for (int i = 0; i < n; i++) {
                final Part p = parts.get(i);
                if (p.kind() == Kind.DYNAMO) {
                    final int made = speed[i] == 0 ? 0 : (int) Math.floor(Math.abs(speed[i]) * fePerRpm);
                    handlers[i].energy = Math.min(p.buffer(), handlers[i].energy + made);
                }
            }
            final List<Node> grid = new ArrayList<>();
            for (int i = 0; i < n; i++) if (on[i]) grid.add(nodes[i]);
            flowOut.clear();
            if (!grid.isEmpty()) FeGridSolver.solve(grid, t, flowOut, true);
            for (int i = 0; i < n; i++) {
                final Part p = parts.get(i);
                final Handler h = handlers[i];
                if (p.kind() == Kind.MACHINE && h.energy >= p.draw() && p.draw() > 0) {
                    h.energy -= p.draw();
                    rec.working[i][t] = true;
                }
                rec.energy[i][t] = h.energy;
                rec.flow[i][t] = flowOut.getOrDefault(nodes[i].nodeKey(), 0);
                rec.onGrid[i][t] = on[i];
                rec.rpm[i][t] = speed[i];
            }
        }
        return rec;
    }

    private static class Handler implements IEnergyStorage {
        final Part part;
        final int index;
        int energy;

        Handler(Part part, int index) {
            this.part = part;
            this.index = index;
            this.energy = part.start();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (part.kind() == Kind.DYNAMO) return 0;
            final int take = Math.max(0, Math.min(maxReceive, Math.min(part.rate(), part.buffer() - energy)));
            if (!simulate) energy += take;
            return take;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (part.kind() == Kind.MACHINE) return 0;
            final int give = Math.max(0, Math.min(maxExtract, Math.min(part.rate(), energy)));
            if (!simulate) energy -= give;
            return give;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return part.buffer();
        }

        @Override
        public boolean canExtract() {
            return part.kind() != Kind.MACHINE;
        }

        @Override
        public boolean canReceive() {
            return part.kind() != Kind.DYNAMO;
        }
    }

    private static final class Tank extends Handler implements FeReserve {
        Tank(Part part, int index) {
            super(part, index);
        }

        @Override
        public int reserveRank() {
            return FeReserve.DEFAULT_RANK;
        }

        @Override
        public long reservePoolKey() {
            return index + 1L;
        }
    }

    private static final class Node implements FeGridNode {
        final int index;
        final Handler handler;
        final int conduit;
        int stored;

        Node(int index, Handler handler, int conduit) {
            this.index = index;
            this.handler = handler;
            this.conduit = conduit;
        }

        @Override
        public long nodeKey() {
            return index + 1L;
        }

        @Override
        public int storedEnergy() {
            return stored;
        }

        @Override
        public void setStoredEnergy(int amount) {
            stored = Math.max(0, Math.min(conduit, amount));
        }

        @Override
        public int bufferCapacity() {
            return conduit;
        }

        @Override
        public int transferRate() {
            return handler.part.rate();
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return handler;
        }
    }
}
