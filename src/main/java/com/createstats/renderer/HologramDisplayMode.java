package com.createstats.renderer;

public enum HologramDisplayMode
{
    LINE_3D("Line Style"),
    BAR_3D("Bar Style"),
    GAUGE_3D("Gauge Style"),

    NETWORK("Line (Wide)");

    private final String displayName;

    private HologramDisplayMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public HologramDisplayMode next() {
        final HologramDisplayMode[] values = values();

        int next = (this.ordinal() + 1) % values.length;
        if (values[next] == NETWORK) {
            next = 0;
        }
        return values[next];
    }

    public float getWidthScale() {
        return switch (this) {
            case BAR_3D -> 1.10f;
            case GAUGE_3D -> 0.95f;
            default -> 1.0f;
        };
    }

    public float getHeightScale() {
        return switch (this) {
            case BAR_3D -> 0.92f;
            case GAUGE_3D -> 1.08f;
            default -> 1.0f;
        };
    }
}
