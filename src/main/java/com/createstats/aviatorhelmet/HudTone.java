package com.createstats.aviatorhelmet;

public enum HudTone {
    TUNGSTEN("tungsten", 0xFFB848, 0xFF9628, 0xFF6038, 0x1EFFD096),

    RADIUM("radium", 0x96FF78, 0x5AE65A, 0xFF7846, 0x16C8FFBE),

    MOONLIGHT("moonlight", 0xCDE8FF, 0x96BEF0, 0xFF785A, 0x12C8DCFF);

    public static final int COLD = 0xFF561C;

    public final String id;

    public final int core;

    public final int dim;

    public final int alert;

    public final int tint;

    HudTone(String id, int core, int dim, int alert, int tint) {
        this.id = id;
        this.core = core;
        this.dim = dim;
        this.alert = alert;
        this.tint = tint;
    }

    public static HudTone of(int ordinal) {
        final HudTone[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : TUNGSTEN;
    }

    public static HudTone next(int ordinal) {
        final HudTone[] v = values();
        return v[(of(ordinal).ordinal() + 1) % v.length];
    }

    public static int warmed(int rgb, double warm) {
        final double t = Math.max(0.0, Math.min(1.0, warm));
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            final int a = (COLD >> shift) & 0xFF;
            final int b = (rgb >> shift) & 0xFF;
            out |= ((int) Math.round(a + (b - a) * t) & 0xFF) << shift;
        }
        return out;
    }
}
