package com.createstats.power;

// the back boss used to say yes to everything while the board was shedding its own machines
public record FeExportReport(long panelPos, int allowance, int spareGeneration, int stored,
                             int capacity, int floorPct, FeReserveReport.Stance stance,
                             boolean holdCharging) {
    public static final FeExportReport NONE = new FeExportReport(Long.MIN_VALUE, 0, 0, 0, 0, 0,
            FeReserveReport.Stance.SURPLUS, false);

    public FeExportReport {
        allowance = Math.max(0, allowance);
        spareGeneration = Math.max(0, spareGeneration);
        stored = Math.max(0, stored);
        capacity = Math.max(0, capacity);
        floorPct = Math.max(0, Math.min(100, floorPct));
        stance = stance == null ? FeReserveReport.Stance.SURPLUS : stance;
    }

    public boolean isPresent() {
        return panelPos != Long.MIN_VALUE;
    }

    public int socPct() {
        return capacity <= 0 ? 0
                : (int) Math.max(0, Math.min(100, (long) stored * 100L / capacity));
    }

    public boolean isRationing() {
        return isPresent() && stance.isRationing();
    }

    public int capped(int wanted) {
        return isPresent() ? Math.min(Math.max(0, wanted), allowance) : Math.max(0, wanted);
    }
}
