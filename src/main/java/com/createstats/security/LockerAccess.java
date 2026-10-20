package com.createstats.security;

public final class LockerAccess {
    private LockerAccess() {
    }

    public static boolean opensWithoutCode(LockerMode mode, boolean governed, boolean hasCode,
                                           boolean readerLive, boolean codeDemanded,
                                           boolean clearancePasses, boolean holder,
                                           boolean houseSealed) {
        if (mode.personal()) {
            if (!readerLive || codeDemanded) {
                return false;
            }
            return holder;
        }
        if (!governed) {
            return !hasCode;
        }

        if (!readerLive || codeDemanded) {
            return false;
        }
        // AWAY+: the code is the only way in, owner too

        if (houseSealed && hasCode) {
            return false;
        }
        return clearancePasses;
    }

    public static boolean mayClaimCode(LockerMode mode, boolean governed, boolean hasCode,
                                       boolean readerLive) {
        return (governed || mode.personal()) && !hasCode && !readerLive;
    }
}
