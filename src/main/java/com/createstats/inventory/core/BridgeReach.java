package com.createstats.inventory.core;

import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;

// same reach as the antenna mixin
public final class BridgeReach {
    private BridgeReach() {
    }

    private static final AntennaTier[] LADDER = {AntennaTier.NONE, AntennaTier.SMALL, AntennaTier.ADVANCED,
            AntennaTier.TRT};

    public record Verdict(boolean reaches, boolean crossDimension, double distance, double reach,
                          double baseReach, AntennaTier needed) {
    }

    public static Verdict check(boolean sameDimension, boolean overworldNetherPair, boolean nether,
                                double distanceSq, double baseRange, AntennaTier tier) {
        final AntennaTier own = tier == null ? AntennaTier.NONE : tier;
        if (!sameDimension) {
            final boolean ok = AntennaRange.crossDimEligible(own, overworldNetherPair);
            return new Verdict(ok, true, 0.0, 0.0, baseRange, overworldNetherPair ? AntennaTier.TRT : null);
        }
        final boolean ok = AntennaRange.withinRangeSq(distanceSq, baseRange, own, nether);
        AntennaTier needed = null;
        for (AntennaTier t : LADDER) {
            if (AntennaRange.withinRangeSq(distanceSq, baseRange, t, nether)) {
                needed = t;
                break;
            }
        }
        return new Verdict(ok, false, Math.sqrt(Math.max(0.0, distanceSq)),
                AntennaRange.localRange(baseRange, own, nether), baseRange, needed);
    }

    public static double reachOf(double baseRange, AntennaTier tier, boolean nether) {
        return AntennaRange.localRange(baseRange, tier, nether);
    }
}
