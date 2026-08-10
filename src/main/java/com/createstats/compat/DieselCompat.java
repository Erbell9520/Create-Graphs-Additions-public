package com.createstats.compat;

import net.neoforged.fml.ModList;

public final class DieselCompat {
    public static final String MOD_ID = "createdieselgenerators";

    private DieselCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }
}