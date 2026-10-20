package com.createstats.compat;

import net.neoforged.fml.ModList;

public final class CreateAdditionCompat {
    public static final String MOD_ID = "createaddition";

    private CreateAdditionCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
