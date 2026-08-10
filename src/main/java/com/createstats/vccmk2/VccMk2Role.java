package com.createstats.vccmk2;

/** [1] */
public enum VccMk2Role {
    UNASSIGNED("Idle", "-", 0xFF4A525C),
    LIFT("Lift", "L", 0xFF5FA8F5),
    THRUST("Thrust", "T", 0xFF63DC7E),
    YAW("Yaw", "Y", 0xFFF6A94A),
    PITCH("Pitch", "P", 0xFFB98BF0),
    ROLL("Roll", "R", 0xFFEC7AB0),
    DRIVE("Drive", "D", 0xFFEAD358),
    STEER("Steer", "S", 0xFF66D8E0);

    private final String label;
    private final String abbrev;
    private final int color;

    VccMk2Role(String label, String abbrev, int color) {
        this.label = label;
        this.abbrev = abbrev;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public String abbrev() {
        return abbrev;
    }

    public int color() {
        return color;
    }

    public boolean isAssigned() {
        return this != UNASSIGNED;
    }

    public static VccMk2Role byOrdinal(int ordinal) {
        VccMk2Role[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : UNASSIGNED;
    }

    /** Bit for this role inside an engine's role mask. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Roles a player can toggle in the inspector. */
    public static final VccMk2Role[] ASSIGNABLE = {
            LIFT, THRUST, YAW, PITCH, ROLL, DRIVE, STEER
    };

    /** Priority order for picking a node's base colour when it holds several roles. */
    public static final VccMk2Role[] PRIORITY = {
            LIFT, THRUST, DRIVE, STEER, YAW, PITCH, ROLL
    };
}
