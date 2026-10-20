package com.createstats.cinecam.client;

import net.minecraft.core.BlockPos;

public final class CineClientHooks {
    private CineClientHooks() {
    }

    public static void lookThrough(BlockPos camera) {
        CineView.enter(camera);
    }
}
