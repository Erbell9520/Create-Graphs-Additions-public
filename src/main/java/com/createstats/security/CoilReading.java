package com.createstats.security;

import com.createstats.stormcoil.CoilTargets;

public record CoilReading(int stage, int volleys, int volleysMax, int strikes, int strikesMax, boolean flat,
                          String blockedBy, boolean fedShort, boolean redstone, boolean engaged, long lastArcAt,
                          String lastArc, CoilTargets.Mode targets) {
    public CoilReading {
        blockedBy = blockedBy == null ? "" : blockedBy;
        lastArc = lastArc == null ? "" : lastArc;
        targets = targets == null ? CoilTargets.Mode.HOSTILE : targets;
    }

    public boolean blocked() {
        return !this.blockedBy.isEmpty();
    }

    public long sinceArc(long now) {
        if (this.lastArcAt == Long.MIN_VALUE) {
            return -1L;
        }
        return Math.max(0L, now - this.lastArcAt);
    }
}
