package com.createstats.rs;

import java.util.Locale;

public final class RsChannels {
    public static final String STORED = "rs_stored";

    public static final String CAPACITY = "rs_capacity";

    public static final String FILL = "rs_fill";

    public static final String TYPES = "rs_types";

    public static final String FLUID = "rs_fluid";
    public static final String FLUID_TYPES = "rs_fluid_types";

    public static final String ENERGY = "rs_energy";
    public static final String ENERGY_PERCENT = "rs_energy_pct";

    public static final String PATTERNS = "rs_patterns";

    public static final String DISKS = "rs_disks";

    public static final String ONLINE = "rs_online";

    public static final String ITEM_PREFIX = "rsi_";

    private RsChannels() {
    }

    public static String itemMetricId(String itemId) {
        final String safe = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);
        final StringBuilder sb = new StringBuilder(ITEM_PREFIX.length() + safe.length());
        sb.append(ITEM_PREFIX);
        for (int i = 0; i < safe.length(); i++) {
            final char c = safe.charAt(i);
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : '_');
        }
        return sb.toString();
    }
}
