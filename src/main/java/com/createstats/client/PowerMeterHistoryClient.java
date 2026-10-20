package com.createstats.client;

import com.createstats.gui.PowerMeterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

// reached via reflection so a dedicated server never loads screen classes
public final class PowerMeterHistoryClient {
    private PowerMeterHistoryClient() {
    }

    public static void receive(BlockPos meterPos, int range, boolean full, int[] samples) {
        if (Minecraft.getInstance().screen instanceof PowerMeterScreen screen) {
            screen.receiveHistory(meterPos, range, full, samples);
        }
    }
}
