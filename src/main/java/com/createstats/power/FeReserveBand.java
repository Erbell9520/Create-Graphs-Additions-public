package com.createstats.power;

// an attractor, not a floor
public final class FeReserveBand {
    private FeReserveBand() {
    }

    public static final int DEFAULT_FLOOR_PCT = 10;

    public static final int DEFAULT_CEILING_PCT = 30;

    public static final int DEFAULT_HEALTHY_PCT = 60;

    public static final int DEFAULT_RIDE_TICKS = 1_200;

    public static final int EMERGENCY_HORIZON_TICKS = DEFAULT_RIDE_TICKS * 10;

    public static final int STANCE_MARGIN_PCT = 3;

    public static final int TICKS_PER_MINUTE = 1_200;

    public static final int DEFAULT_FLOOR_MINUTES = 10;

    public static final int DEFAULT_CEILING_MINUTES = 30;

    public static final int DEFAULT_HEALTHY_MINUTES = 60;

    public static final int WANT_HOLD_TICKS = 24_000;

    public record Minutes(int floor, int ceiling, int healthy) {
        public static final Minutes DEFAULT =
                new Minutes(DEFAULT_FLOOR_MINUTES, DEFAULT_CEILING_MINUTES, DEFAULT_HEALTHY_MINUTES);

        public Minutes {
            floor = Math.max(0, floor);
            ceiling = Math.max(floor, ceiling);
            healthy = Math.max(ceiling, healthy);
        }
    }

    public static Band sized(Band caps, Minutes minutes, int capacity, int criticalWant, int fullRate,
                             int rideTicks) {
        final Band cap = caps == null ? Band.DEFAULT : caps;
        if (capacity <= 0) {
            return cap;
        }
        final Minutes m = minutes == null ? Minutes.DEFAULT : minutes;
        final long want = Math.max(0, criticalWant);
        final int floor = Math.min(cap.floorPct(), pctUp(want * m.floor() * TICKS_PER_MINUTE, capacity));
        final int ceiling = Math.min(cap.ceilingPct(), pctUp(want * m.ceiling() * TICKS_PER_MINUTE, capacity));
        final long deep = Math.max(want * m.healthy() * TICKS_PER_MINUTE,
                energyAt(capacity, floor) + (long) Math.max(0, fullRate) * Math.max(1, rideTicks));
        final int healthy = Math.min(cap.healthyPct(), pctUp(deep, capacity));
        return new Band(floor, ceiling, healthy);
    }

    public static final class WantPeak {
        private int want;
        private long since;
        private boolean started;

        public int update(int criticalWant, long now) {
            final int fresh = Math.max(0, criticalWant);
            if (!started || fresh >= want || now - since >= WANT_HOLD_TICKS) {
                want = fresh;
                since = now;
                started = true;
            }
            return want;
        }

        public int want() {
            return want;
        }

        public long since() {
            return since;
        }

        public void restore(int savedWant, long savedSince) {
            if (savedWant > 0) {
                want = savedWant;
                since = savedSince;
                started = true;
            }
        }
    }

    public record Band(int floorPct, int ceilingPct, int healthyPct) {
        public static final Band DEFAULT =
                new Band(DEFAULT_FLOOR_PCT, DEFAULT_CEILING_PCT, DEFAULT_HEALTHY_PCT);

        public Band {
            floorPct = Math.max(0, Math.min(95, floorPct));
            ceilingPct = Math.max(floorPct, Math.min(98, ceilingPct));
            healthyPct = Math.max(ceilingPct, Math.min(100, healthyPct));
        }
    }

    public record Params(int stored, int capacity, int generation, int netRate, int dischargeRate,
                         int outputCeiling, Band band, int rideTicks,
                         FeReserveReport.Stance previousStance, int criticalWant) {
        public Params(int stored, int capacity, int generation, int netRate, int dischargeRate,
                      int outputCeiling, Band band) {
            this(stored, capacity, generation, netRate, dischargeRate, outputCeiling, band,
                    DEFAULT_RIDE_TICKS, null);
        }

        public Params(int stored, int capacity, int generation, int netRate, int dischargeRate,
                      int outputCeiling, Band band, int rideTicks, FeReserveReport.Stance previousStance) {
            this(stored, capacity, generation, netRate, dischargeRate, outputCeiling, band, rideTicks,
                    previousStance, WANT_UNSTATED);
        }

        public Params {
            stored = Math.max(0, stored);
            capacity = Math.max(0, capacity);
            generation = Math.max(0, generation);
            dischargeRate = Math.max(0, dischargeRate);
            outputCeiling = Math.max(0, outputCeiling);
            band = band == null ? Band.DEFAULT : band;
            rideTicks = Math.max(1, rideTicks);
            criticalWant = Math.max(WANT_UNSTATED, criticalWant);
        }
    }

