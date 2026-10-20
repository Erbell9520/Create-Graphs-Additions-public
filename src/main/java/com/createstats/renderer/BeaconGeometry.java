package com.createstats.renderer;

public final class BeaconGeometry {
    private BeaconGeometry() {
    }

    public static final float[][] OUTLINE = {
            {5.40f, 4.60f},
            {10.60f, 4.60f},
            {11.40f, 5.40f},
            {11.40f, 10.60f},
            {10.60f, 11.40f},
            {5.40f, 11.40f},
            {4.60f, 10.60f},
            {4.60f, 5.40f},
    };

    public static final float VANE_Y0 = 12.90f;
    public static final float VANE_Y1 = 14.50f;
    public static final float VANE_RADIUS = 2.50f;
    public static final float VANE_HALF_THICK = 0.35f;

    public static float[] span(String segment) {
        return switch (segment) {
            case "single" -> new float[] {5.10f, 12.00f};
            case "base" -> new float[] {5.10f, 15.10f};
            case "middle" -> new float[] {0.90f, 15.10f};
            case "top" -> new float[] {0.90f, 12.00f};
            default -> null;
        };
    }
}
