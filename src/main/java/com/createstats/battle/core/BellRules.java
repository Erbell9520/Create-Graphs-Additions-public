package com.createstats.battle.core;

import java.util.ArrayDeque;

public final class BellRules {
    public static final long QUIET_TICKS = 200L;
    public static final long MIN_GAP_TICKS = 40L;
    private static final long[] CALL_TO_STATIONS = {0L, 8L, 16L};

    private final ArrayDeque<Long> pending = new ArrayDeque<>();
    private long lastRing = Long.MIN_VALUE / 2;

    public void hit(long now) {
        if (!pending.isEmpty()) return;
        if (now - lastRing >= QUIET_TICKS) {
            for (long d : CALL_TO_STATIONS) pending.add(now + d);
        } else if (now - lastRing >= MIN_GAP_TICKS) {
            pending.add(now);
        }
    }

    public boolean ringDue(long now) {
        Long next = pending.peek();
        if (next == null || next > now) return false;
        pending.poll();
        lastRing = now;
        return true;
    }

    public void cancel() {
        pending.clear();
    }
}
