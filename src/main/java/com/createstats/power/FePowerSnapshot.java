package com.createstats.power;

// common config isn't synced, so the client trusts this snapshot and nothing else
public record FePowerSnapshot(

        boolean powerRequired,
        boolean powered,
        boolean burstStarved,
        int energy,
        int capacity,
        int idleDrain,
        int activeDrain,
        int burstCost,
        int rebootThresholdPct,
        int measuredDrain,
        int measuredInput,
        float loadMultiplier,
        FePriority priority,
        boolean shed,
        FeLock lock) {
    public FePowerSnapshot {
        capacity = Math.max(0, capacity);
        energy = Math.max(0, Math.min(energy, capacity));
        idleDrain = Math.max(0, idleDrain);
        activeDrain = Math.max(0, activeDrain);
        burstCost = Math.max(0, burstCost);
        rebootThresholdPct = Math.max(0, Math.min(90, rebootThresholdPct));
        measuredDrain = Math.max(0, measuredDrain);
        measuredInput = Math.max(0, measuredInput);
        loadMultiplier = Float.isFinite(loadMultiplier) ? Math.max(1.0f, loadMultiplier) : 1.0f;
        priority = priority == null ? FePriority.NORMAL : priority;
        lock = lock == null ? FeLock.NONE : lock;
    }

    public float fillPct() {
        return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(100.0f, energy * 100.0f / capacity));
    }

    public FeConsumer.Settings asSettings() {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return powerRequired;
            }

            @Override
            public int capacity() {
                return capacity;
            }

            @Override
            public int idleDrain() {
                return idleDrain;
            }

            @Override
            public int activeDrain() {
                return activeDrain;
            }

            @Override
            public int burstCost() {
                return burstCost;
            }

            @Override
            public int rebootThresholdPct() {
                return rebootThresholdPct;
            }
        };
    }

    public int syncSignature() {
        return signature(powerRequired, powered, burstStarved, fillPct(),
                measuredDrain, measuredInput, loadMultiplier, capacity, activeDrain,
                priority.ordinal(), shed, lock.ordinal());
    }

    public static int signature(boolean powerRequired, boolean powered, boolean burstStarved,
                                float fillPct, int measuredDrain, int measuredInput,
                                float loadMultiplier, int capacity, int activeDrain,
                                int priorityOrdinal, boolean shed, int lockOrdinal) {
        int h = powerRequired ? 1 : 0;
        h = h * 31 + (powered ? 1 : 0);
        h = h * 31 + (burstStarved ? 1 : 0);
        h = h * 31 + Math.round(fillPct / 2.0f);
        h = h * 31 + measuredDrain / 32;
        h = h * 31 + measuredInput / 32;
        h = h * 31 + Math.round(loadMultiplier * 10.0f);
        h = h * 31 + capacity;
        h = h * 31 + activeDrain;
        h = h * 31 + priorityOrdinal;
        h = h * 31 + (shed ? 1 : 0);
        h = h * 31 + lockOrdinal;
        return h;
    }
}
