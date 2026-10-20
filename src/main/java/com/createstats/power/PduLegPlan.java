package com.createstats.power;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// capacity per leg first, then generation. two different questions
public final class PduLegPlan {
    private static final int[] NO_LEGS = new int[0];

    private PduLegPlan() {
    }

    public record Device(long key, int leg, int tierOrdinal, int demand, FeLock lock, int[] alsoLegs, boolean station) {
        public Device {
            lock = lock == null ? FeLock.NONE : lock;
            alsoLegs = alsoLegs == null ? NO_LEGS : alsoLegs;
        }

        public Device(long key, int leg, int tierOrdinal, int demand, FeLock lock) {
            this(key, leg, tierOrdinal, demand, lock, NO_LEGS, false);
        }

        public Device(long key, int leg, int tierOrdinal, int demand, FeLock lock, int[] alsoLegs) {
            this(key, leg, tierOrdinal, demand, lock, alsoLegs, alsoLegs != null && alsoLegs.length > 0);
        }

        public static Device station(long key, int leg, int tierOrdinal, int demand, FeLock lock, int[] alsoLegs) {
            return new Device(key, leg, tierOrdinal, demand, lock, alsoLegs, true);
        }
    }

    public record Leg(boolean energised, int ceiling, int tierOrdinal) {
        public Leg(boolean energised, int ceiling) {
            this(energised, ceiling, FePriority.DEFAULT.ordinal());
        }

        public static Leg open(int ceiling) {
            return new Leg(true, ceiling);
        }
    }

    public record StationFit(int demand, int carried, int fed, int live) {
        public boolean wireShort() {
            return fed < demand;
        }
    }

    public record Screened(List<PduAutoController.Device> survivors, Map<Long, PduReason> blocked,
                           int[] legDemand, int[] legAdmitted, Map<Long, int[]> shares, Map<Long, StationFit> stations) {
        public boolean isAtCapacity(int leg) {
            return leg >= 0 && leg < legDemand.length && legDemand[leg] > legAdmitted[leg];
        }

        public int totalDemand() {
            int total = 0;
            for (int d : legDemand) {
                total = FeRates.plus(total, d);
            }
            for (StationFit fit : stations.values()) {
                total = FeRates.plus(total, Math.max(0, fit.demand() - fit.fed()));
            }
            return total;
        }
    }

