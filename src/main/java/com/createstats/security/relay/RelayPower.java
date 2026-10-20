package com.createstats.security.relay;

public final class RelayPower {
    private RelayPower() {
    }

    public static final int STANDBY = 1;

    public static final int COIL = 2;

    public static final int RATED = STANDBY + COIL;

    public static final int BUFFER = 200;

    public static final int INTAKE = 64;

    public record Tick(int paid, boolean logicRuns, boolean coilRuns, int energyAfter) {
    }

    public static Tick step(int energy, boolean suppressed, boolean coilWanted) {
        final int stored = Math.max(0, energy);
        if (suppressed || stored < STANDBY) {
            return new Tick(0, false, false, stored);
        }
        final int want = STANDBY + (coilWanted ? COIL : 0);
        if (stored >= want) {
            return new Tick(want, true, coilWanted, stored - want);
        }
        return new Tick(STANDBY, true, false, stored - STANDBY);
    }

    public static int rated(boolean forcedOff) {
        return forcedOff ? 0 : RATED;
    }
}
