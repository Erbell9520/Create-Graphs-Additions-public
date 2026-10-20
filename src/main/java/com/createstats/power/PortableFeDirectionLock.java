package com.createstats.power;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// one direction per train per tick or two interfaces turn the train into a wire. FE loops forever
public final class PortableFeDirectionLock {
    static final long GRACE_TICKS = 1L;

    private record Claim(PortableFeTransfer.Mode mode, long stamp) {
    }

    private final Map<Long, Claim> claims = new HashMap<>();
    private long lastSweep = Long.MIN_VALUE;

    public boolean mayMove(long key, PortableFeTransfer.Mode mode, long now) {
        final Claim claim = claims.get(key);
        return claim == null || claim.mode() == mode || !fresh(claim, now);
    }

    public boolean stamp(long key, PortableFeTransfer.Mode mode, long now) {
        sweep(now);
        if (!mayMove(key, mode, now)) {
            return false;
        }
        claims.put(key, new Claim(mode, now));
        return true;
    }

    int size() {
        return claims.size();
    }

    private static boolean fresh(Claim claim, long now) {
        return now - claim.stamp() <= GRACE_TICKS && claim.stamp() <= now;
    }

    private void sweep(long now) {
        if (now == lastSweep) {
            return;
        }
        lastSweep = now;
        for (Iterator<Claim> it = claims.values().iterator(); it.hasNext(); ) {
            if (!fresh(it.next(), now)) {
                it.remove();
            }
        }
    }
}
