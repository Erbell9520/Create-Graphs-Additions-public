package com.createstats.blockentity;

public final class FlywheelBankPlan {
    private FlywheelBankPlan() {
    }

    public static float[] share(float demand, float[] weight, float[] cap) {
        int n = cap.length;
        float[] alloc = new float[n];
        boolean[] settled = new boolean[n];
        int free = 0;
        for (int i = 0; i < n; i++) {
            settled[i] = cap[i] <= 0f;
            if (!settled[i]) {
                free++;
            }
        }
        if (free == 0) {
            return alloc;
        }

        float remaining = Math.max(0f, demand);

        for (int pass = 0; pass < n && remaining > 1e-4f && free > 0; pass++) {
            float totalWeight = 0f;
            for (int i = 0; i < n; i++) {
                if (!settled[i]) {
                    totalWeight += Math.max(0f, weight[i]);
                }
            }
            boolean overflowed = false;
            float handedOut = 0f;
            int freeNow = free;
            for (int i = 0; i < n; i++) {
                if (settled[i]) {
                    continue;
                }
                float want = totalWeight > 0f
                    ? remaining * (Math.max(0f, weight[i]) / totalWeight)
                    : remaining / freeNow;
                float headroom = cap[i] - alloc[i];
                if (want >= headroom) {
                    alloc[i] = cap[i];
                    handedOut += headroom;
                    settled[i] = true;
                    free--;
                    overflowed = true;
                } else {
                    alloc[i] += want;
                    handedOut += want;
                }
            }
            remaining -= handedOut;
            if (!overflowed) {
                break;
            }
        }
        return alloc;
    }

    public static float drainPerSecond(float delivered, float efficiency, float friction) {
        return Math.max(0f, delivered) / efficiency + friction;
    }

    public static float sustainSeconds(float usable, float delivered, float efficiency, float friction) {
        if (usable <= 0f) {
            return 0f;
        }
        float drain = drainPerSecond(delivered, efficiency, friction);
        return drain > 0f ? usable / drain : 0f;
    }

    public static boolean assistLatch(boolean engaged, float stress, float capacityWithoutBanks,
                                      float engageFrac, float releaseFrac) {
        if (engaged) {
            return !(stress < capacityWithoutBanks * releaseFrac);
        }
        return stress > capacityWithoutBanks * engageFrac;
    }

    public static boolean chargeLatch(boolean engaged, float load, float gridCapacity,
                                      float engageFrac, float releaseFrac) {
        if (gridCapacity <= 0f) {
            return false;
        }
        if (engaged) {
            return load <= gridCapacity * releaseFrac;
        }
        return load <= gridCapacity * engageFrac;
    }

    public static float chargeShare(float surplus, float myRoom, float totalRoom) {
        if (surplus <= 0f || myRoom <= 0f) {
            return 0f;
        }
        return totalRoom > 0f ? surplus * (myRoom / totalRoom) : surplus;
    }

    public static float heldSpeed(float target, float rated, float factor, boolean foreign, float liveSpeed,
                                  float eps) {
        float want = Math.max(0f, Math.min(target, rated));
        if (foreign && liveSpeed > eps) {
            return Math.min(want, liveSpeed);
        }
        return want * Math.max(0f, Math.min(1f, factor));
    }

    public static float fitFactor(float capacity, float loadAtFull, float loading) {
        if (loadAtFull <= 0f) {
            return 1f;
        }
        if (capacity <= 0f) {
            return 0f;
        }
        return Math.min(1f, capacity * loading / loadAtFull);
    }

    public static float startFactor(float capacity, float loadAtFull, float loading, float dropAbove) {
        if (loadAtFull <= capacity * dropAbove) {
            return 1f;
        }
        return fitFactor(capacity, loadAtFull, loading);
    }

    // derate instead of trip. create has no partial service, one SU over and everything stops
    public static float steerFactor(float factor, float capacity, float stressNow, boolean overstressed,
                                    int stableTicks, float loading, float dropAbove, float raiseBelow,
                                    int raiseAfter) {
        if (factor <= 0f || capacity <= 0f) {
            return factor;
        }
        float loadAtFull = Math.max(0f, stressNow) / factor;
        if (overstressed || stressNow > capacity * dropAbove) {
            return Math.min(factor, fitFactor(capacity, loadAtFull, loading));
        }
        if (factor < 1f && stableTicks >= raiseAfter && stressNow < capacity * raiseBelow) {
            return Math.max(factor, fitFactor(capacity, loadAtFull, loading));
        }
        return factor;
    }
}
