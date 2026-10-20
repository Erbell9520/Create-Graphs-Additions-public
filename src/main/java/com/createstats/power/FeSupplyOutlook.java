package com.createstats.power;

public final class FeSupplyOutlook {
    private FeSupplyOutlook() {
    }

    public static final int STEP_TICKS = 250;

    public static final int UNKNOWN_CHANGE = -1;

    public static final int COUNTDOWN_TRUST_TICKS = 1_200;

    public static int liveCountdown(int countdown, long readAt, long now) {
        if (countdown < 0) {
            return UNKNOWN_CHANGE;
        }
        final long age = now - readAt;
        if (age < 0 || age > COUNTDOWN_TRUST_TICKS) {
            return UNKNOWN_CHANGE;
        }
        final long left = countdown - age;
        return left > 0 ? (int) Math.min(Integer.MAX_VALUE, left) : UNKNOWN_CHANGE;
    }

    public record Weather(SolarCurve.Sky now, int ticksToChange, SolarCurve.Sky then) {
        public static Weather steady(SolarCurve.Sky sky) {
            return new Weather(sky, UNKNOWN_CHANGE, sky);
        }

        public Weather {
            now = now == null ? SolarCurve.Sky.CLEAR : now;
            then = then == null ? now : then;
        }

        public SolarCurve.Sky at(int ahead) {
            return ticksToChange >= 0 && ahead >= ticksToChange ? then : now;
        }

        public boolean isCertain() {
            return ticksToChange >= 0;
        }
    }

    public record Result(int projectedFe, int neededFe, int shortfallFe, int darkTicks,
                         boolean certain) {
        public static final Result NONE = new Result(0, 0, 0, 0, false);

        public boolean isShort() {
            return shortfallFe > 0;
        }

        public float coverage() {
            return neededFe <= 0 ? 1.0f : Math.min(1.0f, projectedFe / (float) neededFe);
        }
    }

    public static Result project(FeSupplyMemory memory, long dayTime, int horizonTicks,
                                 Weather weather, int criticalFePerTick) {
        return project(memory, memory, 0, dayTime, horizonTicks, weather, criticalFePerTick);
    }

    public static Result project(FeSupplyMemory memory, FeSupplyMemory weatherShare,
                                 int unpricedFePerTick, long dayTime, int horizonTicks,
                                 Weather weather, int criticalFePerTick) {
        if (memory == null || horizonTicks <= 0 || !memory.isWarm()) {
            return Result.NONE;
        }
        final Weather sky = weather == null ? Weather.steady(SolarCurve.Sky.CLEAR) : weather;
        final int flat = Math.max(0, unpricedFePerTick);
        final int critical = Math.max(0, criticalFePerTick);
        int projected = 0;
        int dark = 0;
        long balance = 0L;
        long deepest = 0L;
        for (int ahead = 0; ahead < horizonTicks; ahead += STEP_TICKS) {
            final int span = Math.min(STEP_TICKS, horizonTicks - ahead);
            final int rate = rateAt(memory, weatherShare, flat, dayTime, ahead, sky.at(ahead));
            balance += (long) (rate - critical) * span;
            deepest = Math.min(deepest, balance);
            if (rate <= 0) {
                dark += span;
                continue;
            }
            projected = FeRates.plus(projected, (int) Math.min(Integer.MAX_VALUE, (long) rate * span));
        }
        final int needed = (int) Math.min(Integer.MAX_VALUE, (long) critical * horizonTicks);
        final int shortfall = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, -deepest));
        return new Result(projected, needed, shortfall, dark, sky.isCertain());
    }

    private static int rateAt(FeSupplyMemory memory, FeSupplyMemory weatherShare, int flat, long dayTime,
                              int ahead, SolarCurve.Sky sky) {
        final int clear = Math.max(0, memory.expectedFePerTick(dayTime + ahead));
        final int sensitive = weatherShare == null ? 0
                : Math.min(clear, Math.max(0, weatherShare.expectedFePerTick(dayTime + ahead)));
        return FeRates.plus(clear - sensitive,
                FeRates.plus((int) Math.floor(sensitive * sky.factor()), flat));
    }

    public record Series(int stepTicks, int[] rate, int[] clear, long coldMask) {
        public static final Series NONE = new Series(0, new int[0], new int[0], 0L);

        public Series {
            rate = rate == null ? new int[0] : rate.clone();
            clear = clear == null ? new int[0] : clear.clone();
        }

        public int steps() {
            return rate.length;
        }

        public boolean isCold(int step) {
            return step >= 0 && step < 64 && (coldMask >>> step & 1L) != 0;
        }
    }

    public static final int MAX_SERIES_STEPS = 64;

    public static Series series(FeSupplyMemory memory, FeSupplyMemory weatherShare, int unpricedFePerTick,
                                long dayTime, int horizonTicks, int stepTicks, Weather weather) {
        if (memory == null || horizonTicks <= 0 || stepTicks <= 0 || !memory.isWarm()) {
            return Series.NONE;
        }
        final Weather sky = weather == null ? Weather.steady(SolarCurve.Sky.CLEAR) : weather;
        final int flat = Math.max(0, unpricedFePerTick);
        final int steps = Math.min(MAX_SERIES_STEPS, (horizonTicks + stepTicks - 1) / stepTicks);
        final int[] rate = new int[steps];
        final int[] clear = new int[steps];
        long cold = 0L;
        for (int i = 0; i < steps; i++) {
            final int ahead = i * stepTicks;
            rate[i] = rateAt(memory, weatherShare, flat, dayTime, ahead, sky.at(ahead));
            clear[i] = rateAt(memory, weatherShare, flat, dayTime, ahead, SolarCurve.Sky.CLEAR);
            if (memory.expectedFePerTick(dayTime + ahead) == FeSupplyMemory.UNKNOWN) {
                cold |= 1L << i;
            }
        }
        return new Series(stepTicks, rate, clear, cold);
    }

    public static int[] splitHold(int shortfallFe, int boardCriticalFePerTick, int[] peerCriticalFePerTick) {
        final int peers = peerCriticalFePerTick == null ? 0 : peerCriticalFePerTick.length;
        final int[] out = new int[peers + 1];
        final int shortfall = Math.max(0, shortfallFe);
        final int board = Math.max(0, boardCriticalFePerTick);
        if (shortfall <= 0 || board <= 0) {
            out[peers] = shortfall;
            return out;
        }
        int critLeft = board;
        long given = 0L;
        for (int i = 0; i < peers; i++) {
            final int crit = Math.min(critLeft, Math.max(0, peerCriticalFePerTick[i]));
            critLeft -= crit;
            out[i] = (int) ((long) shortfall * crit / board);
            given += out[i];
        }
        out[peers] = (int) Math.max(0L, shortfall - given);
        return out;
    }

    public static int holdPctFor(Result outlook, int reserveCapacity) {
        if (outlook == null || !outlook.isShort() || reserveCapacity <= 0) {
            return 0;
        }
        final long pct = (long) outlook.shortfallFe() * 100L / reserveCapacity;
        return (int) Math.max(0, Math.min(MAX_HOLD_PCT, pct));
    }

    public static final int MAX_HOLD_PCT = 85;
}
