package com.createstats.seismic;

import java.util.Arrays;

public final class VeinOutline {
    public static final int FINE_LIMIT = 1_500;

    public static final int MEDIUM_LIMIT = 12_000;

    private static final int BIAS = 256;

    private VeinOutline() {
    }

    public static int cellFor(int count) {
        return count <= FINE_LIMIT ? 1 : count <= MEDIUM_LIMIT ? 2 : 4;
    }

    public static int[] cells(int[] blocks, int cell) {
        final IntSet seen = new IntSet(blocks.length);
        final int[] out = new int[blocks.length];
        int n = 0;
        for (final int p : blocks) {
            final int c = SurveyShells.pack(Math.floorDiv(SurveyShells.dx(p), cell),
                    Math.floorDiv(SurveyShells.dy(p), cell), Math.floorDiv(SurveyShells.dz(p), cell));
            if (seen.add(c)) {
                out[n++] = c;
            }
        }
        return Arrays.copyOf(out, n);
    }

    public static int[] edges(int[] cells) {
        final IntSet occupied = new IntSet(cells.length);
        for (final int c : cells) {
            occupied.add(c);
        }

        final LongSet candidates = new LongSet(cells.length * 6);
        for (final int c : cells) {
            final int x = SurveyShells.dx(c);
            final int y = SurveyShells.dy(c);
            final int z = SurveyShells.dz(c);
            for (int a = 0; a <= 1; a++) {
                for (int b = 0; b <= 1; b++) {
                    candidates.add(key(0, x, y + a, z + b));
                    candidates.add(key(1, x + a, y, z + b));
                    candidates.add(key(2, x + a, y + b, z));
                }
            }
        }
        final long[] creases = new long[candidates.size()];
        int n = 0;
        for (final long k : candidates.values()) {
            if (crease(occupied, k)) {
                creases[n++] = k;
            }
        }

        final long[] sorted = Arrays.copyOf(creases, n);
        for (int i = 0; i < n; i++) {
            sorted[i] = lineOrder(sorted[i]);
        }
        Arrays.sort(sorted);
        final int[] runs = new int[n * 5];
        int r = 0;
        int i = 0;
        while (i < n) {
            final long line = sorted[i] >>> 10;
            final int start = (int) (sorted[i] & 0x3FF);
            int end = start + 1;
            int j = i + 1;
            while (j < n && (sorted[j] >>> 10) == line && (int) (sorted[j] & 0x3FF) == end) {
                end++;
                j++;
            }
            final int axis = (int) (line >>> 20);
            final int p = (int) ((line >>> 10) & 0x3FF) - BIAS;
            final int q = (int) (line & 0x3FF) - BIAS;
            final int along = start - BIAS;
            runs[r++] = axis == 0 ? along : p;
            runs[r++] = axis == 1 ? along : (axis == 0 ? p : q);
            runs[r++] = axis == 2 ? along : q;
            runs[r++] = axis;
            runs[r++] = end - start;
            i = j;
        }
        return Arrays.copyOf(runs, r);
    }

    public static int runCount(int[] runs) {
        return runs.length / 5;
    }

    static boolean crease(IntSet occupied, long k) {
        final int axis = (int) (k >>> 30);
        final int x = (int) ((k >>> 20) & 0x3FF) - BIAS;
        final int y = (int) ((k >>> 10) & 0x3FF) - BIAS;
        final int z = (int) (k & 0x3FF) - BIAS;

        final boolean[] in = new boolean[4];
        int count = 0;
        for (int s = 0; s < 4; s++) {
            final int du = (s & 1) == 0 ? -1 : 0;
            final int dv = (s & 2) == 0 ? -1 : 0;
            final int cx = axis == 0 ? x : x + du;
            final int cy = axis == 0 ? y + du : (axis == 1 ? y : y + dv);
            final int cz = axis == 2 ? z : z + dv;
            in[s] = occupied.contains(SurveyShells.pack(cx, cy, cz));
            if (in[s]) {
                count++;
            }
        }
        if (count == 1 || count == 3) {
            return true;
        }

        return count == 2 && ((in[0] && in[3]) || (in[1] && in[2]));
    }

    static long key(int axis, int x, int y, int z) {
        return ((long) axis << 30) | ((long) (x + BIAS) << 20) | ((long) (y + BIAS) << 10) | (z + BIAS);
    }

    private static long lineOrder(long k) {
        final int axis = (int) (k >>> 30);
        final int x = (int) ((k >>> 20) & 0x3FF);
        final int y = (int) ((k >>> 10) & 0x3FF);
        final int z = (int) (k & 0x3FF);
        final int along = axis == 0 ? x : axis == 1 ? y : z;
        final int p = axis == 0 ? y : x;
        final int q = axis == 2 ? y : z;
        return ((long) axis << 30) | ((long) p << 20) | ((long) q << 10) | along;
    }

    static final class IntSet {
        private int[] slots;
        private int count;

        IntSet(int expected) {
            int cap = 16;
            while (cap < expected * 2) {
                cap <<= 1;
            }
            slots = new int[cap];
        }

        boolean add(int v) {
            if ((count + 1) * 2 > slots.length) {
                final int[] old = slots;
                slots = new int[old.length * 2];
                count = 0;
                for (final int o : old) {
                    if (o != 0) {
                        add(o);
                    }
                }
            }
            int s = mix(v) & (slots.length - 1);
            while (slots[s] != 0) {
                if (slots[s] == v) {
                    return false;
                }
                s = (s + 1) & (slots.length - 1);
            }
            slots[s] = v;
            count++;
            return true;
        }

        boolean contains(int v) {
            int s = mix(v) & (slots.length - 1);
            while (slots[s] != 0) {
                if (slots[s] == v) {
                    return true;
                }
                s = (s + 1) & (slots.length - 1);
            }
            return false;
        }

        private static int mix(int v) {
            final int h = v * 0x9E3779B1;
            return h ^ (h >>> 15);
        }
    }

    static final class LongSet {
        private long[] slots;
        private int count;

        LongSet(int expected) {
            int cap = 16;
            while (cap < expected * 2) {
                cap <<= 1;
            }
            slots = new long[cap];
        }

        int size() {
            return count;
        }

        void add(long v) {
            if ((count + 1) * 2 > slots.length) {
                final long[] old = slots;
                slots = new long[old.length * 2];
                count = 0;
                for (final long o : old) {
                    if (o != 0L) {
                        add(o);
                    }
                }
            }
            int s = mix(v) & (slots.length - 1);
            while (slots[s] != 0L) {
                if (slots[s] == v) {
                    return;
                }
                s = (s + 1) & (slots.length - 1);
            }
            slots[s] = v;
            count++;
        }

        long[] values() {
            final long[] out = new long[count];
            int n = 0;
            for (final long v : slots) {
                if (v != 0L) {
                    out[n++] = v;
                }
            }
            return out;
        }

        private static int mix(long v) {
            final long h = v * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 29));
        }
    }
}
