package com.createstats.compat.linktablet;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

public final class LinkTabletCompat {
    public static final String MOD_ID = "linktablet";

    private LinkTabletCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(StatsTabletProgram::onRegisterPrograms);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(StatsTabletClient::onRegisterClients);
        }
    }
}
