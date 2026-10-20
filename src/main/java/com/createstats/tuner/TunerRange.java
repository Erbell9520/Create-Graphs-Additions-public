package com.createstats.tuner;

import java.util.function.IntUnaryOperator;

public record TunerRange(int min, int max, int step, IntUnaryOperator cost) {
    public TunerRange {
        if (step <= 0 || max < min) {
            throw new IllegalArgumentException("a range needs a positive step and max >= min");
        }
    }

    public int clamp(int value) {
        final int v = Math.max(min, Math.min(max, value));
        return min + (v - min) / step * step;
    }

    public int step(int value, int notches) {
        return clamp(clamp(value) + notches * step);
    }

    public int cycle(int value) {
        final int v = clamp(value);
        return v >= max ? min : v + step;
    }

    public int notches() {
        return (max - min) / step + 1;
    }

    public int notchOf(int value) {
        return (clamp(value) - min) / step;
    }

    public int costAt(int value) {
        return cost.applyAsInt(clamp(value));
    }
}
