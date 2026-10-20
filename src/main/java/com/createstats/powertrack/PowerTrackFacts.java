package com.createstats.powertrack;

import java.util.Locale;

public final class PowerTrackFacts {
    public static final int MAX_CHARS = 18;

    private PowerTrackFacts() {
    }

    public static String line(int state, int flow) {
        return switch (state) {
            case PowerTrackService.TrainFact.CHARGING -> "charging " + perTick(flow);
            case PowerTrackService.TrainFact.GIVING -> "giving " + perTick(-flow);
            case PowerTrackService.TrainFact.LIVE_IDLE -> "live rail, idle";
            case PowerTrackService.TrainFact.IN_GAP -> "in a rail gap";
            case PowerTrackService.TrainFact.UNPOWERED -> "no live rail";
            default -> "";
        };
    }

    private static String perTick(int fe) {
        final long v = Math.max(0, fe);
        if (v < 1000) {
            return v + " FE/t";
        }
        final double k = v / 1000.0;
        return (k >= 10 || k == Math.rint(k) ? String.format(Locale.ROOT, "%.0fk", k)
                : String.format(Locale.ROOT, "%.1fk", k)) + " FE/t";
    }
}
