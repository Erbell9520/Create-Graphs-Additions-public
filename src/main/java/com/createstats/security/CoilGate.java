package com.createstats.security;

import com.createstats.stormcoil.CoilRules;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public enum CoilGate {
    ALLOW,

    PANEL_SETS,

    LOCKED,

    NO_CLEARANCE,

    OUT_OF_CONTACT;

    public enum Box {
        TARGETS,
        HEIGHT
    }

    public static CoilGate decide(Box box, boolean governed, boolean locked, boolean rosterKnown, boolean passes) {
        if (!governed) {
            return ALLOW;
        }
        if (locked) {
            return LOCKED;
        }
        if (box == Box.TARGETS) {
            return PANEL_SETS;
        }
        if (!rosterKnown) {
            return OUT_OF_CONTACT;
        }
        return passes ? ALLOW : NO_CLEARANCE;
    }

    public static boolean removalTrips(boolean passes) {
        return RepellerGate.removalTrips(passes);
    }

    public static CoilRules.Person personOf(@Nullable SecurityRoster roster, @Nullable UUID id) {
        if (roster == null || id == null) {
            return CoilRules.Person.UNKNOWN;
        }
        if (roster.isOwner(id)) {
            return CoilRules.Person.KEYHOLDER;
        }
        final SecurityRoster.Entry e = roster.find(id);
        if (e == null) {
            return CoilRules.Person.STRANGER;
        }
        if (e.role() != SecurityRole.MEMBER) {
            return CoilRules.Person.KEYHOLDER;
        }
        return CoilRules.Person.member(e.level());
    }
}
