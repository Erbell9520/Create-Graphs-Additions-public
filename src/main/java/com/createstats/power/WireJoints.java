package com.createstats.power;

import java.util.function.Predicate;

import net.minecraft.core.Direction;

public final class WireJoints {
    private int mask;
    private boolean sampled;

    public boolean note(Direction towards, Predicate<Direction> offersOn) {
        if (!sampled) {
            resample(offersOn);
            return true;
        }
        final int bit = 1 << towards.ordinal();
        final boolean was = (mask & bit) != 0;
        final boolean now = offersOn.test(towards);
        if (was == now) {
            return false;
        }
        mask ^= bit;
        return true;
    }

    private void resample(Predicate<Direction> offersOn) {
        int fresh = 0;
        for (Direction dir : Direction.values()) {
            if (offersOn.test(dir)) {
                fresh |= 1 << dir.ordinal();
            }
        }
        mask = fresh;
        sampled = true;
    }
}
