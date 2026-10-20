package com.createstats.power;

import net.minecraft.core.BlockPos;

public interface FeStudPanel {
    BlockPos panelPos();

    int studLinkCount(int stud);

    int maxLinksPerStud();

    boolean isLinkedToConnector(BlockPos connector);

    void linkStud(int stud, FeWireTerminal terminal, WireTier tier);

    int dropStudWires(int stud);

    // don't drop at both ends, that mints a second wire
    void forgetLinksTo(BlockPos connector);
}
