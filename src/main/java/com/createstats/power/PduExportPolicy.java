package com.createstats.power;

public final class PduExportPolicy {
    private PduExportPolicy() {
    }

    public static int allowance(int energy, int capacity, int floorPct, int restorePct,
                                int spareGeneration, int legRoom) {
        if (legRoom <= 0 || energy <= 0 || capacity <= 0) {
            return 0;
        }
        final int floor = Math.round(capacity * (clampPct(floorPct) / 100.0f));
        final int spendable = energy - floor;
        if (spendable <= 0) {
            return 0;
        }
        final int restore = Math.max(floor + 1,
                Math.round(capacity * (clampPct(Math.max(restorePct, floorPct)) / 100.0f)));
        final int spare = Math.max(0, spareGeneration);
        final int allowed;
        if (energy < restore) {
            final float t = (energy - floor) / (float) (restore - floor);
            allowed = Math.round(spare * Math.min(1.0f, Math.max(0.0f, t)));
        } else {
            final int headroom = Math.max(1, capacity - restore);
            final float u = Math.min(1.0f, Math.max(0.0f, (energy - restore) / (float) headroom));
            allowed = Math.round(spare + (Math.max(spare, legRoom) - spare) * u);
        }
        return Math.max(0, Math.min(Math.min(allowed, legRoom), spendable));
    }

    public static FeReserveReport.Stance stanceOf(int energy, int capacity, int floorPct,
                                                  int restorePct, boolean sheddingOwnLoad) {
        if (capacity <= 0) {
            return FeReserveReport.Stance.CONSERVE;
        }
        final int floor = Math.round(capacity * (clampPct(floorPct) / 100.0f));
        if (energy <= floor) {
            return FeReserveReport.Stance.CRITICAL;
        }
        final int restore = Math.round(capacity * (clampPct(Math.max(restorePct, floorPct)) / 100.0f));
        if (energy < restore) {
            return FeReserveReport.Stance.CONSERVE;
        }

        return sheddingOwnLoad ? FeReserveReport.Stance.NOMINAL : FeReserveReport.Stance.SURPLUS;
    }

    public static int spareGeneration(int generation, int runningDemand, int idleDrain,
                                      int branchCharge) {
        return Math.max(0, generation - Math.max(0, runningDemand) - Math.max(0, idleDrain)
                - Math.max(0, branchCharge));
    }

    private static int clampPct(int pct) {
        return Math.max(0, Math.min(100, pct));
    }
}
