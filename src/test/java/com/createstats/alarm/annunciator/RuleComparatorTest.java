package com.createstats.alarm.annunciator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleComparatorTest {
    @Test
    @DisplayName("holds is never stricter than trips, or an alarm could clear while still true")
    void holdsIsAlwaysAtLeastAsPermissiveAsTrips() {
        for (RuleComparator comparator : RuleComparator.values()) {
            for (float deadband : new float[]{0.0f, 0.5f, 5.0f}) {
                for (float value = -20.0f; value <= 20.0f; value += 0.25f) {
                    if (comparator.trips(value, 10.0f, deadband)) {
                        assertTrue(comparator.holds(value, 10.0f, deadband),
                                comparator + " tripped at " + value + " but would not hold there");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a value resting on the threshold stops chattering once a deadband is set")
    void deadbandStopsTheChatter() {
        final RuleComparator above = RuleComparator.ABOVE;
        assertTrue(above.trips(90.0f, 90.0f, 2.0f), "fires exactly at the threshold the player set");
        assertTrue(above.holds(89.0f, 90.0f, 2.0f), "still alarming just under, inside the deadband");
        assertFalse(above.holds(87.9f, 90.0f, 2.0f), "clears once genuinely back down");
        assertFalse(above.trips(89.0f, 90.0f, 2.0f), "and would not have started at that value");
    }

    @Test
    @DisplayName("the deadband delays the clear and never the trip")
    void deadbandNeverDelaysTheTrip() {
        for (float deadband : new float[]{0.0f, 1.0f, 50.0f}) {
            assertTrue(RuleComparator.ABOVE.trips(90.0f, 90.0f, deadband));
            assertTrue(RuleComparator.BELOW.trips(10.0f, 10.0f, deadband));
        }
    }

    @Test
    @DisplayName("below mirrors above")
    void belowMirrorsAbove() {
        final RuleComparator below = RuleComparator.BELOW;
        assertTrue(below.trips(20.0f, 20.0f, 5.0f));
        assertTrue(below.holds(24.0f, 20.0f, 5.0f), "inside the deadband on the way back up");
        assertFalse(below.holds(26.0f, 20.0f, 5.0f));
    }

    @Test
    @DisplayName("equals treats the deadband as the tolerance, because floats are never equal")
    void equalsUsesTheDeadbandAsTolerance() {
        final RuleComparator equals = RuleComparator.EQUALS;
        assertTrue(equals.trips(15.2f, 15.0f, 0.5f));
        assertFalse(equals.trips(16.0f, 15.0f, 0.5f));
        assertTrue(equals.holds(15.9f, 15.0f, 0.5f), "clears at twice the tolerance, not at it");
        assertFalse(equals.holds(16.5f, 15.0f, 0.5f));
    }

    @Test
    @DisplayName("equals with no deadband still matches an exact value")
    void equalsHasAnEpsilonFloor() {
        assertTrue(RuleComparator.EQUALS.trips(7.0f, 7.0f, 0.0f));
        assertFalse(RuleComparator.EQUALS.trips(8.0f, 7.0f, 0.0f));
    }
}
