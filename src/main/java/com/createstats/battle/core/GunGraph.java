package com.createstats.battle.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class GunGraph {
    public static final int YAW = 0;
    public static final int PITCH = 1;

    public static final int WALK_LIMIT = 4096;

    public static final long NONE = Long.MIN_VALUE;

    private final Long2IntOpenHashMap byKey = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap yawTerminal = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap pitchTerminal = new Long2IntOpenHashMap();
    private final List<IntArrayList> adj = new ArrayList<>();
    private final IntArrayList flags = new IntArrayList();
    private final List<long[]> terminalOf = new ArrayList<>();
    private final List<Governor> governors = new ArrayList<>();

    private static final int ENGINE = 1;
    private static final int GOVERNOR = 2;
    private static final int TERMINAL = 4;

    private record Governor(long key, int node, int sideA, int sideB, int input) {
    }

    public GunGraph() {
        byKey.defaultReturnValue(-1);
        yawTerminal.defaultReturnValue(-1);
        pitchTerminal.defaultReturnValue(-1);
    }

    public int node(long key, boolean engine) {
        int i = byKey.get(key);
        if (i < 0) {
            i = add(0);
            byKey.put(key, i);
            terminalOf.add(null);
        }
        if (engine) flags.set(i, flags.getInt(i) | ENGINE);
        return i;
    }

    public int terminal(long mountKey, int axis) {
        Long2IntOpenHashMap map = axis == YAW ? yawTerminal : pitchTerminal;
        int i = map.get(mountKey);
        if (i < 0) {
            i = add(TERMINAL);
            map.put(mountKey, i);
            terminalOf.add(new long[]{mountKey, axis});
        }
        return i;
    }

    public void edge(int a, int b) {
        if (a < 0 || b < 0 || a == b) return;
        if (!adj.get(a).contains(b)) adj.get(a).add(b);
        if (!adj.get(b).contains(a)) adj.get(b).add(a);
    }

    public void governor(long key, int sideA, int sideB, int input) {
        int n = node(key, false);
        flags.set(n, flags.getInt(n) | GOVERNOR);
        governors.add(new Governor(key, n, sideA, sideB, input));
    }

    public int size() {
        return adj.size();
    }

    private int add(int flag) {
        adj.add(new IntArrayList(4));
        flags.add(flag);
        return adj.size() - 1;
    }

    private record Walk(List<long[]> reached, boolean engine) {
    }

    private Walk walk(int governorNode, int start) {
        List<long[]> reached = new ArrayList<>();
        if (start < 0) return new Walk(reached, false);
        boolean engine = false;
        boolean[] seen = new boolean[adj.size()];
        seen[governorNode] = true;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        int visited = 0;
        while (!queue.isEmpty() && visited++ < WALK_LIMIT) {
            int cur = queue.poll();
            int f = flags.getInt(cur);
            if ((f & TERMINAL) != 0) {
                reached.add(terminalOf.get(cur));
                continue;
            }
            if ((f & ENGINE) != 0) engine = true;
            if ((f & GOVERNOR) != 0) continue;
            IntArrayList next = adj.get(cur);
            for (int i = 0; i < next.size(); i++) {
                int nb = next.getInt(i);
                if (!seen[nb]) {
                    seen[nb] = true;
                    queue.add(nb);
                }
            }
        }
        return new Walk(reached, engine);
    }

    public record Drive(long mount, long yaw, long pitch, boolean yawConflict, boolean pitchConflict, boolean shared) {
        public boolean hasYaw() {
            return yaw != NONE;
        }

        public boolean hasPitch() {
            return pitch != NONE;
        }

        public boolean fault() {
            return yawConflict || pitchConflict || shared;
        }
    }

    public List<Drive> solve(long[] mounts) {
        List<List<long[]>> reachOf = new ArrayList<>(governors.size());
        for (Governor gov : governors) {
            if (gov.input() == 0) {
                reachOf.add(walk(gov.node(), gov.sideB()).reached());
            } else if (gov.input() == 1) {
                reachOf.add(walk(gov.node(), gov.sideA()).reached());
            } else {
                Walk a = walk(gov.node(), gov.sideA());
                Walk b = walk(gov.node(), gov.sideB());
                if (a.engine() != b.engine()) {
                    reachOf.add(a.engine() ? b.reached() : a.reached());
                } else {
                    boolean aMount = !a.reached().isEmpty();
                    boolean bMount = !b.reached().isEmpty();
                    reachOf.add(bMount && !aMount ? b.reached() : a.reached());
                }
            }
        }
        List<Drive> out = new ArrayList<>(mounts.length);
        for (long m : mounts) {
            long yaw = NONE;
            long pitch = NONE;
            boolean yawConflict = false;
            boolean pitchConflict = false;
            boolean shared = false;
            for (int g = 0; g < governors.size(); g++) {
                boolean y = false;
                boolean p = false;
                for (long[] r : reachOf.get(g)) {
                    if (r[0] != m) continue;
                    if (r[1] == YAW) y = true;
                    else p = true;
                }
                long key = governors.get(g).key();
                if (y && p) shared = true;
                if (y) {
                    if (yaw == NONE) yaw = key;
                    else if (yaw != key) {
                        yawConflict = true;
                        yaw = Math.min(yaw, key);
                    }
                }
                if (p) {
                    if (pitch == NONE) pitch = key;
                    else if (pitch != key) {
                        pitchConflict = true;
                        pitch = Math.min(pitch, key);
                    }
                }
            }
            out.add(new Drive(m, yaw, pitch, yawConflict, pitchConflict, shared));
        }
        return out;
    }
}
