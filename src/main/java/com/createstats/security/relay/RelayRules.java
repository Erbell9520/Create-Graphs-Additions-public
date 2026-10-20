package com.createstats.security.relay;

import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityEvent;
import com.createstats.security.Truth;

import java.util.function.Predicate;

// household half runs without the relay loaded, block half turns state into a signal
public final class RelayRules {
    private RelayRules() {
    }

    public static final int ON = 15;
    public static final int OFF = 0;

    public static RelayState onEvent(RelayConfig config, RelayState state, SecurityEvent event,
                                     boolean inScope) {
        final RelayCondition c = config.condition();
        if (c == null || !c.isPoint() || !c.eventKind().equals(event.kind()) || !inScope) {
            return state;
        }

        if (event.sequence() <= state.lastEventSequence()) {
            return state;
        }
        long until = state.pulseUntil();
        boolean pending = state.pending();
        switch (config.behavior()) {
            case PULSE -> until = event.acceptedAt() + config.pulseTicks();
            case HOLD -> pending = true;
            case FOLLOW -> {
            }
        }
        return new RelayState(event.sequence(), until, pending, state.baseline(), event.acceptedAt());
    }

    public static RelayState onReading(RelayConfig config, RelayState state, Truth reading, long now) {
        final RelayCondition c = config.condition();
        if (c == null || c.isPoint()) {
            return state;
        }
        final boolean rising = reading == Truth.TRUE && state.baseline() == Truth.FALSE;
        long until = state.pulseUntil();
        boolean pending = state.pending();
        long trigger = state.lastTriggerAt();
        switch (config.behavior()) {
            case PULSE -> {
                if (rising) {
                    until = now + config.pulseTicks();
                    trigger = now;
                }
            }
            case HOLD -> {
                if (reading == Truth.TRUE && !pending) {
                    pending = true;
                    trigger = now;
                }
            }
            case FOLLOW -> {
                if (rising) {
                    trigger = now;
                }
            }
        }
        if (until == state.pulseUntil() && pending == state.pending() && trigger == state.lastTriggerAt()
                && reading == state.baseline()) {
            return state;
        }
        return new RelayState(state.lastEventSequence(), until, pending, reading, trigger);
    }

    public static boolean savedFieldsDiffer(RelayState a, RelayState b) {
        return a.lastEventSequence() != b.lastEventSequence() || a.pulseUntil() != b.pulseUntil()
                || a.pending() != b.pending() || a.lastTriggerAt() != b.lastTriggerAt();
    }

    public record Output(int level, long seenTrigger) {
    }

    public static Output signal(RelayConfig config, RelayState state, Truth reading, boolean powered,
                                RelayContact contact, long now, long seenTrigger) {
        final RelayCondition c = config.condition();
        if (!powered || c == null || !contact.bound()) {
            return new Output(OFF, seenTrigger);
        }
        final boolean linked = contact == RelayContact.LINKED;
        return switch (config.behavior()) {
            case FOLLOW -> new Output(follow(c, linked ? reading : Truth.UNKNOWN), seenTrigger);
            case HOLD -> new Output(state.pending() ? ON : OFF, seenTrigger);
            case PULSE -> pulse(state, now, seenTrigger);
        };
    }

    private static int follow(RelayCondition c, Truth reading) {
        if (c.isPoint()) {
            return OFF;
        }
        if (c == RelayConditions.TROUBLE) {
            return reading == Truth.FALSE ? OFF : ON;
        }
        return reading == Truth.TRUE ? ON : OFF;
    }

    private static Output pulse(RelayState state, long now, long seenTrigger) {
        final long until = state.pulseUntil();
        final boolean live = until != RelayState.NEVER && now < until
                && until - now <= RelayConfig.MAX_PULSE;
        final long trigger = state.lastTriggerAt();
        final boolean unseen = trigger != RelayState.NEVER && trigger != seenTrigger
                && now >= trigger && now - trigger <= 1;
        if (live || unseen) {
            return new Output(ON, trigger == RelayState.NEVER ? seenTrigger : trigger);
        }
        return new Output(OFF, seenTrigger);
    }

