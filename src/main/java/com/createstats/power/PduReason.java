package com.createstats.power;

public enum PduReason {
    RUNNING("Running", false),

    LOCKED_ON("Locked on by you, never shed", false),

    LOCKED_OFF("Switched off by you", false),

    OVER_BUDGET("Shed: generation does not cover it", true),

    OVER_CAPACITY("Shed: branch wiring is at capacity", true),

    BREAKER_OPEN("Off: this leg's breaker is open", false),

    BELOW_FLOOR("Shed: reserve floor reached, Critical only", true),

    TIER_SHED("Shed: buffer did not reach this tier", true),

    AWAITING_SUPPLY("Waiting for spare supply to restart", true),

    STAGED("Waiting its turn to restart", true),

    AWAITING_DWELL("Holding, anti-flap timer", false),

    CORE_RESERVE("Shed: the reserve upstream is being protected for Critical", true),

    STORAGE_CHARGING("Charging off the branch", false),

    STORAGE_DISCHARGING("Discharging to carry the branch", false),

    STORAGE_FULL("Full", false),

    STORAGE_IDLE("Idle, nothing spare to store", false),

    SUPPLY_FALLING("Held: supply is forecast to fall within the minute", true),

    HEADROOM("Held: fits, but not with headroom to spare yet", true),

    WIRE_SHORT("Running short: its studs carry less than it costs", false);

    private final String description;
    private final boolean shed;

    PduReason(String description, boolean shed) {
        this.description = description;
        this.shed = shed;
    }

    public String description() {
        return description;
    }

    public boolean isShed() {
        return shed;
    }

    public String shortLabel() {
        return switch (this) {
            case RUNNING -> "Running";
            case LOCKED_ON -> "Pinned on";
            case LOCKED_OFF -> "Off by you";
            case OVER_BUDGET -> "No supply";
            case OVER_CAPACITY -> "Wire full";
            case BREAKER_OPEN -> "Breaker";
            case BELOW_FLOOR -> "Floor hold";
            case TIER_SHED -> "Below tier";
            case AWAITING_SUPPLY -> "No spare";
            case STAGED -> "Queued";
            case AWAITING_DWELL -> "Anti-flap";
            case CORE_RESERVE -> "Held back";
            case STORAGE_CHARGING -> "Charging";
            case STORAGE_DISCHARGING -> "Draining";
            case STORAGE_FULL -> "Full";
            case STORAGE_IDLE -> "Idle";
            case SUPPLY_FALLING -> "Dimming";
            case HEADROOM -> "Headroom";
            case WIRE_SHORT -> "Wire short";
        };
    }

    public String shortFlag() {
        return switch (this) {
            case RUNNING -> "RUN";
            case LOCKED_ON -> "HELD";
            case LOCKED_OFF -> "OFF";
            case BREAKER_OPEN -> "TRIP";
            case AWAITING_DWELL -> "HOLD";
            case STORAGE_CHARGING -> "CHG";
            case STORAGE_DISCHARGING -> "DIS";
            case STORAGE_FULL -> "FULL";
            case STORAGE_IDLE -> "IDLE";
            case WIRE_SHORT -> "LOW";
            default -> "SHED";
        };
    }

    public static PduReason byOrdinal(int ordinal) {
        final PduReason[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RUNNING;
    }
}
