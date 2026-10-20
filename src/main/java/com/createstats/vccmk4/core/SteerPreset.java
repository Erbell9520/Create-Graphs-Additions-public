package com.createstats.vccmk4.core;

import java.util.Arrays;
import java.util.List;

// the two that change geometry are the only ones the solver cares about
public enum SteerPreset {
    FRONT("front"),

    TWIN_FRONT("twin_front"),

    FRONT_REAR("front_rear"),

    ALL("all"),

    CRAB("crab"),

    SKID("skid"),

    CUSTOM("custom"),

    REAR("rear"),

    NONE("none");

    public static final List<SteerPreset> DISPLAY = List.of(FRONT, REAR, TWIN_FRONT, FRONT_REAR, ALL, CRAB, SKID, NONE);

    public static final int TWIN_FRONT_FROM_AXLES = 4;

    public static final int SKID_FROM_AXLES = 3;

    private final String key;

    SteerPreset(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static SteerPreset byOrdinal(int ordinal) {
        SteerPreset[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : FRONT;
    }

    public AxleRole[] roles(int axles) {
        int n = Math.max(0, axles);
        AxleRole[] out = new AxleRole[n];
        Arrays.fill(out, AxleRole.FIXED);
        if (n == 0) return out;
        switch (this) {
            case TWIN_FRONT -> {
                out[0] = AxleRole.STEER;
                if (n >= 3) out[1] = AxleRole.STEER;
            }
            case FRONT_REAR -> {
                out[0] = AxleRole.STEER;
                out[n - 1] = AxleRole.STEER;
            }
            case ALL, CRAB -> Arrays.fill(out, AxleRole.STEER);
            case SKID, NONE -> Arrays.fill(out, AxleRole.FIXED);
            case REAR -> out[n - 1] = AxleRole.STEER;
            default -> out[0] = AxleRole.STEER;
        }
        return out;
    }

    public static SteerPreset matching(AxleRole[] roles, SteerPreset current) {
        if (roles == null) return current;

        if ((current == CRAB || current == SKID) && Arrays.equals(current.roles(roles.length), roles)) {
            return current;
        }
        for (SteerPreset p : new SteerPreset[]{FRONT, REAR, TWIN_FRONT, FRONT_REAR, ALL, NONE}) {
            if (Arrays.equals(p.roles(roles.length), roles)) return p;
        }
        return CUSTOM;
    }

    public static SteerPreset proposal(int axles, boolean articulated, boolean sidesSeparate) {
        if (articulated) return NONE;
        if (sidesSeparate && axles >= SKID_FROM_AXLES) return SKID;
        return axles >= TWIN_FRONT_FROM_AXLES ? TWIN_FRONT : FRONT;
    }
}
