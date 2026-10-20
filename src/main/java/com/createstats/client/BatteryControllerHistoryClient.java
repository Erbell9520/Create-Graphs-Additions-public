package com.createstats.client;

import com.createstats.gui.BatteryControllerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class BatteryControllerHistoryClient {
    private BatteryControllerHistoryClient() {
    }

    public static void receive(BlockPos controllerPos, int range, boolean full,
                               byte[] soc, int[] in, int[] out) {
        if (Minecraft.getInstance().screen instanceof BatteryControllerScreen screen) {
            screen.receiveHistory(controllerPos, range, full, soc, in, out);
        }
    }
}
