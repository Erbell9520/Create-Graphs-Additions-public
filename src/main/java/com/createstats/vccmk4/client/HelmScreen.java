package com.createstats.vccmk4.client;

import net.minecraft.core.BlockPos;

public interface HelmScreen {
    BlockPos pos();

    boolean passesKeys();
}
