package com.createstats.driverhelmet;

public enum HudTheme {
    TELEMETRY("telemetry", 0x14161B, 0.82, 0xFFB23A, 0xF2E6CF, 0xFFB23A, 0x7FA4FF, 0x62FF8C, 0xFFB23A, 0xFF5245,
            0x62FF8C, 0xFFB23A, 0x7FA4FF),

    DARK("dark", 0x07080A, 0.90, 0x3A4150, 0xB9C2CF, 0xE6EEF7, 0x6FB8D6, 0x7FD6A8, 0xD9A441, 0xE05A50,
            0x6FB8D6, 0x9FD3E6, 0xE6EEF7),

    RACING("racing", 0x000000, 0.86, 0xE10600, 0xFFFFFF, 0xFFD400, 0x2EA8FF, 0x00D26A, 0xFFD400, 0xE10600,
            0x00D26A, 0xE10600, 0x2E6BFF),

    CREATE("create", 0x2B2620, 0.86, 0xE4B04A, 0xF1E3C2, 0xFF9A3C, 0x7FC8E8, 0x8BD66B, 0xE4B04A, 0xE0613E,
            0xD17A45, 0xE4B04A, 0xFF9A3C),

    PUNK("punk", 0x000000, 0.88, 0xE0143A, 0xC9CED6, 0xFF2E3C, 0x7FE0FF, 0x4DFF88, 0xFFB22E, 0xFF3CC8,
            0xB8102A, 0xFF2E3C, 0xFF9AA2);

    public boolean glows() {
        return this == PUNK;
    }

    public final String id;

    public final int plate;
    public final double plateAlpha;

    public final int stripe;

    public final int text;

    public final int primary;

    public final int cool;
    public final int ok;
    public final int warn;
    public final int bad;

    public final int shiftLow;
    public final int shiftMid;
    public final int shiftHigh;

    HudTheme(String id, int plate, double plateAlpha, int stripe, int text, int primary, int cool, int ok, int warn,
             int bad, int shiftLow, int shiftMid, int shiftHigh) {
        this.id = id;
        this.plate = plate;
        this.plateAlpha = plateAlpha;
        this.stripe = stripe;
        this.text = text;
        this.primary = primary;
        this.cool = cool;
        this.ok = ok;
        this.warn = warn;
        this.bad = bad;
        this.shiftLow = shiftLow;
        this.shiftMid = shiftMid;
        this.shiftHigh = shiftHigh;
    }

    public static HudTheme of(int ordinal) {
        final HudTheme[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : TELEMETRY;
    }

    public static HudTheme next(int ordinal) {
        return values()[(of(ordinal).ordinal() + 1) % values().length];
    }
}
