package com.createstats.vccmk4.air.core;

import com.createstats.aerial.V3;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

// keel from the hull, sable's box is the plot box turned and hangs under a tilted ship. sable tax #17
public final class HullShape {
    public static final int KEEL_MAX = 256;

    static final int MAX_SPAN_XZ = 2048;
    static final int MAX_SPAN_Y = 1024;

    private final int[] box;
    private final int keelCell;
    private final int keelCols;
    private final int[] keelX;
    private final int[] keelY;
    private final int[] keelZ;
    private int[] packed = new int[4096];
    private int count;
    private final BitSet airtight = new BitSet();
    private int airtightCount;

    public HullShape(int[] box) {
        if (!fits(box)) throw new IllegalArgumentException("box " + Arrays.toString(box));
        this.box = box.clone();
        int sx = box[3] - box[0] + 1;
        int sz = box[5] - box[2] + 1;
        keelCell = keelCell(sx, sz);
        keelCols = (sx + keelCell - 1) / keelCell;
        int rows = (sz + keelCell - 1) / keelCell;
        keelX = new int[keelCols * rows];
        keelY = new int[keelCols * rows];
        keelZ = new int[keelCols * rows];
        Arrays.fill(keelY, Integer.MAX_VALUE);
    }

    public static boolean fits(int[] box) {
        if (box == null || box.length < 6) return false;
        long sx = (long) box[3] - box[0] + 1;
        long sy = (long) box[4] - box[1] + 1;
        long sz = (long) box[5] - box[2] + 1;
        return sx > 0 && sy > 0 && sz > 0 && sx <= MAX_SPAN_XZ && sy <= MAX_SPAN_Y && sz <= MAX_SPAN_XZ;
    }

    static int keelCell(int sizeX, int sizeZ) {
        int cell = Math.max(1, (int) Math.ceil(Math.sqrt((double) sizeX * sizeZ / KEEL_MAX)));
        while ((long) ((sizeX + cell - 1) / cell) * ((sizeZ + cell - 1) / cell) > KEEL_MAX) cell++;
        return cell;
    }

    public void add(int x, int y, int z, boolean envelope) {
        int dx = x - box[0];
        int dy = y - box[1];
        int dz = z - box[2];
        if (count == packed.length) packed = Arrays.copyOf(packed, packed.length * 2);
        if (envelope) {
            airtight.set(count);
            airtightCount++;
        }
        packed[count++] = (dx << 21) | (dy << 11) | dz;
        int k = (dz / keelCell) * keelCols + dx / keelCell;
        if (y < keelY[k]) {
            keelX[k] = x;
            keelY[k] = y;
            keelZ[k] = z;
        }
    }

    public int blocks() {
        return count;
    }

    public int airtightBlocks() {
        return airtightCount;
    }

    public Collection<V3> centres(double comX, double comY, double comZ) {
        return view(comX, comY, comZ, false);
    }

    public Collection<V3> envelope(double comX, double comY, double comZ) {
        return view(comX, comY, comZ, true);
    }

    public double[][] keel() {
        int n = 0;
        for (int y : keelY) if (y != Integer.MAX_VALUE) n++;
        double[][] out = new double[n][];
        int i = 0;
        for (int k = 0; k < keelY.length; k++) {
            if (keelY[k] == Integer.MAX_VALUE) continue;
            out[i++] = new double[] {keelX[k] + 0.5, keelY[k], keelZ[k] + 0.5};
        }
        return out;
    }

    @FunctionalInterface
    public interface ToWorld {
        void apply(double x, double y, double z, double[] out);
    }

    @FunctionalInterface
    public interface Ground {
        double at(double x, double z);
    }

    public record Keel(double clearance, double pointY, double lowestY) {
        public static final Keel NONE = new Keel(Double.NaN, Double.NaN, Double.NaN);
    }

    public static Keel keel(double[][] points, ToWorld pose, Ground ground) {
        if (points == null || points.length == 0) return Keel.NONE;
        double[] w = new double[3];
        double best = Double.POSITIVE_INFINITY;
        double bestY = Double.NaN;
        double lowest = Double.POSITIVE_INFINITY;
        for (double[] p : points) {
            pose.apply(p[0], p[1], p[2], w);
            if (!Double.isFinite(w[1])) continue;
            lowest = Math.min(lowest, w[1]);
            double g = ground.at(w[0], w[2]);
            if (!Double.isFinite(g)) continue;
            double c = w[1] - g;
            if (c < best) {
                best = c;
                bestY = w[1];
            }
        }
        return new Keel(Double.isFinite(best) ? best : Double.NaN, Double.isFinite(best) ? bestY : Double.NaN,
                Double.isFinite(lowest) ? lowest : Double.NaN);
    }

    private Collection<V3> view(double comX, double comY, double comZ, boolean envelopeOnly) {
        int size = envelopeOnly ? airtightCount : count;
        int[] data = packed;
        int n = count;
        BitSet env = airtight;
        int bx = box[0];
        int by = box[1];
        int bz = box[2];
        return new AbstractCollection<>() {
            @Override
            public Iterator<V3> iterator() {
                return new Iterator<>() {
                    private int i = envelopeOnly ? env.nextSetBit(0) : 0;

                    @Override
                    public boolean hasNext() {
                        return i >= 0 && i < n;
                    }

                    @Override
                    public V3 next() {
                        if (!hasNext()) throw new NoSuchElementException();
                        int p = data[i];
                        i = envelopeOnly ? env.nextSetBit(i + 1) : i + 1;
                        return new V3(bx + (p >>> 21) + 0.5 - comX, by + ((p >>> 11) & 0x3FF) + 0.5 - comY,
                                bz + (p & 0x7FF) + 0.5 - comZ);
                    }
                };
            }

            @Override
            public int size() {
                return size;
            }
        };
    }
}
