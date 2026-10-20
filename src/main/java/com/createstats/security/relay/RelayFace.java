package com.createstats.security.relay;

public final class RelayFace {
    private RelayFace() {
    }

    public static final float BUTTON_U = 11.5f;
    public static final float BUTTON_V = 4.0f;
    public static final float BUTTON_HALF = 2.0f;

    public static final float CONTACT_LAMP_U = 4.0f;
    public static final float OUTPUT_LAMP_U = 12.0f;
    public static final float LAMP_V = 10.5f;

    public static final float WINDOW_U0 = 3.0f;
    public static final float WINDOW_U1 = 9.0f;
    public static final float WINDOW_V0 = 3.0f;
    public static final float WINDOW_V1 = 8.0f;

    public static final float FLAG_U = 11.5f;
    public static final float FLAG_V = 8.75f;

    public static final float PLATE_DEPTH = 3.0f;

    public static float u(int normalX, int normalZ, double x, double z) {
        final int rightX = normalZ;
        final int rightZ = -normalX;
        return (float) (((x - 0.5) * rightX + (z - 0.5) * rightZ + 0.5) * 16.0);
    }

    public static float v(double y) {
        return (float) (y * 16.0);
    }

    public static boolean onButton(float u, float v) {
        return Math.abs(u - BUTTON_U) <= BUTTON_HALF && Math.abs(v - BUTTON_V) <= BUTTON_HALF;
    }
}
