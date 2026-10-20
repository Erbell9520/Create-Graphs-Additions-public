package com.createstats.security.relay;

import com.createstats.security.Truth;

public record RelayState(long lastEventSequence, long pulseUntil, boolean pending, Truth baseline,
                         long lastTriggerAt) {
    public static final long NEVER = Long.MIN_VALUE;

    public RelayState {
        lastEventSequence = Math.max(0L, lastEventSequence);
        baseline = baseline == null ? Truth.UNKNOWN : baseline;
    }

    public static RelayState seed(long eventSequence) {
        return new RelayState(eventSequence, NEVER, false, Truth.UNKNOWN, NEVER);
    }

    public RelayState withPending(boolean held) {
        return new RelayState(this.lastEventSequence, this.pulseUntil, held, this.baseline,
                this.lastTriggerAt);
    }

    public RelayState forgettingBaseline() {
        return new RelayState(this.lastEventSequence, this.pulseUntil, this.pending, Truth.UNKNOWN,
                this.lastTriggerAt);
    }

    public long ticksSinceTrigger(long now) {
        return this.lastTriggerAt == NEVER ? -1L : Math.max(0L, now - this.lastTriggerAt);
    }
}
