package com.createstats.security.relay;

import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityEvent;
import com.createstats.security.Truth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayRulesTest {
    private static final SecurityEndpoint DOOR = SecurityEndpoint.of("minecraft:overworld", 77L);
    private static final SecurityEndpoint OTHER = SecurityEndpoint.of("minecraft:overworld", 78L);

    private static RelayConfig cfg(RelayCondition c, RelayBehavior b) {
        return new RelayConfig(c.id(), b, RelayConfig.DEFAULT_PULSE, "away", null);
    }

    private static SecurityEvent event(long seq, String kind, long at) {
        return new SecurityEvent(seq, kind, DOOR, at);
    }

    private static int out(RelayConfig c, RelayState s, Truth r, long now) {
        return RelayRules.signal(c, s, r, true, RelayContact.LINKED, now, RelayState.NEVER).level();
    }

    @Test
    @DisplayName("A denied attempt pulses for exactly the default ten ticks, from the moment it was accepted")
    void defaultPulseDeadline() {
        final RelayConfig c = cfg(RelayConditions.DENIED, RelayBehavior.PULSE);
        final RelayState s = RelayRules.onEvent(c, RelayState.seed(0), event(1, SecurityEvent.ACCESS_DENIED, 500),
                true);
        assertEquals(510L, s.pulseUntil());
        assertEquals(15, out(c, s, Truth.UNKNOWN, 500));
        assertEquals(15, out(c, s, Truth.UNKNOWN, 509));
        assertEquals(0, out(c, s, Truth.UNKNOWN, 510), "ten ticks, not eleven");
    }

    @Test
    @DisplayName("A distinct event restarts the deadline; the same event delivered twice does not")
    void retriggerAndDuplicates() {
        final RelayConfig c = cfg(RelayConditions.DENIED, RelayBehavior.PULSE);
        RelayState s = RelayRules.onEvent(c, RelayState.seed(0), event(1, SecurityEvent.ACCESS_DENIED, 100), true);
        s = RelayRules.onEvent(c, s, event(2, SecurityEvent.ACCESS_DENIED, 105), true);
        assertEquals(115L, s.pulseUntil(), "a second real attempt restarts the absolute deadline");
        final RelayState again = RelayRules.onEvent(c, s, event(2, SecurityEvent.ACCESS_DENIED, 108), true);
        assertSame(s, again, "the same sequence number is the same event, and changes nothing");
        final RelayState old = RelayRules.onEvent(c, s, event(1, SecurityEvent.ACCESS_DENIED, 109), true);
        assertSame(s, old, "an older event is history");
    }

    @Test
    @DisplayName("Binding and retuning seed the cursor, so nothing that happened before replays")
    void seedingPreventsReplay() {
        final RelayConfig c = cfg(RelayConditions.TAMPER, RelayBehavior.HOLD);
        final RelayState seeded = RelayState.seed(40);
        assertSame(seeded, RelayRules.onEvent(c, seeded, event(40, SecurityEvent.TAMPER_ACCEPTED, 1), true));
        assertSame(seeded, RelayRules.onEvent(c, seeded, event(12, SecurityEvent.TAMPER_ACCEPTED, 1), true));
        assertTrue(RelayRules.onEvent(c, seeded, event(41, SecurityEvent.TAMPER_ACCEPTED, 2), true).pending());
    }

    @Test
    @DisplayName("Events of another kind, or from outside the scope, do nothing")
    void kindAndScope() {
        final RelayConfig c = cfg(RelayConditions.DENIED, RelayBehavior.HOLD);
        final RelayState s = RelayState.seed(0);
        assertSame(s, RelayRules.onEvent(c, s, event(1, SecurityEvent.TAMPER_ACCEPTED, 1), true));
        assertSame(s, RelayRules.onEvent(c, s, event(1, SecurityEvent.ACCESS_DENIED, 1), false));
    }

    @Test
    @DisplayName("A sustained condition pulses on a known rising edge only, never on recovery from unknown")
    void pulseOnKnownRisingEdgeOnly() {
        final RelayConfig c = cfg(RelayConditions.FIRE, RelayBehavior.PULSE);
        RelayState s = RelayState.seed(0);
        s = RelayRules.onReading(c, s, Truth.UNKNOWN, 1);
        s = RelayRules.onReading(c, s, Truth.TRUE, 2);
        assertEquals(RelayState.NEVER, s.pulseUntil(), "unknown to true is a recovery, not a fire starting");
        s = RelayRules.onReading(c, s, Truth.FALSE, 3);
        s = RelayRules.onReading(c, s, Truth.TRUE, 4);
        assertEquals(14L, s.pulseUntil(), "false to true is the edge");
        final RelayState steady = RelayRules.onReading(c, s, Truth.TRUE, 5);
        assertEquals(14L, steady.pulseUntil(), "a fire that keeps burning does not keep pulsing");
        RelayState gap = RelayRules.onReading(c, s, Truth.UNKNOWN, 6);
        gap = RelayRules.onReading(c, gap, Truth.TRUE, 7);
        assertEquals(14L, gap.pulseUntil(), "a gap in observation and back is not a new fire");
    }

    @Test
    @DisplayName("A pulse is never queued: back after the deadline is 0, back before it shows what is left")
    void pulsesAreNotQueued() {
        final RelayConfig c = cfg(RelayConditions.DENIED, RelayBehavior.PULSE);
        final RelayState s = RelayRules.onEvent(c, RelayState.seed(0), event(1, SecurityEvent.ACCESS_DENIED, 1000),
                true);
        assertEquals(15, out(c, s, Truth.UNKNOWN, 1004), "reconnected inside the window: the remainder");
        assertEquals(0, out(c, s, Truth.UNKNOWN, 1011), "reconnected after it: nothing, ever");
    }

    @Test
    @DisplayName("A one-tick pulse noticed a tick late is still shown once, and never twice or later")
    void oneTickGuarantee() {
        final RelayConfig c = new RelayConfig(RelayConditions.DENIED.id(), RelayBehavior.PULSE, 1, "away", null);
        final RelayState s = RelayRules.onEvent(c, RelayState.seed(0), event(1, SecurityEvent.ACCESS_DENIED, 200),
                true);
        final RelayRules.Output late = RelayRules.signal(c, s, Truth.UNKNOWN, true, RelayContact.LINKED, 201,
                RelayState.NEVER);
        assertEquals(15, late.level(), "the relay ticked before the event in tick 200; it shows it in 201");
        final RelayRules.Output after = RelayRules.signal(c, s, Truth.UNKNOWN, true, RelayContact.LINKED, 202,
                late.seenTrigger());
        assertEquals(0, after.level(), "once only");
        assertEquals(0, RelayRules.signal(c, s, Truth.UNKNOWN, true, RelayContact.LINKED, 203,
                RelayState.NEVER).level(), "two ticks late is history, not a pulse");
    }

    @Test
    @DisplayName("A saved deadline further ahead than the longest pulse drives nothing")
    void corruptDeadline() {
        final RelayConfig c = cfg(RelayConditions.DENIED, RelayBehavior.PULSE);
        final RelayState s = new RelayState(1, 1_000_000L, false, Truth.UNKNOWN, RelayState.NEVER);
        assertEquals(0, out(c, s, Truth.UNKNOWN, 10));
    }

    @Test
    @DisplayName("Hold latches on an event and stays after the condition clears")
    void holdLatches() {
        final RelayConfig c = cfg(RelayConditions.FIRE, RelayBehavior.HOLD);
        RelayState s = RelayRules.onReading(c, RelayState.seed(0), Truth.FALSE, 1);
        s = RelayRules.onReading(c, s, Truth.TRUE, 2);
        assertTrue(s.pending());
        s = RelayRules.onReading(c, s, Truth.FALSE, 3);
        assertTrue(s.pending(), "the fire is out; the incident is not dealt with");
        assertEquals(15, out(c, s, Truth.FALSE, 4));
    }

    @Test
    @DisplayName("Hold latches a sustained condition that is already true when the relay binds")
    void holdLatchesOnBinding() {
        final RelayConfig c = cfg(RelayConditions.LOCKDOWN, RelayBehavior.HOLD);
        assertTrue(RelayRules.onReading(c, RelayState.seed(0), Truth.TRUE, 1).pending());
    }

    @Test
    @DisplayName("Out of contact, a relay keeps only the hold it already received")
    void heldThroughOutage() {
        final RelayConfig c = cfg(RelayConditions.TAMPER, RelayBehavior.HOLD);
        final RelayState received = RelayState.seed(0).withPending(true);
        assertEquals(15, RelayRules.signal(c, received, Truth.UNKNOWN, true, RelayContact.UNKNOWN, 1,
                RelayState.NEVER).level());
        assertEquals(15, RelayRules.signal(c, received, Truth.UNKNOWN, true, RelayContact.LOST, 1,
                RelayState.NEVER).level());
        assertEquals(0, RelayRules.signal(c, RelayState.seed(0), Truth.UNKNOWN, true, RelayContact.UNKNOWN, 1,
                RelayState.NEVER).level(), "nothing new reaches it until contact returns");
    }

    @Test
    @DisplayName("Reset: needs rights, contact, a readable condition, and the condition over")
    void resetRules() {
        final RelayConfig fire = cfg(RelayConditions.FIRE, RelayBehavior.HOLD);
        final RelayState held = RelayState.seed(0).withPending(true);
        assertEquals(RelayRules.ResetVerdict.FORBIDDEN, RelayRules.mayReset(fire, held, Truth.FALSE, true, false));
        assertEquals(RelayRules.ResetVerdict.NO_CONTACT, RelayRules.mayReset(fire, held, Truth.FALSE, false, true));
        assertEquals(RelayRules.ResetVerdict.UNREADABLE, RelayRules.mayReset(fire, held, Truth.UNKNOWN, true, true),
                "an unknown fire is not a fire known to be out");
        assertEquals(RelayRules.ResetVerdict.STILL_ACTIVE, RelayRules.mayReset(fire, held, Truth.TRUE, true, true));
        assertEquals(RelayRules.ResetVerdict.RESET, RelayRules.mayReset(fire, held, Truth.FALSE, true, true));
        assertEquals(RelayRules.ResetVerdict.NOTHING_HELD,
                RelayRules.mayReset(fire, RelayState.seed(0), Truth.FALSE, true, true));
        final RelayConfig tamper = cfg(RelayConditions.TAMPER, RelayBehavior.HOLD);
        assertEquals(RelayRules.ResetVerdict.RESET, RelayRules.mayReset(tamper, held, Truth.UNKNOWN, true, true),
                "a point event has no still-happening");
        assertEquals(RelayRules.ResetVerdict.NOT_HOLD,
                RelayRules.mayReset(cfg(RelayConditions.FIRE, RelayBehavior.PULSE), held, Truth.FALSE, true, true));
    }

    @Test
    @DisplayName("Follow: high only for a known TRUE; supervision reads an unreadable house as trouble")
    void follow() {
        final RelayConfig fire = cfg(RelayConditions.FIRE, RelayBehavior.FOLLOW);
        final RelayState s = RelayState.seed(0);
        assertEquals(15, out(fire, s, Truth.TRUE, 1));
        assertEquals(0, out(fire, s, Truth.FALSE, 1));
        assertEquals(0, out(fire, s, Truth.UNKNOWN, 1), "unknown is never a fire, and never a clear either");
        final RelayConfig trouble = cfg(RelayConditions.TROUBLE, RelayBehavior.FOLLOW);
        assertEquals(15, out(trouble, s, Truth.UNKNOWN, 1));
        assertEquals(15, RelayRules.signal(trouble, s, Truth.FALSE, true, RelayContact.LOST, 1,
                RelayState.NEVER).level(), "a powered relay that lost its house says so");
        final RelayConfig healthy = cfg(RelayConditions.HEALTHY, RelayBehavior.FOLLOW);
        assertEquals(15, out(healthy, s, Truth.TRUE, 1));
        assertEquals(0, RelayRules.signal(healthy, s, Truth.TRUE, true, RelayContact.UNKNOWN, 1,
                RelayState.NEVER).level(), "healthy drops the moment contact goes");
    }

    @Test
    @DisplayName("No power, no output, whatever is held")
    void noPowerNoOutput() {
        final RelayConfig c = cfg(RelayConditions.TROUBLE, RelayBehavior.FOLLOW);
        assertEquals(0, RelayRules.signal(c, RelayState.seed(0), Truth.TRUE, false, RelayContact.LINKED, 1,
                RelayState.NEVER).level());
        final RelayConfig hold = cfg(RelayConditions.FIRE, RelayBehavior.HOLD);
        assertEquals(0, RelayRules.signal(hold, RelayState.seed(0).withPending(true), Truth.TRUE, false,
                RelayContact.LINKED, 1, RelayState.NEVER).level());
    }

    @Test
    @DisplayName("An unbound or unaccepted relay drives nothing")
    void unboundDrivesNothing() {
        final RelayConfig c = cfg(RelayConditions.LOCKDOWN, RelayBehavior.FOLLOW);
        assertEquals(0, RelayRules.signal(c, RelayState.seed(0), Truth.TRUE, true, RelayContact.UNBOUND, 1,
                RelayState.NEVER).level());
        assertEquals(0, RelayRules.signal(c, RelayState.seed(0), Truth.TRUE, true, RelayContact.CANDIDATE, 1,
                RelayState.NEVER).level());
    }

    @Test
    @DisplayName("An unknown condition id drives nothing and changes nothing")
    void unknownCondition() {
        final RelayConfig c = new RelayConfig("some_mod:future_thing", RelayBehavior.FOLLOW, 10, "", null);
        final RelayState s = RelayState.seed(0);
        assertEquals(0, out(c, s, Truth.TRUE, 1));
        assertSame(s, RelayRules.onReading(c, s, Truth.TRUE, 1));
    }

    @Test
    @DisplayName("Configure: every refusal the server owes the client, and stale edits never overwrite")
    void configure() {
        final RelayConfig current = RelayConfig.DEFAULT;
        final RelayState quiet = RelayState.seed(0);
        final RelayConfig wanted = cfg(RelayConditions.FIRE, RelayBehavior.HOLD);
        assertEquals(RelayRules.ConfigVerdict.APPLIED,
                RelayRules.mayConfigure(current, quiet, 3, wanted, 3, true, d -> true, m -> true));
        assertEquals(RelayRules.ConfigVerdict.FORBIDDEN,
                RelayRules.mayConfigure(current, quiet, 3, wanted, 3, false, d -> true, m -> true));
        assertEquals(RelayRules.ConfigVerdict.STALE,
                RelayRules.mayConfigure(current, quiet, 4, wanted, 3, true, d -> true, m -> true),
                "another panel's completed edit must not be overwritten by a screen that never saw it");
        assertEquals(RelayRules.ConfigVerdict.BEHAVIOR_NOT_OFFERED, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.DENIED, RelayBehavior.FOLLOW), 0, true, d -> true, m -> true));
        assertEquals(RelayRules.ConfigVerdict.BEHAVIOR_NOT_OFFERED, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.HEALTHY, RelayBehavior.HOLD), 0, true, d -> true, m -> true),
                "supervision is Follow only in V1");
        assertEquals(RelayRules.ConfigVerdict.PULSE_OUT_OF_RANGE, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.DENIED, RelayBehavior.PULSE).withPulse(0), 0, true, d -> true, m -> true));
        assertEquals(RelayRules.ConfigVerdict.PULSE_OUT_OF_RANGE, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.DENIED, RelayBehavior.PULSE).withPulse(201), 0, true, d -> true, m -> true));
        assertEquals(RelayRules.ConfigVerdict.APPLIED, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.DENIED, RelayBehavior.PULSE).withPulse(200), 0, true, d -> true, m -> true));
        assertEquals(RelayRules.ConfigVerdict.MODE_REQUIRED, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.MODE, RelayBehavior.FOLLOW).withMode("upside_down"), 0, true, d -> true,
                m -> false));
        assertEquals(RelayRules.ConfigVerdict.SCOPE_NOT_OFFERED, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.LOCKDOWN, RelayBehavior.FOLLOW).withScope(DOOR), 0, true, d -> true, m -> true),
                "the lockdown is the whole house's");
        assertEquals(RelayRules.ConfigVerdict.SCOPE_NOT_OFFERED, RelayRules.mayConfigure(current, quiet, 0,
                cfg(RelayConditions.FIRE, RelayBehavior.FOLLOW).withScope(OTHER), 0, true, d -> !d.equals(OTHER),
                m -> true));
        assertEquals(RelayRules.ConfigVerdict.UNCHANGED,
                RelayRules.mayConfigure(current, quiet, 0, current, 0, true, d -> true, m -> true),
                "a repeated identical edit is a no-op, not a second audit line");
        assertEquals(RelayRules.ConfigVerdict.PENDING_INCIDENT, RelayRules.mayConfigure(current,
                quiet.withPending(true), 0, wanted, 0, true, d -> true, m -> true),
                "retuning must never be how an unresolved incident disappears");
    }

    @Test
    @DisplayName("Retire: members never; an unresolved channel only by the owner")
    void retire() {
        final RelayState held = RelayState.seed(0).withPending(true);
        assertEquals(RelayRules.RetireVerdict.FORBIDDEN, RelayRules.mayRetire(held, false, false));
        assertEquals(RelayRules.RetireVerdict.OWNER_ONLY_WHILE_HELD, RelayRules.mayRetire(held, true, false));
        assertEquals(RelayRules.RetireVerdict.RETIRED, RelayRules.mayRetire(held, true, true));
        assertEquals(RelayRules.RetireVerdict.RETIRED, RelayRules.mayRetire(RelayState.seed(0), true, false));
    }

    @Test
    @DisplayName("Only saved fields count as a change worth a save")
    void savedFields() {
        final RelayState a = RelayState.seed(3);
        assertFalse(RelayRules.savedFieldsDiffer(a, a.forgettingBaseline()));
        assertTrue(RelayRules.savedFieldsDiffer(a, a.withPending(true)));
    }
}
