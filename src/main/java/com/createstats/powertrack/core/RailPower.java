package com.createstats.powertrack.core;

import com.createstats.power.CableTier;
import com.createstats.power.IndoorCable;

public final class RailPower {
    public static final int MAX_COLLECTORS_PER_CAR = 2;

    private RailPower() {
    }

    public static int sectionCeiling(int base) {
        return CableTier.TRAY.rate(base);
    }

    public static int collectorRate(int base) {
        return IndoorCable.rate(base);
    }

    public static int carRate(int base, int shoesInContact) {
        final int shoes = Math.max(0, Math.min(MAX_COLLECTORS_PER_CAR, shoesInContact));
        return shoes * collectorRate(base);
    }
}
