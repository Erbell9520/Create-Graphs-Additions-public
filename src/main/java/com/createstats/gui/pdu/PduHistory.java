package com.createstats.gui.pdu;

/** [1] */
public final class PduHistory {

    public static final int SIZE = 300;
    /** The largest FE per tick a sample can hold; higher values clamp here. */
    public static final int MAX_FE = Short.MAX_VALUE;
    /** The largest shed count a sample can hold, since it is stored in a byte. */
    public static final int MAX_SHED = 127;

    private final short[] supply = new short[SIZE];
    private final short[] demand = new short[SIZE];
    private final byte[] shed = new byte[SIZE];
    private int head;
    private int count;

    public int count() {
        return count;
    }

    public int capacity() {
        return SIZE;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** Records one sample, clamping FE per tick to the short range and the shed count to a byte. */
    public void record(int supplyFe, int demandFe, int shedCount) {
        pushRaw(clampFe(supplyFe), clampFe(demandFe), (byte) Math.max(0, Math.min(MAX_SHED, shedCount)));
    }

    /** Pushes a pre clamped sample, for the client appending a delta it received already scaled. */
    public void pushRaw(short supplySample, short demandSample, byte shedSample) {
        supply[head] = supplySample;
        demand[head] = demandSample;
        shed[head] = shedSample;
        head = (head + 1) % SIZE;
        if (count < SIZE) {
            count++;
        }
    }

    static short clampFe(int v) {
        return (short) Math.max(0, Math.min(MAX_FE, v));
    }

    private int start() {
        return ((head - count) % SIZE + SIZE) % SIZE;
    }

    private void check(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("sample " + i + " of " + count);
        }
    }

    /** Sample {@code i}, from 0 for the oldest held to {@code count - 1} for the newest. */
    public short supplyAt(int i) {
        check(i);
        return supply[(start() + i) % SIZE];
    }

    public short demandAt(int i) {
        check(i);
        return demand[(start() + i) % SIZE];
    }

    public byte shedAt(int i) {
        check(i);
        return shed[(start() + i) % SIZE];
    }

    public short[] supplyChronological() {
        final short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = supplyAt(i);
        }
        return out;
    }

    public short[] demandChronological() {
        final short[] out = new short[count];
        for (int i = 0; i < count; i++) {
            out[i] = demandAt(i);
        }
        return out;
    }

    public byte[] shedChronological() {
        final byte[] out = new byte[count];
        for (int i = 0; i < count; i++) {
            out[i] = shedAt(i);
        }
        return out;
    }

    /** Replaces the whole buffer from a chronological snapshot, for the client receiving a full send. */
    public void replace(short[] supplyChrono, short[] demandChrono, byte[] shedChrono, int n) {
        head = 0;
        count = 0;
        final int m = Math.min(n, Math.min(supplyChrono.length, Math.min(demandChrono.length, shedChrono.length)));
        for (int i = 0; i < m; i++) {
            pushRaw(supplyChrono[i], demandChrono[i], shedChrono[i]);
        }
    }

    public void clear() {
        head = 0;
        count = 0;
    }
}
