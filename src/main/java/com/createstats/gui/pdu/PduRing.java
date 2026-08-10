package com.createstats.gui.pdu;

/** [1] */
public final class PduRing {

    private final float[] data;
    private int count;
    private int head;

    public PduRing(int capacity) {
        this.data = new float[Math.max(1, capacity)];
    }

    public int capacity() {
        return data.length;
    }

    public int count() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public void push(float value) {
        data[head] = value;
        head = (head + 1) % data.length;
        if (count < data.length) {
            count++;
        }
    }

    /** Sample {@code i}, from 0 for the oldest held value to {@code count - 1} for the newest. */
    public float at(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("sample " + i + " of " + count);
        }
        final int start = ((head - count) % data.length + data.length) % data.length;
        return data[(start + i) % data.length];
    }

    public float latest() {
        return count == 0 ? 0.0f : at(count - 1);
    }

    /** The largest value held, or 0 when empty. The sparkline auto ranges its y axis to this. */
    public float max() {
        float m = 0.0f;
        for (int i = 0; i < count; i++) {
            m = Math.max(m, at(i));
        }
        return m;
    }

    public void clear() {
        count = 0;
        head = 0;
    }
}
