package com.createstats.security;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

// per sensor per hazard. one shared timestamp let fire and flood overwrite each other
public final class HazardWatch {
    public enum Hazard {
        FIRE,
        WATER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int RELEASE_FLOOR = SecurityHousehold.RELEASE_HOLD;

    private static final class Facet {
        long lastPositive = Long.MIN_VALUE;
        long lastClean = Long.MIN_VALUE;
        long lastHeartbeat = Long.MIN_VALUE;
        int sweepTicks;
        int cooldownTicks;
    }

    private final Map<SecurityEndpoint, Map<Hazard, Facet>> facets = new HashMap<>();

    public static long positiveLease(int sweepTicks, int cooldownTicks) {
        return Math.max(RELEASE_FLOOR, (long) Math.max(0, sweepTicks) + Math.max(0, cooldownTicks) + 5L);
    }

    public static long heartbeatLease(int sweepTicks) {
        return 2L * Math.max(5, sweepTicks) + 20L;
    }

    public enum Report {
        POSITIVE,

        SWEEP_CLEAN,

        SWEEP_FOUND
    }

    public void report(SecurityEndpoint sensor, Hazard hazard, Report report, long now, int sweepTicks,
                       int cooldownTicks) {
        switch (report) {
            case POSITIVE -> positive(sensor, hazard, now, sweepTicks, cooldownTicks);
            case SWEEP_CLEAN -> sweep(sensor, hazard, now, true, sweepTicks, cooldownTicks);
            case SWEEP_FOUND -> sweep(sensor, hazard, now, false, sweepTicks, cooldownTicks);
        }
    }

    public void positive(SecurityEndpoint sensor, Hazard hazard, long now, int sweepTicks, int cooldownTicks) {
        final Facet f = facet(sensor, hazard);
        f.lastPositive = now;
        f.sweepTicks = sweepTicks;
        f.cooldownTicks = cooldownTicks;
    }

    public void sweep(SecurityEndpoint sensor, Hazard hazard, long now, boolean clean, int sweepTicks,
                      int cooldownTicks) {
        final Facet f = facet(sensor, hazard);
        f.lastHeartbeat = now;
        f.sweepTicks = sweepTicks;
        f.cooldownTicks = cooldownTicks;
        if (clean) {
            f.lastClean = now;
        }
    }

    public Truth truth(SecurityEndpoint sensor, Hazard hazard, long now) {
        final Map<Hazard, Facet> bySensor = this.facets.get(sensor);
        final Facet f = bySensor == null ? null : bySensor.get(hazard);
        return f == null ? Truth.UNKNOWN : truthOf(f, now);
    }

    private static Truth truthOf(Facet f, long now) {
        final boolean alive = f.lastHeartbeat != Long.MIN_VALUE
                && now - f.lastHeartbeat <= heartbeatLease(f.sweepTicks);
        if (alive && f.lastClean != Long.MIN_VALUE && f.lastClean > f.lastPositive) {
            return Truth.FALSE;
        }
        if (f.lastPositive != Long.MIN_VALUE
                && now - f.lastPositive < positiveLease(f.sweepTicks, f.cooldownTicks)) {
            return Truth.TRUE;
        }
        return Truth.UNKNOWN;
    }

    public boolean heardFrom(SecurityEndpoint sensor, Hazard hazard) {
        final Map<Hazard, Facet> bySensor = this.facets.get(sensor);
        return bySensor != null && bySensor.containsKey(hazard);
    }

    public void retain(Predicate<SecurityEndpoint> keep) {
        this.facets.keySet().removeIf(sensor -> !keep.test(sensor));
    }

    private Facet facet(SecurityEndpoint sensor, Hazard hazard) {
        return this.facets.computeIfAbsent(sensor, k -> new java.util.EnumMap<>(Hazard.class))
                .computeIfAbsent(hazard, k -> new Facet());
    }
}
