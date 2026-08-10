package com.createstats.power;

/** [1] */
public record FePowerSnapshot(
        boolean modeEnabled,
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

    /** Clamps every field into a sane range, so a malformed or hostile packet cannot poison a GUI. */
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

    /** Buffer fill in percent, 0..100. */
    public float fillPct() {
        return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(100.0f, energy * 100.0f / capacity));
    }

    /**
     * The tuning half of this snapshot, viewed as {@link FeConsumer.Settings}, so a client-side
     * consumer can answer {@code settings()} queries from server truth instead of local config.
     */
    public FeConsumer.Settings asSettings() {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return modeEnabled;
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
                // Explicitly overridden rather than inheriting the interface default, which would
                // read this client's own unsynced COMMON config instead of the server's.
                return rebootThresholdPct;
            }
        };
    }

    /** [2] */
    public int syncSignature() {
        return signature(modeEnabled, powered, burstStarved, fillPct(),
                measuredDrain, measuredInput, loadMultiplier, capacity, activeDrain,
                priority.ordinal(), shed, lock.ordinal());
    }

    /** [3] */
    public static int signature(boolean modeEnabled, boolean powered, boolean burstStarved,
                                float fillPct, int measuredDrain, int measuredInput,
                                float loadMultiplier, int capacity, int activeDrain,
                                int priorityOrdinal, boolean shed, int lockOrdinal) {
        int h = modeEnabled ? 1 : 0;
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
