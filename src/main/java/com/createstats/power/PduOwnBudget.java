package com.createstats.power;

public final class PduOwnBudget {
    private PduOwnBudget() {
    }

    public record Inputs(int gen, int planGen, int standing, int ceiling, int energy, int floorEnergy,
                         int horizon, boolean surplus, int fullRide, int reserveRide, int totalDemand) {
        public Inputs {
            horizon = Math.max(1, horizon);
        }
    }

    public record Budgets(int run, int admit, int critical, long restoreSupply, boolean throughputLimited) {
    }

    public static int rideThrough(Inputs in) {
        return Math.max(0, in.energy() - in.floorEnergy()) / in.horizon();
    }

    public static Budgets of(Inputs in) {
        final int ride = rideThrough(in);
        final int ceiling = Math.max(0, in.ceiling());
        final int standing = Math.max(0, in.standing());
        final int run = in.surplus() ? Math.max(0, ceiling - standing)
                : Math.min(ceiling, FeRates.plus(Math.max(0, in.gen() - standing),
                        FeRates.plus(ride, Math.max(0, in.reserveRide()))));
        final long admitSupply = Math.max(0, in.planGen() - standing);
        final int admit = (int) Math.min(ceiling, admitSupply);
        final int critical = in.surplus() ? ceiling
                : Math.min(ceiling, FeRates.plus(Math.max(0, in.gen()),
                        FeRates.plus(ride, Math.max(0, in.fullRide()))));
        final boolean limited = in.surplus() ? in.totalDemand() > ceiling
                : (long) ceiling < (long) Math.max(0, in.gen()) + ride + Math.max(0, in.reserveRide());
        return new Budgets(run, admit, critical, admitSupply, limited);
    }
}
