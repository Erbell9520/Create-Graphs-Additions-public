package com.createstats.vccmk3.hardware;

// the old loop was `if (propPos == null || govPos == null) continue;` and that was it. no warning, nothing
public enum LinkHealth {
    OK(Severity.GOOD,
            "Linked and ready",
            "Nothing to do."),

    EMPTY(Severity.IDLE,
            "Empty slot",
            "Drop a linked Propeller Card here to add hardware."),

    DISABLED(Severity.IDLE,
            "Switched off",
            "Enable it in the Hardware list when you want it back."),

    MISSING_ACTUATOR_CARD(Severity.WARN,
            "No Propeller Card in this row",
            "Add a Propeller Card linked to the propeller, wheel or bearing you want to drive."),

    MISSING_GOVERNOR_CARD(Severity.WARN,
            "No Governor Card in this row",
            "Place an RPM Governor in the shaft that drives this hardware, then link a Governor Card to it."),

    CARD_BLANK(Severity.WARN,
            "Card has never been linked",
            "Hold the card and right-click the block it should control."),

    CROSS_DIMENSION(Severity.BAD,
            "Card points to another dimension",
            "Re-link this card to hardware on this craft."),

    ACTUATOR_MISSING(Severity.BAD,
            "Target hardware is not there any more",
            "Re-link the Propeller Card, or clear this row."),

    GOVERNOR_MISSING(Severity.BAD,
            "Governor block is not there any more",
            "Re-place the RPM Governor and re-link its card."),

    WRONG_TARGET_TYPE(Severity.BAD,
            "That block cannot be driven",
            "Link to a propeller bearing, gyro bearing, wheel mount, swivel bearing or redstone link."),

    GOVERNOR_CLAIMED(Severity.BAD,
            "Another controller owns this governor",
            "Disarm the other controller, or give this hardware its own RPM Governor."),

    OFFLINE(Severity.BAD,
            "Hardware stopped responding",
            "Check the kinetic chain is still connected and the chunk is loaded.");

    public enum Severity {
        GOOD(0xFF6EE7A8),

        IDLE(0xFF53677A),

        WARN(0xFFFFB454),

        BAD(0xFFFF6B6B);

        private final int color;

        Severity(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    private final Severity severity;
    private final String problem;
    private final String fix;

    LinkHealth(Severity severity, String problem, String fix) {
        this.severity = severity;
        this.problem = problem;
        this.fix = fix;
    }

    public Severity severity() {
        return severity;
    }

    public String problem() {
        return problem;
    }

    public String fix() {
        return fix;
    }

    public int color() {
        return severity.color();
    }

    public boolean isCommandable() {
        return this == OK;
    }

    public boolean needsAttention() {
        return severity == Severity.WARN || severity == Severity.BAD;
    }
}
