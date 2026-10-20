package com.createstats.battle.core;

public enum GunState {
    DOWN(HeaderStatus.Lamp.RED),
    NOT_ASSEMBLED(HeaderStatus.Lamp.RED),
    HOLD(HeaderStatus.Lamp.GREY),
    NO_POWER(HeaderStatus.Lamp.GREY),
    HAND_AIMED(HeaderStatus.Lamp.RED),
    NO_YAW_DRIVE(HeaderStatus.Lamp.RED),
    NO_PITCH_DRIVE(HeaderStatus.Lamp.RED),
    NO_ROTATION(HeaderStatus.Lamp.RED),
    HELD_ELSEWHERE(HeaderStatus.Lamp.RED),
    OVERSTRESSED(HeaderStatus.Lamp.RED),
    AIM_FAULT(HeaderStatus.Lamp.RED),
    IDLE(HeaderStatus.Lamp.GREY),
    OUT_OF_RANGE(HeaderStatus.Lamp.AMBER),
    OUT_OF_ARC(HeaderStatus.Lamp.AMBER),
    OFF_BORE(HeaderStatus.Lamp.AMBER),
    SLEWING(HeaderStatus.Lamp.AMBER),
    BLOCKED(HeaderStatus.Lamp.AMBER),
    EMPTY(HeaderStatus.Lamp.RED),
    LOADING(HeaderStatus.Lamp.AMBER),
    SAFE(HeaderStatus.Lamp.AMBER),
    READY(HeaderStatus.Lamp.GREEN),
    FIRING(HeaderStatus.Lamp.GREEN),

    UNSAFE(HeaderStatus.Lamp.RED);

    public static final int MODE_HOLD = 0;
    public static final int MODE_TRACK = 1;
    public static final int MODE_FIRE = 2;

    public static final int AXIS_YAW = 0;
    public static final int AXIS_PITCH = 1;

    public static final int EMPTY_AUTO = 0;
    public static final int EMPTY_BIG = 1;
    public static final int EMPTY_DRY = 2;
    public static final int EMPTY_NO_CHARGE = 3;
    public static final int EMPTY_NO_SHELL = 4;
    public static final int EMPTY_DEAD_CHARGE = 5;
    public static final int EMPTY_STUCK_SHELL = 6;
    public static final int EMPTY_DROP_MORTAR = 7;

    public static final int LOADING_STROKE = 0;
    public static final int LOADING_BREECH = 1;
    public static final int LOADING_SETTLE = 2;

    public static final int UNSAFE_OVERLOAD = 0;
    public static final int UNSAFE_BARREL = 1;
    public static final int UNSAFE_ORDER = 2;
    public static final int UNSAFE_OBSTRUCTED = 3;
    public static final int UNSAFE_SHELL = 4;
    public static final int UNSAFE_UNDERCHARGED = 5;
    public static final int UNSAFE_UNREADABLE = 6;

    private static final GunState[] VALUES = values();

    private final HeaderStatus.Lamp lamp;

    GunState(HeaderStatus.Lamp lamp) {
        this.lamp = lamp;
    }

    public HeaderStatus.Lamp lamp() {
        return lamp;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static GunState byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : IDLE;
    }

    public boolean salvoReady() {
        return this == READY || this == SAFE;
    }

    public boolean ready() {
        return this == READY || this == FIRING;
    }

    public record Inputs(boolean mountPresent, boolean assembled, int mode, boolean powered, boolean fixed,
                         boolean handAimed, boolean yawDrive, boolean pitchDrive, boolean yawTurning, boolean pitchTurning,
                         boolean heldElsewhere, boolean overstressed, boolean aimFault, boolean unsafe, boolean hasTarget,
                         int solution, boolean onTarget, boolean blocked, boolean empty, boolean loading,
                         boolean weaponsFree, boolean firing) {
    }

    public static GunState of(Inputs in) {
        if (!in.mountPresent()) return DOWN;
        if (!in.assembled()) return NOT_ASSEMBLED;
        if (in.mode() == MODE_HOLD) return HOLD;
        if (!in.powered()) return NO_POWER;
        if (!in.fixed()) {
            if (in.handAimed()) return HAND_AIMED;
            if (!in.yawDrive()) return NO_YAW_DRIVE;
            if (!in.pitchDrive()) return NO_PITCH_DRIVE;
            if (!in.yawTurning() || !in.pitchTurning()) return NO_ROTATION;
            if (in.heldElsewhere()) return HELD_ELSEWHERE;
            if (in.overstressed()) return OVERSTRESSED;
            if (in.aimFault()) return AIM_FAULT;
        }
        if (in.unsafe()) return UNSAFE;
        if (!in.hasTarget()) return IDLE;
        if (in.solution() == 1) return OUT_OF_RANGE;
        if (in.solution() == 2) return OUT_OF_ARC;
        if (!in.onTarget()) return in.fixed() ? OFF_BORE : SLEWING;
        if (in.blocked()) return BLOCKED;
        if (in.empty()) return EMPTY;
        if (in.loading()) return LOADING;
        if (in.firing()) return FIRING;
        if (in.mode() == MODE_FIRE && !in.weaponsFree()) return SAFE;
        return READY;
    }

    public static int faultAxis(Inputs in) {
        GunState s = of(in);
        return switch (s) {
            case NO_PITCH_DRIVE -> AXIS_PITCH;
            case NO_ROTATION -> in.yawTurning() ? AXIS_PITCH : AXIS_YAW;
            default -> AXIS_YAW;
        };
    }
}
