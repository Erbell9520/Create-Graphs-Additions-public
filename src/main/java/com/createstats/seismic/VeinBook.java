package com.createstats.seismic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class VeinBook {
    public static final double MARGIN = Math.sqrt(3.0) + 1.0e-6;

    public record Vein(int kind, int count, float nearest, float farthest, int[] blocks, double cx, double cy,
                       double cz) {
        public int echoTick() {
            return SeismicWave.echoTick(nearest);
        }
    }

    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] ALL = all();

    private final int capacity;
    private final int[][] neighbours;

    private int[] packed = new int[64];
    private int[] parent = new int[64];
    private byte[] kindOf = new byte[64];
    private int[] next = new int[64];

    private int[] size = new int[64];
    private int[] tail = new int[64];
    private float[] near = new float[64];
    private float[] far = new float[64];
    private long[] sx = new long[64];
    private long[] sy = new long[64];
    private long[] sz = new long[64];
    private boolean[] closed = new boolean[64];

    private int n;
    private boolean full;
    private final IntIndex index = new IntIndex();

    private int[] open = new int[64];
    private int openCount;

    public VeinBook(int capacity, boolean diagonal) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("a book needs room for one block");
        }
        this.capacity = capacity;
        this.neighbours = diagonal ? ALL : FACES;
    }

    public int stored() {
        return n;
    }

    public boolean full() {
        return full;
    }

    public boolean add(int p, float d, int kind) {
        if (n >= capacity) {
            full = true;
            return false;
        }
        if (n == packed.length) {
            grow(Math.min(capacity, n * 2));
        }
        final int i = n++;
        packed[i] = p;
        parent[i] = i;
        kindOf[i] = (byte) kind;
        next[i] = -1;
        size[i] = 1;
        tail[i] = i;
        near[i] = d;
        far[i] = d;
        final int x = SurveyShells.dx(p);
        final int y = SurveyShells.dy(p);
        final int z = SurveyShells.dz(p);
        sx[i] = x;
        sy[i] = y;
        sz[i] = z;
        closed[i] = false;
        index.put(p, i);
        int root = i;
        for (final int[] o : neighbours) {
            final int j = index.get(SurveyShells.pack(x + o[0], y + o[1], z + o[2]));
            if (j < 0 || kindOf[j] != kindOf[i]) {
                continue;
            }
            final int other = find(j);
            if (other == root || closed[other]) {
                continue;
            }
            root = union(root, other);
        }
        pushOpen(root);
        return true;
    }

    public List<Vein> close(double front) {
        return closeWhere(front, false);
    }

    public List<Vein> closeAll() {
        return closeWhere(Double.POSITIVE_INFINITY, true);
    }

    private List<Vein> closeWhere(double front, boolean all) {
        final List<Vein> out = new ArrayList<>();
        int keep = 0;
        for (int k = 0; k < openCount; k++) {
            final int r = open[k];
            if (parent[r] != r || closed[r]) {
                continue;
            }
            if (all || front >= far[r] + MARGIN) {
                closed[r] = true;
                out.add(vein(r));
            } else {
                open[keep++] = r;
            }
        }
        openCount = keep;
        out.sort((a, b) -> Float.compare(a.nearest(), b.nearest()));
        return out;
    }

    private Vein vein(int r) {
        final int[] blocks = new int[size[r]];
        int k = 0;
        for (int i = rootHead(r); i >= 0; i = next[i]) {
            blocks[k++] = packed[i];
        }
        final double c = size[r];
        return new Vein(kindOf[r], size[r], near[r], far[r], blocks, sx[r] / c + 0.5, sy[r] / c + 0.5,
                sz[r] / c + 0.5);
    }

    private static int rootHead(int r) {
        return r;
    }

    private int find(int i) {
        int r = i;
        while (parent[r] != r) {
            r = parent[r];
        }
        while (parent[i] != r) {
            final int up = parent[i];
            parent[i] = r;
            i = up;
        }
        return r;
    }

    private int union(int a, int b) {
        int keep = a;
        int gone = b;
        if (size[b] > size[a]) {
            keep = b;
            gone = a;
        }
        parent[gone] = keep;
        next[tail[keep]] = gone;
        tail[keep] = tail[gone];
        size[keep] += size[gone];
        near[keep] = Math.min(near[keep], near[gone]);
        far[keep] = Math.max(far[keep], far[gone]);
        sx[keep] += sx[gone];
        sy[keep] += sy[gone];
        sz[keep] += sz[gone];
        return keep;
    }

    private void pushOpen(int r) {
        if (openCount == open.length) {
            int keep = 0;
            for (int k = 0; k < openCount; k++) {
                final int o = open[k];
                if (parent[o] == o && !closed[o] && o != r) {
                    open[keep++] = o;
                }
            }
            openCount = keep;
            if (openCount == open.length) {
                open = Arrays.copyOf(open, open.length * 2);
            }
        }
        open[openCount++] = r;
    }

    private void grow(int to) {
        packed = Arrays.copyOf(packed, to);
        parent = Arrays.copyOf(parent, to);
        kindOf = Arrays.copyOf(kindOf, to);
        next = Arrays.copyOf(next, to);
        size = Arrays.copyOf(size, to);
        tail = Arrays.copyOf(tail, to);
        near = Arrays.copyOf(near, to);
        far = Arrays.copyOf(far, to);
        sx = Arrays.copyOf(sx, to);
        sy = Arrays.copyOf(sy, to);
        sz = Arrays.copyOf(sz, to);
        closed = Arrays.copyOf(closed, to);
    }

    private static int[][] all() {
        final int[][] out = new int[26][];
        int k = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        out[k++] = new int[] {x, y, z};
                    }
                }
            }
        }
        return out;
    }

    private static final class IntIndex {
        private int[] keys = new int[128];
        private int[] values = new int[128];
        private int count;

        void put(int key, int value) {
            if ((count + 1) * 2 > keys.length) {
                rehash(keys.length * 2);
            }
            int slot = slot(key, keys.length);
            while (keys[slot] != 0 && keys[slot] != key) {
                slot = (slot + 1) & (keys.length - 1);
            }
            if (keys[slot] == 0) {
                count++;
            }
            keys[slot] = key;
            values[slot] = value;
        }

        int get(int key) {
            int slot = slot(key, keys.length);
            while (keys[slot] != 0) {
                if (keys[slot] == key) {
                    return values[slot];
                }
                slot = (slot + 1) & (keys.length - 1);
            }
            return -1;
        }

        private void rehash(int to) {
            final int[] oldKeys = keys;
            final int[] oldValues = values;
            keys = new int[to];
            values = new int[to];
            count = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldKeys[i] != 0) {
                    put(oldKeys[i], oldValues[i]);
                }
            }
        }

        private static int slot(int key, int length) {
            final int h = key * 0x9E3779B1;
            return (h ^ (h >>> 15)) & (length - 1);
        }
    }
}
