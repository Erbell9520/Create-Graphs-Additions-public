package com.createstats.gui.home;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WireGraph {
    public record Wire(int x, int y, int z, int flow) {
    }

    public record Segment(int ax, int az, int bx, int bz, int y, int flow, int towards) {
    }

    private final Map<Long, Wire> nodes = new HashMap<>();
    private final Map<Long, Integer> hops = new HashMap<>();

    public WireGraph(Collection<Wire> wires) {
        for (Wire w : wires) {
            nodes.put(key(w.x(), w.y(), w.z()), w);
        }

        final Set<Long> left = new HashSet<>(nodes.keySet());
        while (!left.isEmpty()) {
            long root = 0;
            int best = Integer.MIN_VALUE;
            for (long k : left) {
                final int f = nodes.get(k).flow();
                if (f > best || (f == best && k < root)) {
                    best = f;
                    root = k;
                }
            }
            final ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(root);
            hops.put(root, 0);
            left.remove(root);
            while (!q.isEmpty()) {
                final long k = q.poll();
                final Wire w = nodes.get(k);
                for (int[] d : SIX) {
                    final long n = key(w.x() + d[0], w.y() + d[1], w.z() + d[2]);
                    if (nodes.containsKey(n) && !hops.containsKey(n)) {
                        hops.put(n, hops.get(k) + 1);
                        left.remove(n);
                        q.add(n);
                    }
                }
            }
        }
    }

    private static final int[][] SIX = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int hops(int x, int y, int z) {
        final Integer h = hops.get(key(x, y, z));
        return h == null ? -1 : h;
    }

    public Wire at(int x, int y, int z) {
        return nodes.get(key(x, y, z));
    }

    public List<Wire> band(int yLow, int yHigh) {
        final List<Wire> out = new ArrayList<>();
        for (Wire w : nodes.values()) {
            if (w.y() >= yLow && w.y() < yHigh) {
                out.add(w);
            }
        }
        return out;
    }

    public List<Segment> segments(int yLow, int yHigh) {
        final List<Segment> out = new ArrayList<>();
        for (Wire a : band(yLow, yHigh)) {
            for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
                final Wire b = nodes.get(key(a.x() + d[0], a.y(), a.z() + d[1]));
                if (b == null) {
                    continue;
                }
                final int flow = a.flow() < 0 ? b.flow() : b.flow() < 0 ? a.flow() : Math.min(a.flow(), b.flow());
                final int ha = hops(a.x(), a.y(), a.z());
                final int hb = hops(b.x(), b.y(), b.z());
                out.add(new Segment(a.x(), a.z(), b.x(), b.z(), a.y(), flow, Integer.compare(hb, ha)));
            }
        }
        return out;
    }

    public boolean riser(int x, int z, int yLow, int yHigh) {
        for (int y = yLow; y < yHigh; y++) {
            if (nodes.containsKey(key(x, y, z))
                    && (nodes.containsKey(key(x, y + 1, z)) || nodes.containsKey(key(x, y - 1, z)))) {
                return true;
            }
        }
        return false;
    }

    public int degree(int x, int y, int z) {
        int n = 0;
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (nodes.containsKey(key(x + d[0], y, z + d[1]))) {
                n++;
            }
        }
        return n;
    }

    public boolean climbs(int x, int y, int z) {
        return nodes.containsKey(key(x, y + 1, z)) || nodes.containsKey(key(x, y - 1, z));
    }

    public boolean end(int x, int y, int z) {
        return nodes.containsKey(key(x, y, z)) && degree(x, y, z) <= 1 && !climbs(x, y, z);
    }

    public Set<Long> downstream(int x, int y, int z) {
        final Set<Long> out = new HashSet<>();
        final long start = key(x, y, z);
        if (!nodes.containsKey(start)) {
            return out;
        }
        final ArrayDeque<Long> q = new ArrayDeque<>();
        out.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            final long k = q.poll();
            final Wire w = nodes.get(k);
            final int h = hops.get(k);
            for (int[] d : SIX) {
                final long n = key(w.x() + d[0], w.y() + d[1], w.z() + d[2]);
                final Integer nh = hops.get(n);
                if (nh != null && nh == h + 1 && out.add(n)) {
                    q.add(n);
                }
            }
        }
        return out;
    }

    public static boolean touches(Set<Long> keys, int x, int y, int z) {
        if (keys.contains(key(x, y, z))) {
            return true;
        }
        for (int[] d : SIX) {
            if (keys.contains(key(x + d[0], y + d[1], z + d[2]))) {
                return true;
            }
        }
        return false;
    }

    public List<Wire> entries() {
        final List<Wire> out = new ArrayList<>();
        for (Wire w : nodes.values()) {
            if (hops(w.x(), w.y(), w.z()) == 0) {
                out.add(w);
            }
        }
        return out;
    }

    public List<long[]> pathToEntry(List<int[]> fixture) {
        Wire start = null;
        int best = Integer.MAX_VALUE;
        for (int[] b : fixture) {
            for (int[] d : new int[][] {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0},
                    {0, -1, 0}}) {
                final Wire w = nodes.get(key(b[0] + d[0], b[1] + d[1], b[2] + d[2]));
                if (w != null && hops(w.x(), w.y(), w.z()) < best) {
                    best = hops(w.x(), w.y(), w.z());
                    start = w;
                }
            }
        }
        final List<long[]> out = new ArrayList<>();
        if (start == null) {
            return out;
        }
        Wire at = start;
        out.add(new long[] {at.x(), at.y(), at.z()});
        int h = hops(at.x(), at.y(), at.z());
        while (h > 0) {
            Wire next = null;
            for (int[] d : SIX) {
                final Wire n = nodes.get(key(at.x() + d[0], at.y() + d[1], at.z() + d[2]));
                if (n != null && hops(n.x(), n.y(), n.z()) == h - 1) {
                    next = n;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            at = next;
            h--;
            out.add(new long[] {at.x(), at.y(), at.z()});
        }
        return out;
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
