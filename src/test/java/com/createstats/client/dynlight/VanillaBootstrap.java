package com.createstats.client.dynlight;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;
import java.util.Map;

final class VanillaBootstrap {
    private static boolean done;

    private VanillaBootstrap() {
    }

    static synchronized void ensure() {
        if (done) {
            return;
        }
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        done = true;
    }
}
