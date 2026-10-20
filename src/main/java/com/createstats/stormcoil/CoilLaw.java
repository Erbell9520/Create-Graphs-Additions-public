package com.createstats.stormcoil;

import com.createstats.power.WireTier;

public final class CoilLaw {
    public static final int DEFAULT_BASE = 8192;

    public static final int STRIKE_TICKS = 1200;

    public static final int STRIKES_HELD = 10;

    public static final int CAPACITOR_VOLLEYS = 8;

    public static final int ZAP_INTERVAL = 20;

    public static final int WIND_UP = 10;

    public static final int ENGAGED_TICKS = 200;

    private CoilLaw() {
    }

    public static int inRate(CoilStage stage, int base) {
        final long need = volleyRate(stage.zapCost, stage.targets());
        final long b = Math.max(1, base);
        for (WireTier tier : WireTier.values()) {
            if (b * tier.multiplier() >= need) {
                return saturate(b * tier.multiplier());
            }
        }
        return saturate(b * WireTier.HV.multiplier());
    }

    public static WireTier inWire(CoilStage stage, int base) {
        final long rate = inRate(stage, base);
        for (WireTier tier : WireTier.values()) {
            if ((long) Math.max(1, base) * tier.multiplier() >= rate) {
                return tier;
            }
        }
        return WireTier.HV;
    }

    public static com.createstats.power.ConnectorTier inConnector(CoilStage stage, int base) {
        final long rate = inRate(stage, base);
        for (com.createstats.power.ConnectorTier tier : com.createstats.power.ConnectorTier.values()) {
            if ((long) Math.max(1, base) * tier.multiplier() >= rate) {
                return tier;
            }
        }
        return com.createstats.power.ConnectorTier.INDUSTRIAL;
    }

    public static int volleyRate(int zapCost, int targets) {
        return saturate((Math.max(0L, zapCost) * Math.max(0, targets) + ZAP_INTERVAL - 1) / ZAP_INTERVAL);
    }

    public static int outRate(int base) {
        return saturate((long) Math.max(1, base) * WireTier.HV.multiplier());
    }

    public static int strikeFe(int base) {
        return saturate((long) Math.max(1, base) * STRIKE_TICKS);
    }

    public static int bankSize(int base) {
        return saturate((long) strikeFe(base) * STRIKES_HELD);
    }

    public static int arcRate(int zapCost, int inCap) {
        final long rate = (Math.max(0L, zapCost) + ZAP_INTERVAL - 1) / ZAP_INTERVAL;
        return (int) Math.max(0L, Math.min(rate, Math.max(0, inCap)));
    }

    // MIN_VALUE never counts, now - MIN_VALUE wraps and looks like "just now"
    public static boolean engaged(long now, long lastZap) {
        return lastZap != Long.MIN_VALUE && lastZap <= now && now - lastZap < ENGAGED_TICKS;
    }

    public static CoilStores.Terms terms(CoilStage stage, boolean zapsWanted, int base) {
        return new CoilStores.Terms(stage.zapCost, stage.capacitorSize(), inRate(stage, base), outRate(base),
                zapsWanted, strikeFe(base), bankSize(base), stage.targets());
    }

    private static int saturate(long v) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, v));
    }
}
