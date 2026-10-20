package com.createstats.power;

// pass-through blocks relay the forecast of whatever's behind them
public final class FeForecastRelay {
    private FeForecastRelay() {
    }

    public record Upstream(int[] at, int sensitiveNow) {
        public static final Upstream NONE = new Upstream(new int[0], 0);

        public Upstream {
            at = at == null ? new int[0] : at.clone();
            for (int i = 0; i < at.length; i++) {
                at[i] = Math.max(0, at[i]);
            }
            sensitiveNow = Math.max(0, sensitiveNow);
        }

        public boolean isPresent() {
            return at.length == PduSupplySurvey.FORECAST_OFFSETS.length;
        }

        public int now() {
            return isPresent() ? at[0] : 0;
        }

        public int at(int ticks) {
            if (!isPresent()) {
                return 0;
            }
            final int[] offsets = PduSupplySurvey.FORECAST_OFFSETS;
            if (ticks <= offsets[0]) {
                return at[0];
            }
            for (int i = 1; i < offsets.length; i++) {
                if (ticks <= offsets[i]) {
                    final int span = offsets[i] - offsets[i - 1];
                    final long into = ticks - offsets[i - 1];
                    return (int) (at[i - 1] + (at[i] - (long) at[i - 1]) * into / span);
                }
            }
            return at[at.length - 1];
        }

        public int floor() {
            if (!isPresent()) {
                return 0;
            }
            int floor = Integer.MAX_VALUE;
            for (int v : at) {
                floor = Math.min(floor, v);
            }
            return floor;
        }
    }

    public static int through(int passingNow, int sourcesNow, int sourcesThen) {
        final int passing = Math.max(0, passingNow);
        final int now = Math.max(0, sourcesNow);
        final int drop = Math.max(0, now - Math.max(0, sourcesThen));
        final int headroom = Math.max(0, now - passing);
        final int lost = Math.min(passing, Math.max(0, drop - headroom));
        return passing - lost;
    }

    public static int weatherShareThrough(int passingNow, int sourcesNow, int sensitiveNow) {
        final int passing = Math.max(0, passingNow);
        final int now = Math.max(0, sourcesNow);
        return passing - through(passing, now, now - Math.min(now, Math.max(0, sensitiveNow)));
    }

    public static int export(int exportNow, int supplyDrop) {
        final int export = Math.max(0, exportNow);
        return export - Math.min(export, Math.max(0, supplyDrop));
    }

    public static int exportWeatherShare(int exportNow, int sensitiveNow) {
        return Math.min(Math.max(0, exportNow), Math.max(0, sensitiveNow));
    }
}