    public static final int WANT_UNSTATED = -1;

    static int underFloorCritical(int stored, int criticalWant, int dischargeRate, int rideTicks) {
        if (criticalWant == WANT_UNSTATED) {
            return Math.min(dischargeRate, stored / EMERGENCY_HORIZON_TICKS);
        }
        return Math.min(dischargeRate, Math.min(criticalWant, stored / Math.max(1, rideTicks)));
    }

    public static FeReserveReport assess(long controllerPos, Params p) {
        final Band band = p.band();
        if (p.capacity() <= 0) {
            return new FeReserveReport(controllerPos, 0, 0, p.generation(), 0, 0, 0,
                    p.outputCeiling(), band.floorPct(), band.ceilingPct(),
                    FeReserveReport.Stance.CONSERVE);
        }

        final int soc = (int) Math.max(0, Math.min(100, (long) p.stored() * 100L / p.capacity()));
        final int floorEnergy = (int) ((long) p.capacity() * band.floorPct() / 100L);
        final FeReserveReport.Stance stance = stanceFor(soc, band, p.previousStance());

        final int sustained;
        final int critical;
        if (soc >= band.healthyPct()) {
            final int atHealthy = Math.min(p.dischargeRate(), (int) ((long)
                    (energyAt(p.capacity(), band.healthyPct()) - floorEnergy) / p.rideTicks()));
            final int span = Math.max(1, 100 - band.healthyPct());
            final float t = Math.min(1.0f, (soc - band.healthyPct()) / (float) span);
            sustained = Math.round(atHealthy + (p.dischargeRate() - atHealthy) * t);
            critical = sustained;
        } else if (p.stored() > floorEnergy) {
            sustained = Math.min(p.dischargeRate(), (int) ((long) (p.stored() - floorEnergy) / p.rideTicks()));

            critical = Math.max(sustained,
                    Math.min(p.dischargeRate(), (int) ((long) p.stored() / p.rideTicks())));
        } else {
            sustained = 0;
            critical = underFloorCritical(p.stored(), p.criticalWant(), p.dischargeRate(), p.rideTicks());
        }

        return new FeReserveReport(controllerPos, p.stored(), p.capacity(), p.generation(),
                p.netRate(), sustained, critical, p.outputCeiling(),
                band.floorPct(), band.ceilingPct(), stance);
    }

    private static FeReserveReport.Stance stanceFor(int soc, Band band,
                                                    FeReserveReport.Stance previous) {
        final int margin = previous == null ? 0 : STANCE_MARGIN_PCT;
        final int healthy = atLeast(previous, FeReserveReport.Stance.SURPLUS)
                ? band.healthyPct() : band.healthyPct() + margin;
        final int ceiling = atLeast(previous, FeReserveReport.Stance.NOMINAL)
                ? band.ceilingPct() : band.ceilingPct() + margin;
        final int floor = atLeast(previous, FeReserveReport.Stance.CONSERVE)
                ? band.floorPct() : band.floorPct() + margin;
        if (soc >= healthy) {
            return FeReserveReport.Stance.SURPLUS;
        }
        if (soc >= ceiling) {
            return FeReserveReport.Stance.NOMINAL;
        }
        if (soc >= floor) {
            return FeReserveReport.Stance.CONSERVE;
        }
        return FeReserveReport.Stance.CRITICAL;
    }

    private static boolean atLeast(FeReserveReport.Stance previous, FeReserveReport.Stance target) {
        return previous != null && previous.ordinal() <= target.ordinal();
    }

    private static int energyAt(int capacity, int pct) {
        return (int) ((long) capacity * pct / 100L);
    }

    private static int pctUp(long energy, int capacity) {
        if (energy <= 0 || capacity <= 0) {
            return 0;
        }

        final long held = Math.min(energy, capacity);
        return (int) Math.min(100L, (held * 100L + capacity - 1) / capacity);
    }
}
