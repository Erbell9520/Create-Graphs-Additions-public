package com.createstats.power;

public record FeBandSettings(Unit unit, int floor, int top, int healthy, int capFloor, int capTop, int capHealthy) {
    public enum Unit {
        MINUTES, PERCENT
    }

    public static final int MAX_MINUTES = 100_000;

    public FeBandSettings {
        unit = unit == null ? Unit.MINUTES : unit;
        if (unit == Unit.PERCENT) {
            floor = Math.max(0, Math.min(95, floor));
            top = Math.max(floor, Math.min(98, top));
            healthy = Math.max(top, Math.min(100, healthy));
        } else {
            floor = Math.max(0, Math.min(MAX_MINUTES, floor));
            top = Math.max(floor, Math.min(MAX_MINUTES, top));
            healthy = Math.max(top, Math.min(MAX_MINUTES, healthy));
        }
        capFloor = Math.max(0, Math.min(95, capFloor));
        capTop = Math.max(capFloor, Math.min(98, capTop));
        capHealthy = Math.max(capTop, Math.min(100, capHealthy));
    }

    public static FeBandSettings server(FeReserveBand.Band caps, FeReserveBand.Minutes minutes) {
        final FeReserveBand.Band c = caps == null ? FeReserveBand.Band.DEFAULT : caps;
        final FeReserveBand.Minutes m = minutes == null ? FeReserveBand.Minutes.DEFAULT : minutes;
        return new FeBandSettings(Unit.MINUTES, m.floor(), m.ceiling(), m.healthy(), c.floorPct(), c.ceilingPct(),
                c.healthyPct());
    }

    public FeReserveBand.Band caps() {
        return new FeReserveBand.Band(capFloor, capTop, capHealthy);
    }

    public FeReserveBand.Band band(int capacity, int criticalWant, int fullRate, int rideTicks) {
        if (unit == Unit.PERCENT) {
            return new FeReserveBand.Band(floor, top, healthy);
        }
        return FeReserveBand.sized(caps(), new FeReserveBand.Minutes(floor, top, healthy), capacity, criticalWant,
                fullRate, rideTicks);
    }

    public int edge(int edge) {
        return edge == 0 ? floor : edge == 1 ? top : healthy;
    }

    public int cap(int edge) {
        return edge == 0 ? capFloor : edge == 1 ? capTop : capHealthy;
    }

    public FeBandSettings withEdge(int edge, int value) {
        return switch (edge) {
            case 0 -> new FeBandSettings(unit, value, Math.max(value, top), Math.max(value, healthy), capFloor, capTop,
                    capHealthy);
            case 1 -> new FeBandSettings(unit, Math.min(floor, value), value, Math.max(value, healthy), capFloor,
                    capTop, capHealthy);
            case 2 -> new FeBandSettings(unit, Math.min(floor, value), Math.min(top, value), value, capFloor, capTop,
                    capHealthy);
            default -> this;
        };
    }

    public FeBandSettings withCap(int edge, int value) {
        return switch (edge) {
            case 0 -> new FeBandSettings(unit, floor, top, healthy, value, Math.max(value, capTop),
                    Math.max(value, capHealthy));
            case 1 -> new FeBandSettings(unit, floor, top, healthy, Math.min(capFloor, value), value,
                    Math.max(value, capHealthy));
            case 2 -> new FeBandSettings(unit, floor, top, healthy, Math.min(capFloor, value), Math.min(capTop, value),
                    value);
            default -> this;
        };
    }

    public FeBandSettings withUnit(Unit to, int capacity, int criticalWant, int fullRate, int rideTicks,
                                   FeReserveBand.Minutes fallback) {
        if (to == null || to == unit) {
            return this;
        }
        if (to == Unit.PERCENT) {
            final FeReserveBand.Band now = band(capacity, criticalWant, fullRate, rideTicks);
            return new FeBandSettings(Unit.PERCENT, now.floorPct(), now.ceilingPct(), now.healthyPct(), capFloor,
                    capTop, capHealthy);
        }
        if (criticalWant <= 0 || capacity <= 0) {
            final FeReserveBand.Minutes m = fallback == null ? FeReserveBand.Minutes.DEFAULT : fallback;
            return new FeBandSettings(Unit.MINUTES, m.floor(), m.ceiling(), m.healthy(), capFloor, capTop, capHealthy);
        }
        return new FeBandSettings(Unit.MINUTES, minutesFor(floor, capacity, criticalWant),
                minutesFor(top, capacity, criticalWant), minutesFor(healthy, capacity, criticalWant), capFloor, capTop,
                capHealthy);
    }

    public static int minutesFor(int pct, int capacity, int criticalWant) {
        if (pct <= 0 || capacity <= 0 || criticalWant <= 0) {
            return 0;
        }
        final long perMinute = (long) criticalWant * FeReserveBand.TICKS_PER_MINUTE;
        final long fe = (long) capacity * pct / 100L;
        return (int) Math.min(MAX_MINUTES, fe / perMinute);
    }
}
