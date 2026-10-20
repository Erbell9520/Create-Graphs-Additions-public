package com.createstats.security;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class Clearance {
    private Clearance() {
    }

    public static final int MIN = 1;
    public static final int MAX = 5;

    public static final int DEFAULT_REQUIRED = MIN;
    public static final int DEFAULT_GRANTED = MIN;

    private static final String[] KEYS = {"", "visitor", "guest", "resident", "trusted", "keyholder"};

    public static int clamp(int level) {
        return level < MIN ? MIN : level > MAX ? MAX : level;
    }

    public static String key(int level) {
        return KEYS[clamp(level)];
    }

    public static Component label(int level) {
        return Component.translatable("clearance.create_stats." + key(level));
    }

    public static Component badge(int level) {
        return Component.literal(clamp(level) + " ").append(label(level));
    }

    public static String plain(int level) {
        return KEYS[clamp(level)].toUpperCase(Locale.ROOT);
    }
}
