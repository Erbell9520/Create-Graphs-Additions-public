package com.createstats;

public final class DebugLogging {
    private DebugLogging() {
    }

    public static boolean fccVerboseEnabled() {
        return ModConfiguration.COMMON.fccVerboseLogging.get();
    }

    public static boolean rpmGovernorVerboseEnabled() {
        return ModConfiguration.COMMON.rpmGovernorVerboseLogging.get();
    }

    public static boolean antennaDebugEnabled() {
        return ModConfiguration.COMMON.antennaDebugLogging.get();
    }

    public static void fccInfo(String message, Object... args) {
        if (fccVerboseEnabled()) {
            CreateStats.LOGGER.info(message, args);
        }
    }

    public static void fccDebug(String message, Object... args) {
        if (fccVerboseEnabled()) {
            CreateStats.LOGGER.debug(message, args);
        }
    }

    public static void rpmGovernorInfo(String message, Object... args) {
        if (rpmGovernorVerboseEnabled()) {
            CreateStats.LOGGER.info(message, args);
        }
    }

    public static void antennaInfo(String message, Object... args) {
        if (antennaDebugEnabled()) {
            CreateStats.LOGGER.info(message, args);
        }
    }
}