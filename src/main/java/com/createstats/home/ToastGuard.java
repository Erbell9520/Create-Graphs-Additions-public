package com.createstats.home;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ToastGuard {
    public static final int GAP_TICKS = 1200;

    public static final int ALLOWED = 3;

    public static final int WINDOW_TICKS = 12000;

    public enum Verdict {
        QUIET,

        TOAST,

        LAST
    }

    private final Map<Long, ArrayDeque<Long>> sent = new HashMap<>();

    private final Map<Long, Long> muted = new HashMap<>();

    public Verdict ask(long key, long now) {
        final Long quietSince = muted.get(key);
        if (quietSince != null) {
            if (now - quietSince < WINDOW_TICKS) {
                muted.put(key, now);
                return Verdict.QUIET;
            }
            muted.remove(key);
            sent.remove(key);
        }
        final ArrayDeque<Long> times = sent.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_TICKS) {
            times.pollFirst();
        }
        if (!times.isEmpty() && now - times.peekLast() < GAP_TICKS) {
            return Verdict.QUIET;
        }
        times.addLast(now);
        if (times.size() >= ALLOWED) {
            muted.put(key, now);
            return Verdict.LAST;
        }
        return Verdict.TOAST;
    }

    public void retain(Set<Long> keys) {
        sent.keySet().retainAll(keys);
        muted.keySet().retainAll(keys);
    }
}
