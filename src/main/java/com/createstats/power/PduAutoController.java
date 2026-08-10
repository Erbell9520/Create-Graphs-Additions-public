package com.createstats.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** [1] */
public final class PduAutoController {

    /** A sentinel "long ago" that cannot overflow when subtracted from the game time. */
    public static final long NEVER = Long.MIN_VALUE / 2;

    /**
     * One managed device as the controller sees it: a stable identity, its tier, and what it costs
     * to run. Building these is the caller's job, which is also what keeps the expensive part (a
     * capability lookup per device) to exactly one call per device per control step.
     *
     * @param key         stable identity, in practice the connector's packed block position
     * @param tierOrdinal {@link FePriority} ordinal, where lower is more important
     * @param demand      FE per tick this device costs to run
     * @param lock        the player's override, if any
     */
    public record Device(long key, int tierOrdinal, int demand, FeLock lock) {

        /** A device with no player override, which is the ordinary case. */
        public Device(long key, int tierOrdinal, int demand) {
            this(key, tierOrdinal, demand, FeLock.NONE);
        }

        public Device {
            lock = lock == null ? FeLock.NONE : lock;
        }
    }

    /**
     * The supply picture for one control step.
     *
     * two budgets fix the hungriest-device-pulsing bug, that's the whole reason they're separate.
     * judging a restore against the buffer is a feedback loop: shedding the biggest load lets the
     * buffer refill, a buffer-coupled budget re-admits that load, it drains the buffer, gets shed
     * again. so a <b>running</b> device stays alive while generation plus spendable reserve can
     * still cover it ({@code runBudget}), but a <b>shed</b> device only comes back when generation
     * <i>alone</i> can sustain it ({@code admitBudget}). restoring on generation only breaks the
     * loop, since generation doesn't move when the buffer does.
     *
     * @param runBudget   FE per tick a device already running is allowed to keep drawing against,
     *                    generation plus the reserve spent as ride-through
     * @param admitBudget FE per tick a shed device must fit inside to be restored, generation alone
     * @param burstMargin extra headroom a restore must clear on top of {@code admitBudget}, so the
     *                    branch never comes back up at exactly 100 percent of what it measured
     * @param belowFloor  true when the buffer has fallen under the reserve floor, which trips the
     *                    safety layer
     * @param surplus     true when supply is spare (a bank is charging or the buffer is above the
     *                    restore mark), which is the precondition for any restore
     * @param throughputLimited true when the budget is capped by the branch wiring rather than by
     *                    generation, so a device that does not fit is a wiring problem rather than
     *                    a supply one
     */
    public record Supply(int runBudget, int admitBudget, int burstMargin, boolean belowFloor,
                         boolean surplus, boolean throughputLimited) {

        /** The common case, where the branch wiring is not the binding constraint. */
        public Supply(int runBudget, int admitBudget, int burstMargin, boolean belowFloor, boolean surplus) {
            this(runBudget, admitBudget, burstMargin, belowFloor, surplus, false);
        }

        /** A symmetric supply, for the cases where the reserve plays no part. */
        public static Supply flat(int budget, boolean surplus) {
            return new Supply(budget, budget, 0, false, surplus);
        }
    }

    /**
     * Timing tuning. Held as a record so a test can shrink the dwell to a couple of steps instead of
     * simulating sixty ticks per assertion, and so these can move to config without touching the
     * decision.
     *
     * @param dwellTicks           minimum ticks between two state flips of one device
     * @param restoreIntervalTicks minimum ticks between restoring any two devices
     */
    public record Tuning(int dwellTicks, int restoreIntervalTicks) {
        /** The shipped defaults: three seconds of dwell, one second between restores. */
        public static final Tuning DEFAULT = new Tuning(60, 20);
    }

    /**
     * The outcome of one control step.
     *
     * @param admitted       the keys allowed to draw, in admit order
     * @param admittedDemand summed demand of the admitted set, which is what the budget was spent on
     */
    public record Plan(Set<Long> admitted, int admittedDemand, Map<Long, PduReason> reasons) {
        public static final Plan EMPTY = new Plan(Set.of(), 0, Map.of());

        public boolean isRunning(long key) {
            return admitted.contains(key);
        }

        /** Why this device is in the state it is in, for the panel to explain itself. */
        public PduReason reasonFor(long key) {
            return reasons.getOrDefault(key, PduReason.RUNNING);
        }
    }

    /** True while each device is admitted. Absent means "new device", which starts admitted. */
    private final Map<Long, Boolean> running = new HashMap<>();
    /** Game time each device last changed run state, for the minimum switching time. */
    private final Map<Long, Long> lastFlip = new HashMap<>();
    /** Game time any device was last restored, for staged restoration. */
    private long lastRestore = NEVER;

    private Tuning tuning = Tuning.DEFAULT;

    public void setTuning(Tuning tuning) {
        this.tuning = tuning == null ? Tuning.DEFAULT : tuning;
    }

    public Tuning tuning() {
        return tuning;
    }

