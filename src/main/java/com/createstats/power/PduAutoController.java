package com.createstats.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PduAutoController {
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final int DEFAULT_FORGET_TICKS = 100;

    public record Device(long key, int tierOrdinal, int demand, FeLock lock, int legTierOrdinal) {
        public Device(long key, int tierOrdinal, int demand) {
            this(key, tierOrdinal, demand, FeLock.NONE);
        }

        public Device(long key, int tierOrdinal, int demand, FeLock lock) {
            this(key, tierOrdinal, demand, lock, FePriority.DEFAULT.ordinal());
        }

        public Device {
            lock = lock == null ? FeLock.NONE : lock;
        }
    }

    // two budgets. judging restores against the buffer made the hungriest device pulse on and off forever
    public record Supply(int runBudget, int admitBudget, int burstMargin, boolean belowFloor,
                         boolean surplus, boolean throughputLimited, int criticalBudget,
                         int reserveDepth, boolean reserveLow, boolean supplyShock,
                         int forecastDrop) {
        public Supply {
            criticalBudget = Math.max(criticalBudget, runBudget);
            reserveDepth = Math.max(0, reserveDepth);
            forecastDrop = Math.max(0, forecastDrop);
        }

        public Supply(int runBudget, int admitBudget, int burstMargin, boolean belowFloor,
                      boolean surplus, boolean throughputLimited, int criticalBudget,
                      int reserveDepth, boolean reserveLow, boolean supplyShock) {
            this(runBudget, admitBudget, burstMargin, belowFloor, surplus, throughputLimited,
                    criticalBudget, reserveDepth, reserveLow, supplyShock, 0);
        }

        public Supply(int runBudget, int admitBudget, int burstMargin, boolean belowFloor, boolean surplus) {
            this(runBudget, admitBudget, burstMargin, belowFloor, surplus, false);
        }

        public Supply(int runBudget, int admitBudget, int burstMargin, boolean belowFloor,
                      boolean surplus, boolean throughputLimited) {
            this(runBudget, admitBudget, burstMargin, belowFloor, surplus, throughputLimited,
                    runBudget, 0, false, false);
        }

        public static Supply flat(int budget, boolean surplus) {
            return new Supply(budget, budget, 0, false, surplus);
        }

        public int budgetFor(int tierOrdinal) {
            return tierOrdinal == FePriority.CRITICAL.ordinal() ? criticalBudget : runBudget;
        }
    }

    public record Tuning(int dwellTicks, int restoreIntervalTicks, int forgetTicks) {
        public static final Tuning DEFAULT = new Tuning(60, 20);

        public Tuning(int dwellTicks, int restoreIntervalTicks) {
            this(dwellTicks, restoreIntervalTicks, DEFAULT_FORGET_TICKS);
        }

        public Tuning {
            forgetTicks = Math.max(0, forgetTicks);
        }
    }

    public record Plan(Set<Long> admitted, int admittedDemand, Map<Long, PduReason> reasons) {
        public static final Plan EMPTY = new Plan(Set.of(), 0, Map.of());

        public boolean isRunning(long key) {
            return admitted.contains(key);
        }

        public PduReason reasonFor(long key) {
            return reasons.getOrDefault(key, PduReason.RUNNING);
        }
    }

    private final Map<Long, Boolean> running = new HashMap<>();

    private final Map<Long, Long> lastFlip = new HashMap<>();

    private final Map<Long, Long> lastSeen = new HashMap<>();

    private long lastRestore = NEVER;

    private Tuning tuning = Tuning.DEFAULT;

    public void setTuning(Tuning tuning) {
        this.tuning = tuning == null ? Tuning.DEFAULT : tuning;
    }

    public Tuning tuning() {
        return tuning;
    }

    public boolean isRunning(long key) {
        final Boolean known = running.get(key);
        return known != null ? known : running.isEmpty();
    }

    public void reset() {
        running.clear();
        lastFlip.clear();
        lastSeen.clear();
        lastRestore = NEVER;
    }

    private boolean assumedRunning(Device device, boolean fresh) {
        final Boolean known = running.get(device.key());
        return known != null ? known : fresh || device.tierOrdinal() == FePriority.CRITICAL.ordinal();
    }

    private void forgetAbsent(Set<Long> present, long now) {
        for (long key : present) {
            lastSeen.put(key, now);
        }
        final long memory = tuning.forgetTicks();
        running.keySet().removeIf(key -> {
            if (present.contains(key)) {
                return false;
            }
            final Long seen = lastSeen.get(key);
            return seen == null || now < seen || now - seen > memory;
        });
        lastFlip.keySet().retainAll(running.keySet());
        lastSeen.keySet().retainAll(running.keySet());
    }

    private static PduReason overBudgetReason(Supply supply) {
        return supply.throughputLimited() ? PduReason.OVER_CAPACITY : PduReason.OVER_BUDGET;
    }

    public static int restoreMargin(int margin, int admitBudget, long supply) {
        final long m = Math.max(0, margin);
        final long uncovered = (long) Math.max(0, admitBudget) + m - Math.max(0L, supply);
        return (int) Math.max(0L, Math.min(m, uncovered));
    }

    public Plan decide(List<Device> devices, Supply supply, long now) {
        if (devices.isEmpty()) {
            forgetAbsent(Set.of(), now);
            return Plan.EMPTY;
        }

        final boolean fresh = running.isEmpty();
        final Map<Long, PduReason> reasons = new HashMap<>();

        final List<Device> ordered = new ArrayList<>(devices);
        ordered.sort((a, b) -> {
            if (a.tierOrdinal() != b.tierOrdinal()) {
                return Integer.compare(a.tierOrdinal(), b.tierOrdinal());
            }

            if (a.legTierOrdinal() != b.legTierOrdinal()) {
                return Integer.compare(a.legTierOrdinal(), b.legTierOrdinal());
            }
            if (a.demand() != b.demand()) {
                return Integer.compare(a.demand(), b.demand());
            }
            return Long.compare(a.key(), b.key());
        });

        final List<Device> walk = new ArrayList<>(ordered.size());
        for (int from = 0; from < ordered.size(); ) {
            final Device head = ordered.get(from);
            int to = from;
            while (to < ordered.size() && ordered.get(to).tierOrdinal() == head.tierOrdinal()
                    && ordered.get(to).legTierOrdinal() == head.legTierOrdinal()) {
                to++;
            }
            for (int k = from; k < to; k++) {
                if (assumedRunning(ordered.get(k), fresh)) {
                    walk.add(ordered.get(k));
                }
            }
            for (int k = from; k < to; k++) {
                if (!assumedRunning(ordered.get(k), fresh)) {
                    walk.add(ordered.get(k));
                }
            }
            from = to;
        }

        final Set<Long> admitted = new LinkedHashSet<>();
        final Set<Long> present = new java.util.HashSet<>();

        boolean restoredThisStep = !supply.supplyShock()
                && lastRestore > NEVER && now - lastRestore < tuning.restoreIntervalTicks();
        int cumulative = 0;

        for (Device device : ordered) {
            if (device.lock() == FeLock.FORCE_ON) {
                present.add(device.key());
                running.put(device.key(), Boolean.TRUE);
                admitted.add(device.key());
                cumulative += device.demand();
                reasons.put(device.key(), PduReason.LOCKED_ON);
            }
        }

        for (Device device : walk) {
            final long key = device.key();
            if (device.lock() == FeLock.FORCE_ON) {
                continue;
            }
            if (device.lock() == FeLock.FORCE_OFF) {
                present.add(key);
                running.put(key, Boolean.FALSE);
                reasons.put(key, PduReason.LOCKED_OFF);
                continue;
            }
            present.add(key);
            final int demand = device.demand();
            final boolean isCritical = device.tierOrdinal() == FePriority.CRITICAL.ordinal();
            final boolean wasRunning = assumedRunning(device, fresh);

            final boolean desired;
            PduReason reason;
            if (supply.belowFloor()) {
                desired = isCritical;
                reason = desired ? PduReason.RUNNING : PduReason.BELOW_FLOOR;
            } else if (wasRunning) {
                desired = cumulative + demand <= supply.budgetFor(device.tierOrdinal());

                reason = desired ? PduReason.RUNNING
                        : supply.reserveLow() && !isCritical ? PduReason.CORE_RESERVE
                        : overBudgetReason(supply);
            } else {
                final boolean fits =
                        cumulative + demand <= supply.admitBudget() - supply.burstMargin();

                final boolean coreHeld = supply.reserveLow() && !isCritical;

                final boolean gated = !supply.surplus() && !isCritical;
                desired = fits && !gated && !restoredThisStep && !coreHeld;

                final boolean forecastHeld = !fits && supply.forecastDrop() > 0
                        && !supply.throughputLimited()
                        && cumulative + demand <= supply.admitBudget() + supply.forecastDrop()
                                - supply.burstMargin();

                final boolean headroomHeld = !fits && !forecastHeld && supply.burstMargin() > 0
                        && cumulative + demand <= supply.admitBudget();
                reason = desired ? PduReason.RUNNING
                        : coreHeld ? PduReason.CORE_RESERVE
                        : forecastHeld ? PduReason.SUPPLY_FALLING
                        : headroomHeld ? PduReason.HEADROOM
                        : !fits ? overBudgetReason(supply)
                        : gated ? PduReason.AWAITING_SUPPLY
                        : PduReason.STAGED;
            }

            boolean target = desired;

            if (desired != wasRunning && !supply.belowFloor() && !supply.supplyShock()) {
                if (now - lastFlip.getOrDefault(key, NEVER) < tuning.dwellTicks()) {
                    target = wasRunning;
                    reason = PduReason.AWAITING_DWELL;
                }
            }
            reasons.put(key, reason);

            if (target != wasRunning) {
                lastFlip.put(key, now);
                if (target) {
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

        forgetAbsent(present, now);
        return new Plan(admitted, cumulative, reasons);
    }
}
