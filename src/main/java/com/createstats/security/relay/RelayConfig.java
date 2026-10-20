package com.createstats.security.relay;

import com.createstats.security.SecurityEndpoint;
import org.jetbrains.annotations.Nullable;

public record RelayConfig(String conditionId, RelayBehavior behavior, int pulseTicks, String mode,
                          @Nullable SecurityEndpoint scope) {
    public static final int MIN_PULSE = 1;

    public static final int MAX_PULSE = 200;

    public static final int DEFAULT_PULSE = 10;

    public static final RelayConfig DEFAULT = new RelayConfig(RelayConditions.LOCKDOWN.id(),
            RelayBehavior.FOLLOW, DEFAULT_PULSE, "away", null);

    public RelayConfig {
        conditionId = conditionId == null ? "" : conditionId;
        mode = mode == null ? "" : mode;
    }

    @Nullable
    public RelayCondition condition() {
        return RelayConditions.byId(this.conditionId);
    }

    public static boolean validPulse(int ticks) {
        return ticks >= MIN_PULSE && ticks <= MAX_PULSE;
    }

    public RelayConfig withCondition(String id) {
        return new RelayConfig(id, this.behavior, this.pulseTicks, this.mode, this.scope);
    }

    public RelayConfig withBehavior(RelayBehavior b) {
        return new RelayConfig(this.conditionId, b, this.pulseTicks, this.mode, this.scope);
    }

    public RelayConfig withPulse(int ticks) {
        return new RelayConfig(this.conditionId, this.behavior, ticks, this.mode, this.scope);
    }

    public RelayConfig withMode(String key) {
        return new RelayConfig(this.conditionId, this.behavior, this.pulseTicks, key, this.scope);
    }

    public RelayConfig withScope(@Nullable SecurityEndpoint device) {
        return new RelayConfig(this.conditionId, this.behavior, this.pulseTicks, this.mode, device);
    }
}
