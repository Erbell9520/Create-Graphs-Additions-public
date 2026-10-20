package com.createstats.compat;

import net.neoforged.fml.ModList;

public final class SimulatedCompat {
    public static final String SIMULATED_MOD_ID = "simulated";
    public static final String AERONAUTICS_MOD_ID = "aeronautics";
    public static final String AERONAUTICS_BUNDLED_MOD_ID = "aeronautics_bundled";

    private SimulatedCompat() {
    }

    public static boolean isAvailable() {
        final ModList modList = ModList.get();
        return modList.isLoaded(SIMULATED_MOD_ID)
                || modList.isLoaded(AERONAUTICS_MOD_ID)
                || modList.isLoaded(AERONAUTICS_BUNDLED_MOD_ID);
    }
}
