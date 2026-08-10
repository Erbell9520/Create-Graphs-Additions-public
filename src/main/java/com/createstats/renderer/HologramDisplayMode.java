package com.createstats.renderer;

public enum HologramDisplayMode
{
    LINE_3D("Line Style"),
    BAR_3D("Bar Style"),
    GAUGE_3D("Gauge Style"),
    // [1]
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
        // NETWORK is excluded from cycling and is normalised to LINE_3D on load, so
        // it is never the active mode.  The guard below is a safety net only.
        int next = (this.ordinal() + 1) % values.length;
        if (values[next] == NETWORK) {
            next = 0; // wrap back to LINE_3D
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
