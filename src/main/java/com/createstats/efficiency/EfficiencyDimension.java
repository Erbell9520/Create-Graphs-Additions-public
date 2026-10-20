package com.createstats.efficiency;

public enum EfficiencyDimension {
    UTILIZATION("Load", "Φ_U", 0.22f, 0xFF44AAFF,
            "How well the network is using its capacity"),

    HEADROOM("Reserve", "Φ_H", 0.16f, 0xFF00FF88,
            "Margin left against the worst demand seen recently"),

    STABILITY("Rhythm", "Φ_S", 0.18f, 0xFF44FFCC,
            "How steady the RPM stays while working"),

    UPTIME("Reliability", "Φ_M", 0.22f, 0xFF88FF44,
            "How consistently the network avoids overstress"),

    SMOOTHNESS("Smoothness", "Φ_V", 0.12f, 0xFFB07CFF,
            "How evenly the load is drawn rather than in surges"),

    RESILIENCE("Resilience", "Φ_R", 0.10f, 0xFFFF7AA8,
            "How quickly overstress clears once it starts");

    private final String displayName;
    private final String symbol;
    private final float weight;
    private final int barColor;
    private final String description;

    EfficiencyDimension(String displayName, String symbol, float weight, int barColor, String description) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.weight = weight;
        this.barColor = barColor;
        this.description = description;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getSymbol() {
        return this.symbol;
    }

    public float getWeight() {
        return this.weight;
    }

    public int getBarColor() {
        return this.barColor;
    }

    public String getDescription() {
        return this.description;
    }

    public static float weightSum() {
        float sum = 0.0f;
        for (EfficiencyDimension d : values()) {
            sum += d.weight;
        }
        return sum;
    }
}
