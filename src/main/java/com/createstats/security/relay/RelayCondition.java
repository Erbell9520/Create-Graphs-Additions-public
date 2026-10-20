package com.createstats.security.relay;

import com.createstats.security.SecurityKinds;

import java.util.EnumSet;
import java.util.Set;

public record RelayCondition(String id, Shape shape, Set<RelayBehavior> behaviors, String eventKind,
                             Set<String> deviceKinds, boolean needsMode, String langKey) {
    public static final String ANY_MONITOR = "*";

    public enum Shape {
        SUSTAINED,
        POINT
    }

    public RelayCondition {
        behaviors = behaviors.isEmpty() ? EnumSet.noneOf(RelayBehavior.class) : EnumSet.copyOf(behaviors);
        deviceKinds = Set.copyOf(deviceKinds);
    }

    public boolean isPoint() {
        return this.shape == Shape.POINT;
    }

    public boolean offers(RelayBehavior behavior) {
        return behavior != null && this.behaviors.contains(behavior);
    }

    public boolean narrowable() {
        return !this.deviceKinds.isEmpty();
    }

    public boolean acceptsDevice(String kind) {
        if (kind == null || this.deviceKinds.isEmpty()) {
            return false;
        }
        return this.deviceKinds.contains(kind)
                || (this.deviceKinds.contains(ANY_MONITOR) && SecurityKinds.monitors(kind));
    }
}
