package com.createstats.compat;

import net.neoforged.fml.ModList;

public final class ElectroEnergeticsCompat {
    public static final String MOD_ID = "electroenergetics";

    private ElectroEnergeticsCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}
