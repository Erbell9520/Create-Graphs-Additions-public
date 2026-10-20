package com.createstats.compat;

import net.neoforged.fml.ModList;

public final class RefinedStorageCompat {
    public static final String MOD_ID = "refinedstorage";

    private RefinedStorageCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
