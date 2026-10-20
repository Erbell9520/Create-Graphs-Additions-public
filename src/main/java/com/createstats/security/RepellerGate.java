package com.createstats.security;

// a repeller's whole effect is a setting, a stranger could retune zombies to bats in one click
public enum RepellerGate {
    ALLOW,

    LOCKED,

    NO_CLEARANCE,

    OUT_OF_CONTACT;

    public static RepellerGate decide(boolean governed, boolean locked, boolean rosterKnown,
                                      boolean passes) {
        if (!governed) {
            return ALLOW;
        }
        if (locked) {
            return LOCKED;
        }
        if (!rosterKnown) {
            return OUT_OF_CONTACT;
        }
        return passes ? ALLOW : NO_CLEARANCE;
    }

    public static boolean removalTrips(boolean passes) {
        return !passes;
    }
}
