package com.createstats.client;

import net.minecraft.client.gui.screens.Screen;
import com.createstats.gui.DashboardScreen;
import net.minecraft.client.Minecraft;
import com.createstats.data.TimeSeriesStore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientCogHandler
{
    private ClientCogHandler() {
    }

    public static void openDashboard(TimeSeriesStore store) {
        if (store != null && store.getSampleCount() > 0) {
            Minecraft.getInstance().setScreen((Screen)new DashboardScreen(store));
        }
    }
}
