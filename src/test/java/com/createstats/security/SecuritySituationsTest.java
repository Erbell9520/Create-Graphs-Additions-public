package com.createstats.security;

import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayConditions;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayReading;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecuritySituationsTest {
    private static final String DIM = "minecraft:overworld";
    private static final int SWEEP = 55;
    private static final int COOLDOWN = 40;

    private static SecurityEndpoint at(long p) {
        return SecurityEndpoint.of(DIM, p);
    }

    private static SecuritySituations.Watched dev(long p, String kind, Presence presence) {
        return new SecuritySituations.Watched(at(p), kind, presence, false, false,
                SecurityKinds.monitors(kind));
    }

    private static RelayConfig cfg(RelayCondition c) {
        return new RelayConfig(c.id(), RelayBehavior.FOLLOW, 10, "away", null);
    }

    private static SecuritySituations.Facts facts(long now, HazardWatch hazards, SecuritySituations.Watched... ds) {
        final Map<SecurityEndpoint, SecuritySituations.Watched> m = new HashMap<>();
        for (SecuritySituations.Watched d : ds) {
            m.put(d.at(), d);
        }
        return new SecuritySituations.Facts(false, "home", now, m, hazards);
    }

    private static Truth read(RelayCondition c, SecuritySituations.Facts f) {
        return SecuritySituations.read(cfg(c), f).truth();
    }

    @Test
    @DisplayName("Fire and water are independent: both at once, and clearing one leaves the other")
    void fireAndWaterIndependent() {
        final HazardWatch w = new HazardWatch();
        w.positive(at(1), HazardWatch.Hazard.FIRE, 100, SWEEP, COOLDOWN);
        w.positive(at(2), HazardWatch.Hazard.WATER, 100, 30, COOLDOWN);
        final SecuritySituations.Watched fire = dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT);
        final SecuritySituations.Watched water = dev(2, SecurityKinds.SENSOR_WATER, Presence.PRESENT);
        assertEquals(Truth.TRUE, read(RelayConditions.FIRE, facts(110, w, fire, water)));
        assertEquals(Truth.TRUE, read(RelayConditions.WATER, facts(110, w, fire, water)));

        w.sweep(at(2), HazardWatch.Hazard.WATER, 140, true, 30, COOLDOWN);
        assertEquals(Truth.FALSE, read(RelayConditions.WATER, facts(141, w, fire, water)));
        assertEquals(Truth.TRUE, read(RelayConditions.FIRE, facts(141, w, fire, water)),
                "clearing water must not clear fire");
    }

    @Test
    @DisplayName("A fire is TRUE with one dark sensor elsewhere; ANY, with the unavailable counted")
    void anyWithAnUnknown() {
        final HazardWatch w = new HazardWatch();
        w.positive(at(1), HazardWatch.Hazard.FIRE, 100, SWEEP, COOLDOWN);
        final RelayReading r = SecuritySituations.read(cfg(RelayConditions.FIRE), facts(110, w,
                dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT),
                dev(2, SecurityKinds.SENSOR_FIRE, Presence.UNLOADED)));
        assertEquals(Truth.TRUE, r.truth());
        assertEquals(2, r.sources());
        assertEquals(1, r.unavailable());
    }

    @Test
    @DisplayName("No fire anywhere seen, but one sensor dark: UNKNOWN, never FALSE")
    void unknownIsNotClear() {
        final HazardWatch w = new HazardWatch();
        w.sweep(at(1), HazardWatch.Hazard.FIRE, 100, true, SWEEP, COOLDOWN);
        assertEquals(Truth.UNKNOWN, read(RelayConditions.FIRE, facts(110, w,
                dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT),
                dev(2, SecurityKinds.SENSOR_FIRE, Presence.UNREACHED))));
        assertEquals(Truth.FALSE, read(RelayConditions.FIRE, facts(110, w,
                dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT))));
    }

    @Test
    @DisplayName("A half-finished sweep cannot clear a fire found on another layer")
    void partialSweepCannotClear() {
        final HazardWatch w = new HazardWatch();
        w.sweep(at(1), HazardWatch.Hazard.FIRE, 50, true, SWEEP, COOLDOWN);
        w.positive(at(1), HazardWatch.Hazard.FIRE, 100, SWEEP, COOLDOWN);

        assertEquals(Truth.TRUE, read(RelayConditions.FIRE,
                facts(140, w, dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT))));

        w.sweep(at(1), HazardWatch.Hazard.FIRE, 150, false, SWEEP, COOLDOWN);
        assertEquals(Truth.TRUE, read(RelayConditions.FIRE,
                facts(151, w, dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT))));
    }

    @Test
    @DisplayName("A stale report cannot clear: a silent sensor ages from TRUE to UNKNOWN, never to FALSE")
    void staleCannotClear() {
        final HazardWatch w = new HazardWatch();
        w.sweep(at(1), HazardWatch.Hazard.FIRE, 50, true, SWEEP, COOLDOWN);
        w.positive(at(1), HazardWatch.Hazard.FIRE, 100, SWEEP, COOLDOWN);
        final long lease = HazardWatch.positiveLease(SWEEP, COOLDOWN);
        assertEquals(Truth.TRUE, read(RelayConditions.FIRE,
                facts(100 + lease - 1, w, dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT))));
        assertEquals(Truth.UNKNOWN, read(RelayConditions.FIRE,
                facts(100 + lease, w, dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT))));
    }

    @Test
    @DisplayName("A sensor that is out of sight keeps only its TRUE")
    void outOfSightKeepsOnlyTrue() {
        final HazardWatch w = new HazardWatch();
        w.positive(at(1), HazardWatch.Hazard.FIRE, 100, SWEEP, COOLDOWN);
        assertEquals(Truth.TRUE, read(RelayConditions.FIRE,
                facts(110, w, dev(1, SecurityKinds.SENSOR_FIRE, Presence.MISSING))),
                "a fire sensor the fire destroyed was right about the fire");
        final HazardWatch clean = new HazardWatch();
        clean.sweep(at(1), HazardWatch.Hazard.FIRE, 100, true, SWEEP, COOLDOWN);
        assertEquals(Truth.UNKNOWN, read(RelayConditions.FIRE,
                facts(110, clean, dev(1, SecurityKinds.SENSOR_FIRE, Presence.UNREACHED))));
    }

    @Test
    @DisplayName("An empty scope is UNKNOWN for a trigger: no fire sensors is not a house without fire")
    void emptyScopeIsUnknown() {
        final HazardWatch w = new HazardWatch();
        assertEquals(Truth.UNKNOWN, read(RelayConditions.FIRE,
                facts(1, w, dev(1, SecurityKinds.SENSOR_MOTION, Presence.PRESENT))));
        assertEquals(Truth.UNKNOWN, read(RelayConditions.BREACHED, facts(1, w)));
    }

    @Test
    @DisplayName("Trouble counts every required source that is not present, breached or impaired")
    void trouble() {
        final HazardWatch w = new HazardWatch();
        assertEquals(Truth.FALSE, read(RelayConditions.TROUBLE, facts(1, w,
                dev(1, SecurityKinds.DOOR, Presence.PRESENT),
                dev(2, SecurityKinds.SENSOR_MOTION, Presence.PRESENT))));
        for (Presence p : new Presence[]{Presence.STALE, Presence.UNLOADED, Presence.UNREACHED, Presence.MISSING}) {
            assertEquals(Truth.TRUE, read(RelayConditions.TROUBLE, facts(1, w,
                    dev(1, SecurityKinds.DOOR, Presence.PRESENT),
                    dev(2, SecurityKinds.SENSOR_MOTION, p))), "a sensor " + p + " is a hole in the house");
        }
        final SecuritySituations.Watched blind = new SecuritySituations.Watched(at(3),
                SecurityKinds.SENSOR_MOTION, Presence.PRESENT, false, true, true);
        assertEquals(Truth.TRUE, read(RelayConditions.TROUBLE, facts(1, w, blind)));
        final SecuritySituations.Watched relay = dev(4, SecurityKinds.RELAY, Presence.MISSING);
        assertEquals(Truth.FALSE, read(RelayConditions.TROUBLE, facts(1, w, relay)),
                "a relay's own fault never feeds the trouble condition");
        assertEquals(Truth.FALSE, read(RelayConditions.TROUBLE, facts(1, w)), "nothing expected, nothing wrong");
    }

    @Test
    @DisplayName("Healthy needs something commissioned and nothing wrong")
    void healthy() {
        final HazardWatch w = new HazardWatch();
        assertEquals(Truth.FALSE, read(RelayConditions.HEALTHY, facts(1, w)), "nothing commissioned is not healthy");
        assertEquals(Truth.TRUE, read(RelayConditions.HEALTHY, facts(1, w,
                dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT))));
        assertEquals(Truth.FALSE, read(RelayConditions.HEALTHY, facts(1, w,
                dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT),
                dev(2, SecurityKinds.SENSOR_FIRE, Presence.UNLOADED))));
    }

    @Test
    @DisplayName("Narrowed to one device: that device's facts only, and a vanished target is trouble")
    void narrowedScope() {
        final HazardWatch w = new HazardWatch();
        w.positive(at(1), HazardWatch.Hazard.FIRE, 100, SWEEP, COOLDOWN);
        w.sweep(at(2), HazardWatch.Hazard.FIRE, 100, true, SWEEP, COOLDOWN);
        final SecuritySituations.Facts f = facts(110, w,
                dev(1, SecurityKinds.SENSOR_FIRE, Presence.PRESENT),
                dev(2, SecurityKinds.SENSOR_FIRE, Presence.PRESENT));
        final RelayConfig workshop = cfg(RelayConditions.FIRE).withScope(at(2));
        assertEquals(Truth.FALSE, SecuritySituations.read(workshop, f).truth(),
                "the workshop relay ignores the fire in the kitchen");
        assertEquals(Truth.TRUE, SecuritySituations.read(cfg(RelayConditions.TROUBLE).withScope(at(9)), f).truth());
        assertEquals(Truth.FALSE, SecuritySituations.read(cfg(RelayConditions.HEALTHY).withScope(at(9)), f).truth());
    }

    @Test
    @DisplayName("Breach is ANY over doors and shutters that can report it")
    void breach() {
        final HazardWatch w = new HazardWatch();
        final SecuritySituations.Watched breached = new SecuritySituations.Watched(at(1), SecurityKinds.DOOR,
                Presence.PRESENT, true, false, true);
        assertEquals(Truth.TRUE, read(RelayConditions.BREACHED, facts(1, w, breached,
                dev(2, SecurityKinds.SHUTTER, Presence.UNLOADED))));
        assertEquals(Truth.UNKNOWN, read(RelayConditions.BREACHED, facts(1, w,
                dev(1, SecurityKinds.DOOR, Presence.PRESENT), dev(2, SecurityKinds.SHUTTER, Presence.UNLOADED))));
        assertEquals(Truth.FALSE, read(RelayConditions.BREACHED, facts(1, w,
                dev(1, SecurityKinds.DOOR, Presence.PRESENT))));
    }

    @Test
    @DisplayName("SEC-12: a door, garage or shutter verified gone is a breach; one merely out of sight is not")
    void goneIsABreach() {
        final HazardWatch w = new HazardWatch();
        assertEquals(Truth.TRUE, read(RelayConditions.BREACHED, facts(1, w,
                dev(1, SecurityKinds.DOOR, Presence.MISSING), dev(2, SecurityKinds.DOOR, Presence.PRESENT))),
                "a door broken out of the wall, with the rest standing");
        assertEquals(Truth.TRUE, read(RelayConditions.BREACHED, facts(1, w,
                dev(1, SecurityKinds.SHUTTER, Presence.MISSING), dev(2, SecurityKinds.DOOR, Presence.UNLOADED))),
                "known TRUE beats an unknown elsewhere");
        for (Presence away : new Presence[]{Presence.STALE, Presence.UNLOADED, Presence.UNREACHED}) {
            assertEquals(Truth.UNKNOWN, read(RelayConditions.BREACHED, facts(1, w,
                    dev(1, SecurityKinds.DOOR, away))), away + " cannot be told from a door still standing");
        }
        assertEquals(Truth.UNKNOWN, read(RelayConditions.BREACHED, facts(1, w,
                dev(1, SecurityKinds.SENSOR_MOTION, Presence.MISSING))),
                "a sensor is not something a breach line watches; with no door at all it knows nothing");
        final RelayConfig onTheDoor = cfg(RelayConditions.BREACHED).withScope(at(1));
        assertEquals(Truth.TRUE, SecuritySituations.read(onTheDoor, facts(1, w,
                dev(1, SecurityKinds.DOOR, Presence.MISSING), dev(2, SecurityKinds.DOOR, Presence.PRESENT))).truth(),
                "narrowed to the broken door, the relay keeps watching it and reads the breach");
    }

    @Test
    @DisplayName("A double door is one scope: narrowed to its anchor, either leaf gone is a breach")
    void doubleDoorIsOneScope() {
        final HazardWatch w = new HazardWatch();
        final SecuritySituations.Watched anchor = dev(1, SecurityKinds.DOOR, Presence.PRESENT);
        final SecuritySituations.Watched otherLeafGone = new SecuritySituations.Watched(at(2), SecurityKinds.DOOR,
                Presence.MISSING, false, false, true, at(1));
        final SecuritySituations.Watched otherLeafStanding = new SecuritySituations.Watched(at(2), SecurityKinds.DOOR,
                Presence.PRESENT, false, false, true, at(1));
        final SecuritySituations.Watched elsewhereGone = dev(3, SecurityKinds.DOOR, Presence.MISSING);
        final RelayConfig onTheDoorway = cfg(RelayConditions.BREACHED).withScope(at(1));
        assertEquals(Truth.TRUE, SecuritySituations.read(onTheDoorway, facts(1, w, anchor, otherLeafGone)).truth(),
                "the leaf that is not the row's anchor was mined: the doorway is open");
        assertEquals(Truth.FALSE, SecuritySituations.read(onTheDoorway,
                facts(1, w, anchor, otherLeafStanding, elsewhereGone)).truth(),
                "both leaves standing, and a broken door elsewhere is not this doorway's");
        assertEquals(Truth.TRUE, read(RelayConditions.BREACHED, facts(1, w, anchor, otherLeafGone)),
                "the whole house sees it too");
        assertEquals(2, SecuritySituations.read(onTheDoorway, facts(1, w, anchor, otherLeafStanding)).sources(),
                "both leaves count as sources");
        assertEquals(at(2), dev(2, SecurityKinds.DOOR, Presence.PRESENT).doorway(),
                "a device that stands for itself is its own doorway");
    }

    @Test
    @DisplayName("Lockdown and mode are the household's own, always known")
    void lockdownAndMode() {
        final SecuritySituations.Facts f = new SecuritySituations.Facts(true, "away", 1, Map.of(), new HazardWatch());
        assertEquals(Truth.TRUE, read(RelayConditions.LOCKDOWN, f));
        assertEquals(Truth.TRUE, SecuritySituations.read(cfg(RelayConditions.MODE).withMode("away"), f).truth());
        assertEquals(Truth.FALSE, SecuritySituations.read(cfg(RelayConditions.MODE).withMode("home"), f).truth());
    }

    @Test
    @DisplayName("Leases come from the reporter: a wider sweep gets a longer lease, never below the release")
    void leases() {
        assertEquals(SecurityHousehold.RELEASE_HOLD, HazardWatch.positiveLease(55, 40),
                "a default fire sweep sits under the release floor");
        assertEquals(165 + 40 + 5, HazardWatch.positiveLease(165, 40),
                "a sixteen block fire sweep outlasts the floor and gets its own lease");
        final HazardWatch w = new HazardWatch();
        w.sweep(at(1), HazardWatch.Hazard.WATER, 100, true, 30, COOLDOWN);
        final long alive = HazardWatch.heartbeatLease(30);
        assertEquals(Truth.FALSE, w.truth(at(1), HazardWatch.Hazard.WATER, 100 + alive));
        assertEquals(Truth.UNKNOWN, w.truth(at(1), HazardWatch.Hazard.WATER, 100 + alive + 1),
                "a clean sweep is only believed while the sensor is alive to sweep again");
    }
}
