package com.createstats.security;

import net.minecraft.core.BlockPos;

public interface GovernedDevice {
    void repoll();

    // only a hint, taken only while it has no panel of its own
    void offerPanel(BlockPos panel);

    void releasePanel(BlockPos panel);
}
