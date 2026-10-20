package com.createstats.security.relay;

import com.createstats.security.SecurityEvent;
import com.createstats.security.SecurityKinds;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RelayConditions {
    private RelayConditions() {
    }

    private static final Set<RelayBehavior> ALL = EnumSet.allOf(RelayBehavior.class);
    private static final Set<RelayBehavior> EVENTS = EnumSet.of(RelayBehavior.PULSE, RelayBehavior.HOLD);
    private static final Set<RelayBehavior> SUPERVISION = EnumSet.of(RelayBehavior.FOLLOW);

    public static final RelayCondition LOCKDOWN = new RelayCondition("create_stats:lockdown",
            RelayCondition.Shape.SUSTAINED, ALL, null, Set.of(), false, "lockdown");

    public static final RelayCondition TAMPER = new RelayCondition("create_stats:tamper_accepted",
            RelayCondition.Shape.POINT, EVENTS, SecurityEvent.TAMPER_ACCEPTED,
            Set.of(SecurityKinds.SENSOR_TAMPER, SecurityKinds.LOCKER), false, "tamper");

    public static final RelayCondition FIRE = new RelayCondition("create_stats:fire_present",
            RelayCondition.Shape.SUSTAINED, ALL, null, Set.of(SecurityKinds.SENSOR_FIRE), false, "fire");

    public static final RelayCondition WATER = new RelayCondition("create_stats:water_present",
            RelayCondition.Shape.SUSTAINED, ALL, null, Set.of(SecurityKinds.SENSOR_WATER), false, "water");

    public static final RelayCondition DENIED = new RelayCondition("create_stats:access_denied",
            RelayCondition.Shape.POINT, EVENTS, SecurityEvent.ACCESS_DENIED,
            Set.of(SecurityKinds.DOOR, SecurityKinds.LOCKER), false, "denied");

    public static final RelayCondition BREACHED = new RelayCondition("create_stats:device_breached",
            RelayCondition.Shape.SUSTAINED, ALL, null, Set.of(SecurityKinds.DOOR, SecurityKinds.SHUTTER),
            false, "breached");

    public static final RelayCondition TROUBLE = new RelayCondition("create_stats:device_trouble",
            RelayCondition.Shape.SUSTAINED, SUPERVISION, null, Set.of(RelayCondition.ANY_MONITOR), false,
            "trouble");

    public static final RelayCondition MODE = new RelayCondition("create_stats:household_mode",
            RelayCondition.Shape.SUSTAINED, ALL, null, Set.of(), true, "mode");

    public static final RelayCondition HEALTHY = new RelayCondition("create_stats:monitoring_healthy",
            RelayCondition.Shape.SUSTAINED, SUPERVISION, null, Set.of(RelayCondition.ANY_MONITOR), false,
            "healthy");

    private static final Map<String, RelayCondition> BY_ID = new LinkedHashMap<>();

    static {
        for (RelayCondition c : List.of(LOCKDOWN, TAMPER, FIRE, WATER, DENIED, BREACHED, TROUBLE, MODE,
                HEALTHY)) {
            if (BY_ID.put(c.id(), c) != null) {
                throw new IllegalStateException("two relay conditions share the id " + c.id());
            }
        }
    }

    @Nullable
    public static RelayCondition byId(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static List<RelayCondition> all() {
        return List.copyOf(BY_ID.values());
    }
}
