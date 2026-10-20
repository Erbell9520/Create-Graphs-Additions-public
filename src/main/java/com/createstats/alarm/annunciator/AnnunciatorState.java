package com.createstats.alarm.annunciator;

public enum AnnunciatorState {
    NORMAL("normal", LampPattern.OFF, false),

    ALARM("alarm", LampPattern.FAST_FLASH, true),

    ACKNOWLEDGED("acknowledged", LampPattern.STEADY, false),

    RINGBACK("ringback", LampPattern.SLOW_FLASH, false);

    private final String serializedName;
    private final LampPattern lamp;
    private final boolean horn;

    AnnunciatorState(String serializedName, LampPattern lamp, boolean horn) {
        this.serializedName = serializedName;
        this.lamp = lamp;
        this.horn = horn;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public LampPattern lamp() {
        return this.lamp;
    }

    public boolean hornSounding() {
        return this.horn;
    }

    public boolean isActive() {
        return this == ALARM || this == ACKNOWLEDGED;
    }

    public boolean isOutstanding() {
        return this != NORMAL;
    }

    public static AnnunciatorState advance(AnnunciatorState current, boolean condition,
                                           boolean latching, boolean ack, boolean reset) {
        if (reset && !condition) {
            return NORMAL;
        }
        return switch (current) {
            case NORMAL -> condition ? ALARM : NORMAL;

            case ALARM -> {
                if (!condition) {
                    yield latching && !ack ? RINGBACK : NORMAL;
                }
                yield ack ? ACKNOWLEDGED : ALARM;
            }

            case ACKNOWLEDGED -> condition ? ACKNOWLEDGED : NORMAL;

            case RINGBACK -> {
                if (condition) {
                    yield ALARM;
                }
                yield (ack || reset) ? NORMAL : RINGBACK;
            }
        };
    }

    public static AnnunciatorState byName(String name) {
        for (AnnunciatorState state : values()) {
            if (state.serializedName.equalsIgnoreCase(name) || state.name().equalsIgnoreCase(name)) {
                return state;
            }
        }
        return NORMAL;
    }
}