    public static Screened screen(List<Device> devices, Leg[] legs) {
        final int legCount = legs.length;
        final int[] legDemand = new int[legCount];
        final int[] legAdmitted = new int[legCount];
        final Map<Long, PduReason> blocked = new HashMap<>();
        final Map<Long, int[]> shares = new HashMap<>(2);
        final Map<Long, StationFit> stations = new HashMap<>(2);
        final List<PduAutoController.Device> survivors = new ArrayList<>(devices.size());

        final List<List<Device>> byLeg = new ArrayList<>(legCount);
        for (int i = 0; i < legCount; i++) {
            byLeg.add(new ArrayList<>());
        }
        final List<Device> parallel = new ArrayList<>(1);
        for (Device d : devices) {
            if (d.leg() < 0 || d.leg() >= legCount) {
                continue;
            }
            if (d.station()) {
                parallel.add(d);
            } else {
                byLeg.get(d.leg()).add(d);
            }
        }

        for (int leg = 0; leg < legCount; leg++) {
            final List<Device> here = byLeg.get(leg);
            if (here.isEmpty()) {
                continue;
            }
            for (Device d : here) {
                legDemand[leg] += Math.max(0, d.demand());
            }
            if (!legs[leg].energised()) {
                for (Device d : here) {
                    blocked.put(d.key(), PduReason.BREAKER_OPEN);
                }
                continue;
            }

            here.sort(PduLegPlan::byImportance);
            int cumulative = 0;
            final int ceiling = Math.max(0, legs[leg].ceiling());
            for (Device d : here) {
                final int demand = Math.max(0, d.demand());

                if (cumulative + demand <= ceiling) {
                    cumulative += demand;
                    survivors.add(new PduAutoController.Device(
                            d.key(), d.tierOrdinal(), demand, d.lock(), legs[leg].tierOrdinal()));
                } else {
                    blocked.put(d.key(), PduReason.OVER_CAPACITY);
                }
            }
            legAdmitted[leg] += cumulative;
        }

        final int[] spare = new int[legCount];
        for (int i = 0; i < legCount; i++) {
            spare[i] = legs[i].energised() ? Math.max(0, Math.max(0, legs[i].ceiling()) - legAdmitted[i]) : 0;
        }
        parallel.sort(PduLegPlan::byImportance);
        for (Device d : parallel) {
            final int demand = Math.max(0, d.demand());
            final int[] feeds = feedsOf(d, legs);
            long carried = 0;
            for (int leg : feeds) {
                carried += spare[leg];
            }
            final int[] share = split(demand, feeds, spare);
            int fed = 0;
            for (int leg : feeds) {
                fed += share[leg];
            }
            stations.put(d.key(), new StationFit(demand, (int) Math.min(Integer.MAX_VALUE, carried), fed, feeds.length));
            if (fed <= 0 && demand > 0) {
                blocked.put(d.key(), feeds.length == 0 ? PduReason.BREAKER_OPEN : PduReason.OVER_CAPACITY);
                continue;
            }
            for (int leg : feeds) {
                spare[leg] -= share[leg];
                legDemand[leg] += share[leg];
                legAdmitted[leg] += share[leg];
            }
            shares.put(d.key(), share);
            survivors.add(new PduAutoController.Device(
                    d.key(), d.tierOrdinal(), fed, d.lock(), legs[d.leg()].tierOrdinal()));
        }
        return new Screened(survivors, blocked, legDemand, legAdmitted, shares, stations);
    }

    public static int[] split(int demand, int[] feeds, int[] spare) {
        final int[] share = new int[spare.length];
        long carried = 0;
        for (int leg : feeds) {
            carried += Math.max(0, spare[leg]);
        }
        final long want = Math.min(Math.max(0, demand), carried);
        if (want <= 0) {
            return share;
        }
        long given = 0;
        for (int leg : feeds) {
            final int part = (int) (want * Math.max(0, spare[leg]) / carried);
            share[leg] = part;
            given += part;
        }

        for (int leg : feeds) {
            if (given >= want) {
                break;
            }
            final int more = (int) Math.min(want - given, Math.max(0, spare[leg]) - share[leg]);
            if (more > 0) {
                share[leg] += more;
                given += more;
            }
        }
        return share;
    }

    private static int[] feedsOf(Device d, Leg[] legs) {
        final int[] out = new int[1 + d.alsoLegs().length];
        int n = 0;
        if (legs[d.leg()].energised()) {
            out[n++] = d.leg();
        }
        for (int leg : d.alsoLegs()) {
            if (leg < 0 || leg >= legs.length || leg == d.leg() || !legs[leg].energised()) {
                continue;
            }
            boolean seen = false;
            for (int i = 0; i < n; i++) {
                seen |= out[i] == leg;
            }
            if (!seen) {
                out[n++] = leg;
            }
        }
        return Arrays.copyOf(out, n);
    }

    private static int byImportance(Device a, Device b) {
        final boolean aOn = a.lock() == FeLock.FORCE_ON;
        final boolean bOn = b.lock() == FeLock.FORCE_ON;
        if (aOn != bOn) {
            return aOn ? -1 : 1;
        }
        if (a.tierOrdinal() != b.tierOrdinal()) {
            return Integer.compare(a.tierOrdinal(), b.tierOrdinal());
        }
        if (a.demand() != b.demand()) {
            return Integer.compare(a.demand(), b.demand());
        }
        return Long.compare(a.key(), b.key());
    }
}
