package com.createstats.power;

import net.minecraft.core.BlockPos;

public interface FeBoardPeer {
    int declaredCriticalDemandFePerTick();

    void acceptExportReport(FeExportReport report);

    FeReserveReport boardReserveReport();

    void notePushFrom(BlockPos feeder, int fe);

    void acceptHoldTarget(int holdFe, long untilTick);

    default void acceptHoldTarget(long fromPos, int holdFe, long untilTick) {
        acceptHoldTarget(holdFe, untilTick);
    }
}
