package com.createstats.power;

/** [1] */
public enum PduReason {

    /** Being served normally. */
    RUNNING("Running", false),
    /** Running because the player pinned it on, whatever the arithmetic says. */
    LOCKED_ON("Locked on by you, never shed", false),
    /** Off because the player switched it off. Not a fault and not a shed. */
    LOCKED_OFF("Switched off by you", false),
    /** Shed: the sustainable supply does not stretch this far down the list. */
    OVER_BUDGET("Shed: generation does not cover it", true),
    /** [2] */
    OVER_CAPACITY("Shed: branch wiring is at capacity", true),
    /** [3] */
    BREAKER_OPEN("Off: this leg's breaker is open", false),
    /** Shed: the buffer fell under the reserve floor, so only Critical may draw. */
    BELOW_FLOOR("Shed: reserve floor reached, Critical only", true),
    /** Shed by the tier rule in Manual mode: the buffer never reached this tier. */
    TIER_SHED("Shed: buffer did not reach this tier", true),
    /** Shed: it would fit, but there is no spare supply to bring it back on. */
    AWAITING_SUPPLY("Waiting for spare supply to restart", true),
    /** Shed: it would fit, but another device was restarted this interval. Staged restoration. */
    STAGED("Waiting its turn to restart", true),
    /** Holding its current state until the minimum switching time elapses. The anti-flap timer. */
    AWAITING_DWELL("Holding, anti-flap timer", false);

    private final String description;
    private final boolean shed;

    PduReason(String description, boolean shed) {
        this.description = description;
        this.shed = shed;
    }

    /** One line a player can act on, for the row tooltip. */
    public String description() {
        return description;
    }

    /** True when this reason means the panel chose not to serve the device. */
    public boolean isShed() {
        return shed;
    }

    /** The short flag shown inline on a row, where there is only room for a word. */
    public String shortFlag() {
        return switch (this) {
            case RUNNING -> "RUN";
            case LOCKED_ON -> "HELD";
            case LOCKED_OFF -> "OFF";
            case BREAKER_OPEN -> "TRIP";
            case AWAITING_DWELL -> "HOLD";
            default -> "SHED";
        };
    }

    /** Ordinal-safe lookup for NBT decode, defaulting to {@link #RUNNING}. */
    public static PduReason byOrdinal(int ordinal) {
        final PduReason[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RUNNING;
    }
}
