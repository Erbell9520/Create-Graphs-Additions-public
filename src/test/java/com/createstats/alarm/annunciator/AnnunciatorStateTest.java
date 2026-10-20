package com.createstats.alarm.annunciator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnunciatorStateTest {
    private static AnnunciatorState step(AnnunciatorState from, boolean condition, boolean ack, boolean reset) {
        return AnnunciatorState.advance(from, condition, true, ack, reset);
    }

    @Test
    @DisplayName("the ordinary life of an alarm: trip, acknowledge, recover")
    void theHappyPath() {
        AnnunciatorState state = AnnunciatorState.NORMAL;
        state = step(state, true, false, false);
        assertEquals(AnnunciatorState.ALARM, state, "trips the moment the condition is true");
        assertTrue(state.hornSounding());
        assertEquals(LampPattern.FAST_FLASH, state.lamp());

        state = step(state, true, true, false);
        assertEquals(AnnunciatorState.ACKNOWLEDGED, state, "ACK silences it without clearing it");
        assertFalse(state.hornSounding(), "acknowledging is exactly the act of stopping the noise");
        assertEquals(LampPattern.STEADY, state.lamp());
        assertTrue(state.isActive(), "the condition is still true, so redstone stays up");

        state = step(state, false, false, false);
        assertEquals(AnnunciatorState.NORMAL, state, "an acknowledged alarm that recovers just ends");
    }

    @Test
    @DisplayName("an alarm that comes and goes unseen leaves a ringback behind")
    void ringbackRecordsWhatHappenedWhileYouWereOut() {
        AnnunciatorState state = step(AnnunciatorState.NORMAL, true, false, false);
        state = step(state, false, false, false);
        assertEquals(AnnunciatorState.RINGBACK, state);
        assertEquals(LampPattern.SLOW_FLASH, state.lamp());
        assertFalse(state.hornSounding(), "the ringback lamp persists, the noise does not");
        assertTrue(state.isOutstanding(), "it still wants a player");
        assertFalse(state.isActive(), "but the condition is over, so redstone drops");
    }

    @Test
    @DisplayName("a ringback clears by hand, and only by hand")
    void ringbackNeedsAPerson() {
        AnnunciatorState state = AnnunciatorState.RINGBACK;
        assertEquals(AnnunciatorState.RINGBACK, step(state, false, false, false),
                "it must not time out on its own, or the record would be worthless");
        assertEquals(AnnunciatorState.NORMAL, step(state, false, true, false));
        assertEquals(AnnunciatorState.NORMAL, step(state, false, false, true));
    }

    @Test
    @DisplayName("the same fault happening again is news, not a continuation")
    void ringbackRetripsWithTheHorn() {
        assertEquals(AnnunciatorState.ALARM, step(AnnunciatorState.RINGBACK, true, false, false));
    }

    @Test
    @DisplayName("RESET refuses to clear something that is still going wrong")
    void resetCannotBeLiedTo() {
        assertEquals(AnnunciatorState.ALARM, step(AnnunciatorState.ALARM, true, false, true));
        assertEquals(AnnunciatorState.ACKNOWLEDGED, step(AnnunciatorState.ACKNOWLEDGED, true, false, true));
        assertEquals(AnnunciatorState.NORMAL, step(AnnunciatorState.ALARM, false, false, true));
    }

    @Test
    @DisplayName("a non-latching rule skips ringback entirely")
    void nonLatchingRulesJustStop() {
        final AnnunciatorState alarm = AnnunciatorState.advance(
                AnnunciatorState.NORMAL, true, false, false, false);
        assertEquals(AnnunciatorState.ALARM, alarm);
        assertEquals(AnnunciatorState.NORMAL,
                AnnunciatorState.advance(alarm, false, false, false, false),
                "no record kept, which is the point of switching latching off");
    }

    @Test
    @DisplayName("acknowledging an alarm as it clears dismisses it rather than banking a ringback")
    void ackDuringRecoveryDismisses() {
        assertEquals(AnnunciatorState.NORMAL,
                AnnunciatorState.advance(AnnunciatorState.ALARM, false, true, true, false));
    }

    @Test
    @DisplayName("every state answers every input, so no rule can land somewhere undrawable")
    void theTableIsTotal() {
        for (AnnunciatorState from : AnnunciatorState.values()) {
            for (boolean condition : new boolean[]{false, true}) {
                for (boolean latching : new boolean[]{false, true}) {
                    for (boolean ack : new boolean[]{false, true}) {
                        for (boolean reset : new boolean[]{false, true}) {
                            final AnnunciatorState to = AnnunciatorState.advance(
                                    from, condition, latching, ack, reset);
                            assertNotNull(to);
                            assertNotNull(to.lamp());
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("only an unacknowledged alarm sounds the horn")
    void exactlyOneStateIsNoisy() {
        for (AnnunciatorState state : AnnunciatorState.values()) {
            assertEquals(state == AnnunciatorState.ALARM, state.hornSounding(), state.name());
        }
    }
}
