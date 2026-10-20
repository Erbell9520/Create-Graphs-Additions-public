package com.createstats.alarm.annunciator;

public enum RuleComparator {
    ABOVE("above", "≥"),

    BELOW("below", "≤"),

    EQUALS("equals", "≈");

    private static final float EQUALS_EPSILON = 0.001f;

    private final String serializedName;
    private final String symbol;

    RuleComparator(String serializedName, String symbol) {
        this.serializedName = serializedName;
        this.symbol = symbol;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public String symbol() {
        return this.symbol;
    }

    public boolean trips(float observed, float threshold, float deadband) {
        return switch (this) {
            case ABOVE -> observed >= threshold;
            case BELOW -> observed <= threshold;
            case EQUALS -> Math.abs(observed - threshold) <= tolerance(deadband);
        };
    }

    public boolean holds(float observed, float threshold, float deadband) {
        final float margin = Math.max(0.0f, deadband);

        return switch (this) {
            case ABOVE -> observed >= threshold - margin;
            case BELOW -> observed <= threshold + margin;
            case EQUALS -> Math.abs(observed - threshold) <= tolerance(deadband) * 2.0f;
        };
    }

    private static float tolerance(float deadband) {
        return Math.max(EQUALS_EPSILON, Math.abs(deadband));
    }

    public RuleComparator next() {
        final RuleComparator[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static RuleComparator byName(String name) {
        for (RuleComparator comparator : values()) {
            if (comparator.serializedName.equalsIgnoreCase(name) || comparator.name().equalsIgnoreCase(name)) {
                return comparator;
            }
        }
        return ABOVE;
    }
}
