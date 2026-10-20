package com.createstats.security;

import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayConditions;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayReading;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// facts in, readings out, no world
public final class SecuritySituations {
    private SecuritySituations() {
    }

    public record Watched(SecurityEndpoint at, String kind, Presence presence, boolean breached,
                          boolean impaired, boolean required, SecurityEndpoint doorway) {
        public Watched {
            doorway = doorway == null ? at : doorway;
        }

        public Watched(SecurityEndpoint at, String kind, Presence presence, boolean breached,
                       boolean impaired, boolean required) {
            this(at, kind, presence, breached, impaired, required, at);
        }
    }

    public record Facts(boolean lockdown, String modeKey, long now, Map<SecurityEndpoint, Watched> devices,
                        HazardWatch hazards) {
    }

    public static RelayReading read(RelayConfig config, Facts facts) {
        final RelayCondition c = config.condition();
        if (c == null || c.isPoint()) {
            return RelayReading.NONE;
        }
        if (c == RelayConditions.LOCKDOWN) {
            return RelayReading.of(Truth.of(facts.lockdown()));
        }
        if (c == RelayConditions.MODE) {
            return RelayReading.of(Truth.of(facts.modeKey().equals(config.mode())));
        }
        final Collection<Watched> scope = scope(config, facts);
        final boolean narrowedAway = config.scope() != null && !facts.devices().containsKey(config.scope());
        if (c == RelayConditions.FIRE) {
            return hazard(HazardWatch.Hazard.FIRE, SecurityKinds.SENSOR_FIRE, scope, facts);
        }
        if (c == RelayConditions.WATER) {
            return hazard(HazardWatch.Hazard.WATER, SecurityKinds.SENSOR_WATER, scope, facts);
        }
        if (c == RelayConditions.BREACHED) {
            return breach(scope);
        }
        if (c == RelayConditions.TROUBLE) {
            return narrowedAway ? new RelayReading(Truth.TRUE, 1, 1) : trouble(scope);
        }
        if (c == RelayConditions.HEALTHY) {
            return narrowedAway ? new RelayReading(Truth.FALSE, 1, 1) : healthy(scope);
        }
        return RelayReading.NONE;
    }

    private static Collection<Watched> scope(RelayConfig config, Facts facts) {
        if (config.scope() == null) {
            return facts.devices().values();
        }
        final Watched one = facts.devices().get(config.scope());
        if (one == null) {
            return List.of();
        }
        final List<Watched> doorway = new ArrayList<>();
        doorway.add(one);
        for (Watched w : facts.devices().values()) {
            if (!w.at().equals(one.at()) && w.doorway().equals(one.at())) {
                doorway.add(w);
            }
        }
        return doorway;
    }

    private static RelayReading hazard(HazardWatch.Hazard hazard, String kind, Collection<Watched> scope,
                                       Facts facts) {
        int sources = 0;
        int unavailable = 0;
        Truth any = Truth.FALSE;
        for (Watched w : scope) {
            if (!kind.equals(w.kind())) {
                continue;
            }
            sources++;
            Truth t = facts.hazards().truth(w.at(), hazard, facts.now());
            if (!w.presence().available() && t != Truth.TRUE) {
                t = Truth.UNKNOWN;
            }
            if (t == Truth.UNKNOWN) {
                unavailable++;
            }
            any = any.or(t);
        }
        return new RelayReading(sources == 0 ? Truth.UNKNOWN : any, sources, unavailable);
    }

    private static RelayReading breach(Collection<Watched> scope) {
        int sources = 0;
        int unavailable = 0;
        Truth any = Truth.FALSE;
        for (Watched w : scope) {
            if (!SecurityKinds.breachable(w.kind())) {
                continue;
            }
            sources++;
            final Truth t = broken(w) ? Truth.TRUE
                    : w.presence().available() ? Truth.of(w.breached()) : Truth.UNKNOWN;
            if (t == Truth.UNKNOWN) {
                unavailable++;
            }
            any = any.or(t);
        }
        return new RelayReading(sources == 0 ? Truth.UNKNOWN : any, sources, unavailable);
    }

    private static boolean broken(Watched w) {
        return w.presence() == Presence.MISSING;
    }

    private static RelayReading trouble(Collection<Watched> scope) {
        int sources = 0;
        int troubled = 0;
        for (Watched w : scope) {
            if (!w.required()) {
                continue;
            }
            sources++;
            if (inTrouble(w)) {
                troubled++;
            }
        }
        return new RelayReading(Truth.of(troubled > 0), sources, troubled);
    }

    private static RelayReading healthy(Collection<Watched> scope) {
        final RelayReading t = trouble(scope);
        return new RelayReading(Truth.of(t.sources() > 0 && t.unavailable() == 0), t.sources(),
                t.unavailable());
    }

    public static boolean inTrouble(Watched w) {
        return !w.presence().available() || w.breached() || w.impaired();
    }
}
