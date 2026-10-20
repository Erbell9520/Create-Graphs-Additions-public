package com.createstats.client.ponder;

@FunctionalInterface
public interface Ease {
    double at(double t);

    Ease LINEAR = t -> t;

    Ease SMOOTH = t -> t * t * (3.0 - 2.0 * t);

    Ease QUINTIC = t -> t * t * t * (t * (6.0 * t - 15.0) + 10.0);

    Ease OUT_CUBIC = t -> 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t);

    Ease IN_CUBIC = t -> t * t * t;

    static Ease launch(double k) {
        double norm = k + Math.exp(-k) - 1.0;
        return t -> (k * t + Math.exp(-k * t) - 1.0) / norm;
    }

    default double clamped(double t) {
        return at(Math.max(0.0, Math.min(1.0, t)));
    }
}
