package com.createstats.power;

public final class PortableFeColors {
    public static final int CHARGE_ON_BRASS = 0x1E6B24;
    public static final int CHARGE_ON_DARK = 0x55FF55;
    public static final String CHARGE_CODE = "§a";
    public static final String CHARGE_GLYPH = "▲";

    public static final int DRAW_ON_BRASS = 0xA8430A;
    public static final int DRAW_ON_DARK = 0xFFAA00;
    public static final String DRAW_CODE = "§6";
    public static final String DRAW_GLYPH = "▼";

    private PortableFeColors() {
    }

    public static int onBrass(PortableFeTransfer.Mode mode) {
        return mode == PortableFeTransfer.Mode.DRAW ? DRAW_ON_BRASS : CHARGE_ON_BRASS;
    }

    public static int onDark(PortableFeTransfer.Mode mode) {
        return mode == PortableFeTransfer.Mode.DRAW ? DRAW_ON_DARK : CHARGE_ON_DARK;
    }

    public static String code(PortableFeTransfer.Mode mode) {
        return mode == PortableFeTransfer.Mode.DRAW ? DRAW_CODE : CHARGE_CODE;
    }

    public static String glyph(PortableFeTransfer.Mode mode) {
        return mode == PortableFeTransfer.Mode.DRAW ? DRAW_GLYPH : CHARGE_GLYPH;
    }

    public static int fillOnDark(double fraction) {
        if (fraction < 0.2) {
            return 0xFF5555;
        }
        return fraction < 0.6 ? DRAW_ON_DARK : CHARGE_ON_DARK;
    }
}
