package com.createstats.security;

import com.createstats.power.SecurityDevice;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

// choices outrank faults. a coil you switched off isn't news
public enum CoilStatus {
    UNLOADED,

    STOOD_DOWN,

    HELD,

    BLOCKED,

    FED_SHORT,

    FLAT,

    NO_TARGETS,

    FIGHTING,

    ON_GUARD;

    private static final CoilStatus[] ORDER = values();

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isFault() {
        return this == BLOCKED || this == FED_SHORT || this == FLAT;
    }

    public boolean onGuard() {
        return this == ON_GUARD || this == FIGHTING;
    }

    public boolean standby() {
        return this == STOOD_DOWN || this == HELD || this == NO_TARGETS;
    }

    public SecurityDevice.DeviceState deviceState() {
        return switch (this) {
            case ON_GUARD, FIGHTING -> SecurityDevice.DeviceState.ACTIVE;
            case FLAT -> SecurityDevice.DeviceState.UNPOWERED;
            case BLOCKED, FED_SHORT -> SecurityDevice.DeviceState.DAMAGED;
            case UNLOADED -> SecurityDevice.DeviceState.OFFLINE;
            default -> SecurityDevice.DeviceState.IDLE;
        };
    }

    public static CoilStatus of(boolean loaded, boolean on, boolean held, boolean blocked, boolean fedShort,
                                boolean flat, boolean armedNow, boolean engaged) {
        if (!loaded) {
            return UNLOADED;
        }
        if (!on) {
            return STOOD_DOWN;
        }
        if (held) {
            return HELD;
        }
        if (blocked) {
            return BLOCKED;
        }
        if (fedShort) {
            return FED_SHORT;
        }
        if (flat) {
            return FLAT;
        }
        if (!armedNow) {
            return NO_TARGETS;
        }
        return engaged ? FIGHTING : ON_GUARD;
    }

    public static CoilStatus of(boolean on, boolean hearsRedstone, @Nullable CoilReading r, boolean armedNow) {
        if (r == null) {
            return UNLOADED;
        }
        return of(true, on, hearsRedstone && r.redstone(), r.blocked(), r.fedShort(), r.flat(), armedNow,
                r.engaged());
    }

    public static CoilStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : UNLOADED;
    }

    public enum Lamp {
        NONE,

        GREEN,

        AMBER,

        RED_FLASHING
    }

    public static Lamp lamp(boolean governed, boolean lockdown, CoilStatus s) {
        if (!governed) {
            return Lamp.NONE;
        }
        if (lockdown) {
            return Lamp.RED_FLASHING;
        }
        return s != null && s.onGuard() ? Lamp.GREEN : Lamp.AMBER;
    }

    public static boolean flashOn(double t) {
        return Math.floorMod((long) Math.floor(t), 10L) < 6;
    }
}
