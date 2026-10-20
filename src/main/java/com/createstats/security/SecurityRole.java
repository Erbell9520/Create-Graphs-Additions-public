package com.createstats.security;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum SecurityRole {
    OWNER,

    ADMIN,

    MEMBER;

    private static final SecurityRole[] ORDER = values();

    public boolean isOwner() {
        return this == OWNER;
    }

    public boolean mayEditRoster() {
        return this != MEMBER;
    }

    public boolean mayEditDevices() {
        return this != MEMBER;
    }

    public boolean mayEditPanel() {
        return this == OWNER;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component label() {
        return Component.translatable("role.create_stats." + key());
    }

    public static SecurityRole byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : MEMBER;
    }

    public static SecurityRole byName(String name) {
        for (SecurityRole role : ORDER) {
            if (role.name().equals(name)) {
                return role;
            }
        }
        return MEMBER;
    }
}
