package com.createstats.client;

import com.createstats.gui.PowerDistributionUnitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class PduHistoryClient {
    private PduHistoryClient() {
    }

    public static void receive(BlockPos pduPos, int range, boolean full,
                               short[] supply, short[] demand, byte[] shed) {
        if (Minecraft.getInstance().screen instanceof PowerDistributionUnitScreen screen) {
            screen.receiveHistory(pduPos, range, full, supply, demand, shed);
        }
    }
}