    /** Whether the last decision left this device admitted. Absent devices read as admitted. */
    public boolean isRunning(long key) {
        return running.getOrDefault(key, Boolean.TRUE);
    }

    /** Drops all switching history, so the next step decides from scratch. */
    public void reset() {
        running.clear();
        lastFlip.clear();
        lastRestore = NEVER;
    }

    /** Blames the right half of the system when a device does not fit: the wire, or the generator. */
    private static PduReason overBudgetReason(Supply supply) {
        return supply.throughputLimited() ? PduReason.OVER_CAPACITY : PduReason.OVER_BUDGET;
    }

    /**
     * Runs one control step and returns the admitted set.
     *
     * <p>Devices absent from {@code devices} are forgotten, so a branch that is rewired does not
     * carry stale switching history for blocks that are no longer on it.</p>
     *
     * @param devices the managed devices, in any order
     * @param supply  the measured supply picture for this step
     * @param now     the current game time, used for the dwell and the restore interval
     */
    public Plan decide(List<Device> devices, Supply supply, long now) {
        if (devices.isEmpty()) {
            running.clear();
            lastFlip.clear();
            return Plan.EMPTY;
        }
        final Map<Long, PduReason> reasons = new HashMap<>();

        // [2]
        final List<Device> ordered = new ArrayList<>(devices);
        ordered.sort((a, b) -> {
            if (a.tierOrdinal() != b.tierOrdinal()) {
                return Integer.compare(a.tierOrdinal(), b.tierOrdinal());
            }
            if (a.demand() != b.demand()) {
                return Integer.compare(a.demand(), b.demand());
            }
            return Long.compare(a.key(), b.key());
        });

        final Set<Long> admitted = new LinkedHashSet<>();
        final Set<Long> present = new java.util.HashSet<>();
        boolean restoredThisStep = lastRestore > NEVER && now - lastRestore < tuning.restoreIntervalTicks();
        int cumulative = 0;

        // [3]
        for (Device device : ordered) {
            if (device.lock() == FeLock.FORCE_ON) {
                present.add(device.key());
                running.put(device.key(), Boolean.TRUE);
                admitted.add(device.key());
                cumulative += device.demand();
                reasons.put(device.key(), PduReason.LOCKED_ON);
            }
        }

        for (Device device : ordered) {
            final long key = device.key();
            if (device.lock() == FeLock.FORCE_ON) {
                continue; // already admitted above
            }
            if (device.lock() == FeLock.FORCE_OFF) {
                // Never runs, and never counted: a device the player has switched off is not demand.
                present.add(key);
                running.put(key, Boolean.FALSE);
                reasons.put(key, PduReason.LOCKED_OFF);
                continue;
            }
            present.add(key);
            final int demand = device.demand();
            final boolean wasRunning = isRunning(key);
            final boolean isCritical = device.tierOrdinal() == FePriority.CRITICAL.ordinal();

            // [4]
            final boolean desired;
            PduReason reason;
            if (supply.belowFloor()) {
                // Safety layer. Only CRITICAL draws, and it takes effect this step.
                desired = isCritical;
                reason = desired ? PduReason.RUNNING : PduReason.BELOW_FLOOR;
            } else if (wasRunning) {
                // [5]
                desired = cumulative + demand <= supply.runBudget();
                reason = desired ? PduReason.RUNNING : overBudgetReason(supply);
            } else {
                // Re-admit only what generation alone sustains, so a refilled buffer cannot pull a
                // shed device back and start it pulsing. See Supply for why this is the asymmetry.
                final boolean fits =
                        cumulative + demand <= supply.admitBudget() - supply.burstMargin();
                desired = fits && supply.surplus() && !restoredThisStep;
                reason = desired ? PduReason.RUNNING
                        : !fits ? overBudgetReason(supply)
                        : !supply.surplus() ? PduReason.AWAITING_SUPPLY
                        : PduReason.STAGED;
            }

            boolean target = desired;
            // Minimum switching time. A safety trip is exempt, because waiting out a dwell while the
            // reserve drains is how you lose the CRITICAL ride-through the floor exists to protect.
            if (desired != wasRunning && !supply.belowFloor()) {
                if (now - lastFlip.getOrDefault(key, NEVER) < tuning.dwellTicks()) {
                    target = wasRunning; // awaiting hysteresis
                    reason = PduReason.AWAITING_DWELL;
                }
            }
            reasons.put(key, reason);

            if (target != wasRunning) {
                lastFlip.put(key, now);
                if (target) {
                    // Staged restoration: one device per interval, so a recovering branch does not
                    // pick up its whole shed load in a single tick and trip straight back over.
                    lastRestore = now;
                    restoredThisStep = true;
                }
            }

            running.put(key, target);
            if (target) {
                admitted.add(key);
                cumulative += demand;
            }
        }

        running.keySet().retainAll(present);
        lastFlip.keySet().retainAll(present);
        return new Plan(admitted, cumulative, reasons);
    }
}
