package com.createstats.client.hud;

public final class RollingBuffer {
    private final float[] data;
    private int head;
    private int size;
    private float min = Float.POSITIVE_INFINITY;
    private float max = Float.NEGATIVE_INFINITY;
    private boolean dirty;

    public RollingBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity > 0");
        this.data = new float[capacity];
    }

    public int capacity() { return this.data.length; }
    public int size() { return this.size; }
    public boolean isEmpty() { return this.size == 0; }

    public void push(float v) {
        if (this.size < this.data.length) {
            this.data[(this.head + this.size) % this.data.length] = v;
            this.size++;
        } else {
            this.data[this.head] = v;
            this.head = (this.head + 1) % this.data.length;
        }
        this.dirty = true;
    }

    public float sample(int index) {
        if (index < 0 || index >= this.size) throw new IndexOutOfBoundsException(Integer.toString(index));
        return this.data[(this.head + index) % this.data.length];
    }

    public float latest() {
        if (this.size == 0) return 0f;
        return this.data[(this.head + this.size - 1) % this.data.length];
    }

    public float min() {
        recompute();
        return this.size == 0 ? 0f : this.min;
    }

    public float max() {
        recompute();
        return this.size == 0 ? 0f : this.max;
    }

    public void clear() {
        this.head = 0;
        this.size = 0;
        this.min = Float.POSITIVE_INFINITY;
        this.max = Float.NEGATIVE_INFINITY;
        this.dirty = false;
    }

    private void recompute() {
        if (!this.dirty) return;
        float mn = Float.POSITIVE_INFINITY;
        float mx = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < this.size; i++) {
            final float v = this.data[(this.head + i) % this.data.length];
            if (v < mn) mn = v;
            if (v > mx) mx = v;
        }
        this.min = mn;
        this.max = mx;
        this.dirty = false;
    }
}
