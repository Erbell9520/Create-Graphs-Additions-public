package com.createstats.power;

public record FeLoadCost(int budgetCost, int reportedDraw) {
    public static final FeLoadCost NONE = new FeLoadCost(0, 0);

    public FeLoadCost {
        budgetCost = Math.max(0, budgetCost);
        reportedDraw = Math.max(0, reportedDraw);
    }

    public static FeLoadCost rated(int ratedDraw, int measuredDraw) {
        return new FeLoadCost(ratedDraw, measuredDraw);
    }

    public static FeLoadCost consumer(int measuredDraw, int activeDrain) {
        return new FeLoadCost(measuredDraw > 0 ? measuredDraw : activeDrain, measuredDraw);
    }

    public static FeLoadCost opaque(int headroom) {
        return new FeLoadCost(headroom, headroom);
    }
}
