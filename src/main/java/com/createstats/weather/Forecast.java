package com.createstats.weather;

import java.util.List;

public record Forecast(List<Band> bands, List<DayCell> days, int certainUntilTicks) {
    public record Band(int startTick, int endTick, Condition condition, boolean certain) {
    }

    // no percent on a locked cell
    public record DayCell(int dayIndex, Condition condition, int popPercent, boolean certain,
                          float highC, float lowC) {
    }

    public static Forecast none() {
        return new Forecast(List.of(), List.of(), 0);
    }

    public boolean isEmpty() {
        return days.isEmpty();
    }
}
