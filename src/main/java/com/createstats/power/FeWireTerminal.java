package com.createstats.power;

import net.minecraft.core.BlockPos;

import java.util.List;

public interface FeWireTerminal {
    BlockPos terminalPos();

    List<BlockPos> links();

    WireTier tierOf(BlockPos link);

    void addLink(BlockPos far, WireTier tier);

    void removeDeadLink(BlockPos far);

    void dropWiresAndUnlinkAll();

    default int maxLinks() {
        return ConnectorTier.POWER.maxLinks();
    }

    default boolean feedsInParallel() {
        return false;
    }

    @org.jetbrains.annotations.Nullable
    default BlockPos homeLink() {
        return null;
    }

    @org.jetbrains.annotations.Nullable
    default net.minecraft.network.chat.Component refuseLink(
            @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level, BlockPos far) {
        return null;
    }
}
