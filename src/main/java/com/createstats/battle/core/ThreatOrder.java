package com.createstats.battle.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ThreatOrder {
    public static final int MAX = 24;

    public static final double CLOSING = -0.5;

    public enum Kind { SHIP, MOB, PLAYER }

    public record Contact(int index, Kind kind, boolean current, double range, double rangeRate, double approach,
                          boolean hostile, boolean targetsCrew) {
    }

    private ThreatOrder() {
    }

    public static int band(Contact c) {
        if (c.current()) return 0;
        return switch (c.kind()) {
            case SHIP -> c.rangeRate() < CLOSING ? 1 : 4;
            case MOB -> c.hostile() ? (c.targetsCrew() ? 2 : 3) : 5;
            case PLAYER -> 6;
        };
    }

    public static final int PLAYER_BAND = 6;

    public static List<Contact> order(List<Contact> in) {
        List<Contact> out = new ArrayList<>(in);
        out.sort(Comparator.comparingInt(ThreatOrder::band)
                .thenComparingDouble(c -> band(c) == 1 ? c.approach() : c.range())
                .thenComparingInt(Contact::index));
        return out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : out;
    }
}
