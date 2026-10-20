package com.createstats.security;

import java.util.Set;

public final class SecurityKinds {
    private SecurityKinds() {
    }

    public static final String DOOR = "door";
    public static final String LOCKER = "locker";
    public static final String SHUTTER = "shutter";
    public static final String REPELLER = "repeller";
    public static final String RELAY = "relay";

    public static final String COIL = "coil";
    public static final String SENSOR_MOTION = "sensor:motion";
    public static final String SENSOR_TAMPER = "sensor:tamper";
    public static final String SENSOR_FIRE = "sensor:fire";
    public static final String SENSOR_WATER = "sensor:water";

    public static final Set<String> ALL = Set.of(DOOR, LOCKER, SHUTTER, REPELLER, RELAY, COIL,
            SENSOR_MOTION, SENSOR_TAMPER, SENSOR_FIRE, SENSOR_WATER);

    public static String sensor(SensorKind kind) {
        return "sensor:" + kind.key();
    }

    public static boolean isSensor(String kind) {
        return kind != null && kind.startsWith("sensor:");
    }

    public static boolean breachable(String kind) {
        return DOOR.equals(kind) || SHUTTER.equals(kind);
    }

    public static boolean monitors(String kind) {
        return kind != null && !RELAY.equals(kind) && ALL.contains(kind);
    }
}
