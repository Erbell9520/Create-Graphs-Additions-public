package com.createstats.power;

import com.createstats.blockentity.PowerConnectorBE;

import java.util.Locale;

public final class FeRates {
    private FeRates() {
    }

    public static int base() {
        return PowerConnectorBE.baseTransferRate();
    }

    public static int cable(CableTier tier) {
        return tier.rate(base());
    }

    public static int indoorCable() {
        return IndoorCable.rate(base());
    }

    public static int wire(WireTier tier) {
        return base() * tier.multiplier();
    }

    public static int loadStep(int fePerTick, int ratedFePerTick, int steps) {
        if (fePerTick <= 0 || ratedFePerTick <= 0 || steps <= 0) {
            return 0;
        }
        final int scaled = (int) Math.min(steps, (long) fePerTick * steps / ratedFePerTick);
        return Math.max(1, scaled);
    }

    public static int connector(ConnectorTier tier) {
        return base() * tier.multiplier();
    }

    public static int connectorThroughput(int base, ConnectorTier tier, Iterable<WireTier> spans) {
        return connectorThroughput(base, tier, spans, 0);
    }

    public static int connectorThroughput(int base, ConnectorTier tier, Iterable<WireTier> spans,
                                          int cableFeed) {
        final int ceiling = base * tier.multiplier();
        int sum = Math.max(0, cableFeed);
        boolean any = cableFeed > 0;
        for (WireTier span : spans) {
            if (span == null) {
                continue;
            }
            any = true;
            sum = plus(sum, base * span.multiplier());
        }
        return any ? Math.min(ceiling, sum) : ceiling;
    }

    public static int plus(int a, int b) {
        final long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE
                : sum < 0 ? 0 : (int) sum;
    }

    public static String perTick(int fePerTick) {
        return String.format(Locale.ROOT, "%,d FE/t", Math.max(0, fePerTick));
    }

    public static String amount(int fe) {
        return String.format(Locale.ROOT, "%,d FE", Math.max(0, fe));
    }
}
