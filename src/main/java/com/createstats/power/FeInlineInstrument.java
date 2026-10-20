package com.createstats.power;

import net.minecraft.core.Direction;

// not a grid member, the flood stops at the terminal. that's the whole mechanism
public interface FeInlineInstrument {
    int NO_CEILING = Integer.MAX_VALUE;

    Direction inputFace();

    Direction outputFace();

    default boolean capsThroughput() {
        return false;
    }

    default int throughputCeiling() {
        return NO_CEILING;
    }

    default int managedThroughputCeiling() {
        return NO_CEILING;
    }

    void recordManagedTransfer(int fe, boolean clipped);
}
