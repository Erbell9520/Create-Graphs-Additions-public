package com.createstats.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** [1] */
public final class PduLegPlan {

    private PduLegPlan() {
    }

    /**
     * One managed device, tagged with the leg it hangs off.
     *
     * @param key         stable identity, in practice the device's packed block position
     * @param leg         which output leg carries it, 0 to {@link PduStuds#COUNT} for the back output
     * @param tierOrdinal {@link FePriority} ordinal, lower is more important
     * @param demand      FE per tick it costs to run
     * @param lock        the player's override, if any
     */
    public record Device(long key, int leg, int tierOrdinal, int demand, FeLock lock) {
        public Device {
            lock = lock == null ? FeLock.NONE : lock;
        }
    }

    /**
     * One output leg as the screen sees it.
     *
     * @param energised false when the breaker is open or tripped, which takes the leg out entirely
     * @param ceiling   FE per tick this leg can actually carry, the lower of the stud's own rating and
     *                  the grade of wire strung to it
     */
    public record Leg(boolean energised, int ceiling) {
        /** A leg with nothing wrong with it, for the ordinary case and for tests. */
        public static Leg open(int ceiling) {
            return new Leg(true, ceiling);
        }
    }

    /**
     * The outcome.
     *
     * @param survivors   devices that fit their own leg, to be judged against supply next
     * @param blocked     why each screened out device did not make it, keyed by device
     * @param legDemand   total demand offered to each leg, whether or not it fitted
     * @param legAdmitted demand that fitted inside each leg's ceiling
     */
    public record Screened(List<PduAutoController.Device> survivors, Map<Long, PduReason> blocked,
                           int[] legDemand, int[] legAdmitted) {

        /** True when this leg turned something away for want of capacity, not for want of supply. */
        public boolean isAtCapacity(int leg) {
            return leg >= 0 && leg < legDemand.length && legDemand[leg] > legAdmitted[leg];
        }

        /** Total demand the board is being asked for, across every leg. */
        public int totalDemand() {
            int total = 0;
            for (int d : legDemand) {
                total += d;
            }
            return total;
        }
    }

    /**
     * Screens every device against its own leg's capacity.
     *
     * <p>Ordering inside a leg deliberately matches {@link PduAutoController}'s: locked on first, then
     * tier ascending, then cheapest, then key. Using a different order here would mean the capacity
     * pass and the supply pass disagree about which machine matters, so a device could survive one and
     * be dropped by the other on alternate ticks and pulse forever.</p>
     *
     * @param devices every managed device, in any order
     * @param legs    one entry per leg; a device naming a leg outside this array is dropped as unwired
     */
    public static Screened screen(List<Device> devices, Leg[] legs) {
        final int legCount = legs.length;
        final int[] legDemand = new int[legCount];
        final int[] legAdmitted = new int[legCount];
        final Map<Long, PduReason> blocked = new HashMap<>();
        final List<PduAutoController.Device> survivors = new ArrayList<>(devices.size());

        // Bucket by leg first, so each leg's ordering is computed over its own devices only.
        final List<List<Device>> byLeg = new ArrayList<>(legCount);
        for (int i = 0; i < legCount; i++) {
            byLeg.add(new ArrayList<>());
        }
        for (Device d : devices) {
            if (d.leg() < 0 || d.leg() >= legCount) {
                // Wired to nothing this panel owns. Not an error and not a shed: simply not ours.
                continue;
            }
            byLeg.get(d.leg()).add(d);
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
                // An open or tripped breaker is not scarcity, so nothing here competes for supply and
                // nothing here is reported as shed. The leg is simply not live.
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
                // [2]
                if (cumulative + demand <= ceiling) {
                    cumulative += demand;
                    survivors.add(new PduAutoController.Device(
                            d.key(), d.tierOrdinal(), demand, d.lock()));
                } else {
                    blocked.put(d.key(), PduReason.OVER_CAPACITY);
                }
            }
            legAdmitted[leg] = cumulative;
        }
        return new Screened(survivors, blocked, legDemand, legAdmitted);
    }

    /** [3] */
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
