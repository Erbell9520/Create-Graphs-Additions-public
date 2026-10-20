package com.createstats.stormcoil;

import java.util.function.IntPredicate;

public final class CoilFit {
    public static final int RODS = 4;

    public record Fit(int stage, int blockedAt, int blockedCell) {
        public boolean blocked() {
            return blockedAt > 0;
        }
    }

    private CoilFit() {
    }

    public static Fit fit(int requested, IntPredicate columnFree, boolean rodsFree) {
        final int want = Math.max(1, Math.min(4, requested));
        for (int s = 2; s <= want; s++) {
            if (!columnFree.test(s - 1)) {
                return new Fit(s - 1, s, s - 1);
            }
            if (s == 4 && !rodsFree) {
                return new Fit(3, 4, RODS);
            }
        }
        return new Fit(want, 0, 0);
    }
}