    public enum ConfigVerdict {
        APPLIED,
        UNCHANGED,
        FORBIDDEN,
        STALE,
        UNKNOWN_CONDITION,
        BEHAVIOR_NOT_OFFERED,
        PULSE_OUT_OF_RANGE,
        MODE_REQUIRED,
        SCOPE_NOT_OFFERED,
        PENDING_INCIDENT;

        public boolean applied() {
            return this == APPLIED;
        }
    }

    public static ConfigVerdict mayConfigure(RelayConfig current, RelayState state, long revision,
                                             RelayConfig wanted, long expectedRevision, boolean mayEdit,
                                             Predicate<SecurityEndpoint> scopeOk, Predicate<String> modeOk) {
        if (!mayEdit) {
            return ConfigVerdict.FORBIDDEN;
        }
        if (expectedRevision != revision) {
            return ConfigVerdict.STALE;
        }
        final RelayCondition c = wanted.condition();
        if (c == null) {
            return ConfigVerdict.UNKNOWN_CONDITION;
        }
        if (!c.offers(wanted.behavior())) {
            return ConfigVerdict.BEHAVIOR_NOT_OFFERED;
        }
        if (!RelayConfig.validPulse(wanted.pulseTicks())) {
            return ConfigVerdict.PULSE_OUT_OF_RANGE;
        }
        if (c.needsMode() && !modeOk.test(wanted.mode())) {
            return ConfigVerdict.MODE_REQUIRED;
        }
        if (wanted.scope() != null && (!c.narrowable() || !scopeOk.test(wanted.scope()))) {
            return ConfigVerdict.SCOPE_NOT_OFFERED;
        }
        if (wanted.equals(current)) {
            return ConfigVerdict.UNCHANGED;
        }
        if (state.pending()) {
            return ConfigVerdict.PENDING_INCIDENT;
        }
        return ConfigVerdict.APPLIED;
    }

    public enum ResetVerdict {
        RESET,
        FORBIDDEN,
        NOT_HOLD,
        NOTHING_HELD,
        NO_CONTACT,
        UNSUPPORTED,
        UNREADABLE,
        STILL_ACTIVE;

        public boolean done() {
            return this == RESET;
        }
    }

    public static ResetVerdict mayReset(RelayConfig config, RelayState state, Truth reading,
                                        boolean contactLinked, boolean mayEdit) {
        if (!mayEdit) {
            return ResetVerdict.FORBIDDEN;
        }
        if (config.behavior() != RelayBehavior.HOLD) {
            return ResetVerdict.NOT_HOLD;
        }
        if (!state.pending()) {
            return ResetVerdict.NOTHING_HELD;
        }
        if (!contactLinked) {
            return ResetVerdict.NO_CONTACT;
        }
        final RelayCondition c = config.condition();
        if (c == null) {
            return ResetVerdict.UNSUPPORTED;
        }
        if (!c.isPoint()) {
            if (!reading.isKnown()) {
                return ResetVerdict.UNREADABLE;
            }
            if (reading == Truth.TRUE) {
                return ResetVerdict.STILL_ACTIVE;
            }
        }
        return ResetVerdict.RESET;
    }

    public enum RetireVerdict {
        RETIRED,
        FORBIDDEN,
        OWNER_ONLY_WHILE_HELD
    }

    public static RetireVerdict mayRetire(RelayState state, boolean mayEdit, boolean owner) {
        if (!mayEdit) {
            return RetireVerdict.FORBIDDEN;
        }
        if (state.pending() && !owner) {
            return RetireVerdict.OWNER_ONLY_WHILE_HELD;
        }
        return RetireVerdict.RETIRED;
    }
}
