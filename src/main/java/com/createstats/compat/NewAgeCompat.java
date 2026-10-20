package com.createstats.compat;

import net.neoforged.fml.ModList;

public final class NewAgeCompat {
    public static final String MOD_ID = "create_new_age";

    private NewAgeCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
