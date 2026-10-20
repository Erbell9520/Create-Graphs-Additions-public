package com.createstats.power;

// the PDU walk finds nothing past a controller, so the controller reports
public record FeReserveReport(long controllerPos, int stored, int capacity, int generation,
                              int netRate, int sustainedDraw, int criticalDraw, int chargeClaim,
                              int outputCeiling, int floorPct, int ceilingPct, Stance stance) {
    public FeReserveReport(long controllerPos, int stored, int capacity, int generation,
                           int netRate, int sustainedDraw, int criticalDraw, int outputCeiling,
                           int floorPct, int ceilingPct, Stance stance) {
        this(controllerPos, stored, capacity, generation, netRate, sustainedDraw, criticalDraw, 0,
                outputCeiling, floorPct, ceilingPct, stance);
    }

    public enum Stance {
        SURPLUS("Surplus", "The reserve is deep, spend it"),

        NOMINAL("Nominal", "Drawing down toward the band"),

        CONSERVE("Conserve", "Holding the band, generation only"),

        CRITICAL("Critical", "Below the floor, critical load only");

        private final String label;
        private final String blurb;

        Stance(String label, String blurb) {
            this.label = label;
            this.blurb = blurb;
        }

        public String label() {
            return label;
        }

        public String blurb() {
            return blurb;
        }

        public boolean isRationing() {
            return this == CONSERVE || this == CRITICAL;
        }
    }

    public static final FeReserveReport NONE =
            new FeReserveReport(0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Stance.CONSERVE);

    public FeReserveReport {
        stored = Math.max(0, stored);
        capacity = Math.max(0, capacity);
        generation = Math.max(0, generation);
        sustainedDraw = Math.max(0, sustainedDraw);

        criticalDraw = Math.max(criticalDraw, sustainedDraw);

        chargeClaim = Math.max(0, Math.min(chargeClaim, generation));
        outputCeiling = Math.max(0, outputCeiling);
        floorPct = Math.max(0, Math.min(100, floorPct));
        ceilingPct = Math.max(floorPct, Math.min(100, ceilingPct));
        stance = stance == null ? Stance.CONSERVE : stance;
    }

    public FeReserveReport withChargeClaim(int claim) {
        return claim == chargeClaim ? this
                : new FeReserveReport(controllerPos, stored, capacity, generation, netRate,
                        sustainedDraw, criticalDraw, claim, outputCeiling, floorPct, ceilingPct,
                        stance);
    }

    public FeReserveReport limitedBy(Stance upstream, int allowance) {
        if (upstream == null) {
            return this;
        }
        final Stance worse = upstream.ordinal() > stance.ordinal() ? upstream : stance;
        final int held = Math.min(sustainedDraw, Math.max(0, allowance));
        return worse == stance && held == sustainedDraw ? this
                : new FeReserveReport(controllerPos, stored, capacity, generation, netRate,
                        held, criticalDraw, chargeClaim, outputCeiling, floorPct, ceilingPct, worse);
    }

    public static FeReserveReport combine(java.util.Collection<FeReserveReport> reports) {
        FeReserveReport single = null;
        int present = 0;
        for (FeReserveReport report : reports) {
            if (report != null && report.isPresent()) {
                single = report;
                present++;
            }
        }
        if (present == 0) {
            return NONE;
        }
        if (present == 1) {
            return single;
        }
        long pos = 0L;
        int stored = 0;
        int capacity = 0;
        int generation = 0;
        int netRate = 0;
        int sustained = 0;
        int critical = 0;
        int claim = 0;
        int ceiling = 0;
        int deepest = -1;
        int floor = 0;
        int top = 0;
        Stance worst = Stance.SURPLUS;
        for (FeReserveReport report : reports) {
            if (report == null || !report.isPresent()) {
                continue;
            }
            stored += report.stored();
            capacity += report.capacity();
            generation += report.generation();
            netRate += report.netRate();
            sustained += report.sustainedDraw();
            critical += report.criticalDraw();
            claim += report.chargeClaim();
            ceiling += report.outputCeiling();
            if (report.stance().ordinal() > worst.ordinal()) {
                worst = report.stance();
            }
            if (report.capacity() > deepest) {
                deepest = report.capacity();
                floor = report.floorPct();
                top = report.ceilingPct();
                pos = report.controllerPos();
            }
        }
        return new FeReserveReport(pos, stored, capacity, generation, netRate, sustained, critical,
                claim, ceiling, floor, top, worst);
    }

    public boolean isRecharging() {
        return chargeClaim > 0;
    }

    public boolean isPresent() {
        return capacity > 0;
    }

    public int socPct() {
        return capacity <= 0 ? 0 : (int) Math.max(0, Math.min(100, (long) stored * 100L / capacity));
    }

    public float fill() {
        return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, stored / (float) capacity));
    }

    public int runBudget() {
        return Math.min(outputCeiling, Math.max(0, generation - chargeClaim) + sustainedDraw);
    }

    public int criticalBudget() {
        return Math.min(outputCeiling, generation + criticalDraw);
    }
}
