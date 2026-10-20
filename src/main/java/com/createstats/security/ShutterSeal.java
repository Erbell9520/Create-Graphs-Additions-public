package com.createstats.security;

public final class ShutterSeal {
    private ShutterSeal() {
    }

    public static boolean wantsSealed(boolean released, boolean houseSealed, boolean lockdown,
                                      boolean commanded, boolean duskNow) {
        if (released) {
            return false;
        }
        if (houseSealed) {
            return true;
        }
        if (lockdown) {
            return true;
        }
        return commanded || duskNow;
    }
}
