package com.createstats.power;

public record FeIdleLoad(boolean powered, int spend, int rated, int measured) {
    public static FeIdleLoad of(boolean suppressed, int energy, int idleDraw) {
        final int cost = Math.max(0, idleDraw);

        final int rated = suppressed ? 0 : cost;

        final boolean powered = !suppressed && energy >= cost;
        return new FeIdleLoad(powered, powered ? cost : 0, rated, powered ? cost : 0);
    }
}
