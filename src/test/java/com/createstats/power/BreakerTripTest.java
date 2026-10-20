package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Main Breaker thermal trip")
class BreakerTripTest {
    private static final int TRIP = 100;

    private static int ticksToTrip(float overload) {
        final BreakerTrip trip = new BreakerTrip();
        for (int tick = 1; tick <= TRIP * 1000; tick++) {
            if (trip.note(overload, TRIP)) {
                return tick;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("twice the rating trips after exactly the configured time, and not before")
    void doubleTheRatingIsTheAnchor() {
        assertEquals(TRIP, ticksToTrip(2.0f),
                "2x is the one figure the config comment quotes and the curve is normalised on");
    }

    @Test
    @DisplayName("the harder it is pushed the sooner it goes, and every step of that is monotonic")
    void severitySetsTheTime() {
        final float[] over = {1.1f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f, 7.9f};
        int previous = Integer.MAX_VALUE;
        for (float multiple : over) {
            final int ticks = ticksToTrip(multiple);
            assertTrue(ticks > 0, multiple + "x never tripped at all");
            assertTrue(ticks < previous,
                    multiple + "x took " + ticks + " ticks, no sooner than the lighter overload "
                            + "before it (" + previous + "): the whole point of the curve is that "
                            + "how far over it is decides how long there is");
            previous = ticks;
        }
        assertTrue(ticksToTrip(3.0f) * 3 < TRIP * 2,
                "three times the rating should go in well under half the time twice it does");
    }

    @Test
    @DisplayName("a dead short opens the contacts on the tick it happens, with no heating at all")
    void magneticTripIsInstant() {
        final BreakerTrip trip = new BreakerTrip();
        assertTrue(trip.note(BreakerTrip.MAGNETIC_MULTIPLE, TRIP),
                "the magnetic trip is the only path that fires without waiting");
        assertEquals(0.0f, trip.heatUnits(), 1.0e-6f, "a magnetic trip leaves no heat behind");

        final BreakerTrip disabled = new BreakerTrip();
        assertFalse(disabled.note(BreakerTrip.MAGNETIC_MULTIPLE * 4.0f, 0),
                "a pack that turned tripping off turned all of it off, magnetic included");
    }

    @Test
    @DisplayName("a quiet circuit never trips however long it runs")
    void quietNeverTrips() {
        final BreakerTrip trip = new BreakerTrip();
        for (int tick = 0; tick < TRIP * 20; tick++) {
            assertFalse(trip.note(0.0f, TRIP));
            assertFalse(trip.note(1.0f, TRIP), "exactly at the rating is not over it");
        }
        assertEquals(0.0f, trip.heatUnits(), 1.0e-6f);
    }

    @Test
    @DisplayName("2x at half duty sheds heat as fast as it makes it, so it never trips")
    void halfDutyCycleAtDoubleNeverTrips() {
        final BreakerTrip trip = new BreakerTrip();
        for (int tick = 0; tick < TRIP * 50; tick++) {
            assertFalse(trip.note(tick % 2 == 0 ? 2.0f : 0.0f, TRIP),
                    "a 50% duty cycle at 2x tripped, which makes any spiky load a trip");
        }
        assertTrue(trip.heatUnits() <= 1.0f, "heat crept up on a balanced duty cycle");
    }

    @Test
    @DisplayName("the same half duty cycle at 3x does trip, because severity outruns the cooling")
    void halfDutyCycleAtTripleTrips() {
        final BreakerTrip trip = new BreakerTrip();
        int ticks = 0;
        while (ticks < TRIP * 100) {
            ticks++;
            if (trip.note(ticks % 2 == 0 ? 3.0f : 0.0f, TRIP)) {
                break;
            }
        }
        assertTrue(ticks < TRIP * 100, "a half duty cycle at three times the rating never tripped");
        assertTrue(ticks > TRIP, "it tripped as fast as a solid overload, so cooling did nothing");
    }

    @Test
    @DisplayName("three ticks in four over the rating still trips, just later than a solid one")
    void heavyDutyCycleTripsLate() {
        final BreakerTrip trip = new BreakerTrip();
        int ticks = 0;
        while (ticks < TRIP * 100) {
            ticks++;
            if (trip.note(ticks % 4 != 0 ? 2.0f : 0.0f, TRIP)) {
                break;
            }
        }
        assertTrue(ticks > TRIP, "a partial overload tripped as fast as a solid one");
        assertTrue(ticks < TRIP * 100, "a three-in-four overload never tripped at all");
    }

    @Test
    @DisplayName("the element is cool again after it lets go, so a reset is not an instant re-trip")
    void trippingClearsTheHeat() {
        final BreakerTrip trip = new BreakerTrip();
        while (!trip.note(2.0f, TRIP)) {
        }
        assertEquals(0.0f, trip.heatUnits(), 1.0e-6f,
                "a tripped breaker still holding its heat would trip again on the first busy tick "
                        + "after a reset, and read as one that cannot be reset at all");
    }

    @Test
    @DisplayName("a trip time of zero disables tripping and holds no heat")
    void zeroDisablesTripping() {
        final BreakerTrip trip = new BreakerTrip();
        for (int tick = 0; tick < 10_000; tick++) {
            assertFalse(trip.note(4.0f, 0));
        }
        assertEquals(0.0f, trip.heatUnits(), 1.0e-6f,
                "turning tripping back on mid-session must not find an hour of stored heat");
    }

    @Test
    @DisplayName("the pilot's warning rises with the heat and reaches full only at the trip")
    void heatFractionTracksTheElement() {
        final BreakerTrip trip = new BreakerTrip();
        assertEquals(0.0f, trip.heat(TRIP), 1.0e-6f);
        for (int tick = 0; tick < TRIP / 2; tick++) {
            trip.note(2.0f, TRIP);
        }
        assertEquals(0.5f, trip.heat(TRIP), 0.02f, "the pilot must be halfway at half the heat");
        assertEquals(0.0f, trip.heat(0), 1.0e-6f, "no warning is due when nothing can trip");
    }

    @Test
    @DisplayName("the countdown the readout prints is what the element actually does")
    void countdownMatchesTheElement() {
        for (float multiple : new float[] {1.2f, 1.5f, 2.0f, 4.0f, 6.0f}) {
            final int predicted = BreakerTrip.ticksToTrip(multiple, TRIP, 0.0f);
            final int actual = ticksToTrip(multiple);

            assertTrue(Math.abs(predicted - actual) <= 1,
                    "the readout promised " + predicted + " ticks at " + multiple
                            + "x and the element took " + actual + ": that is not rounding, it is "
                            + "two different curves");
        }
        assertEquals(-1, BreakerTrip.ticksToTrip(1.0f, TRIP, 0.0f),
                "a breaker at or under its rating is never counting down to anything");
        assertEquals(0, BreakerTrip.ticksToTrip(BreakerTrip.MAGNETIC_MULTIPLE, TRIP, 0.0f));
        assertEquals(-1, BreakerTrip.ticksToTrip(4.0f, 0, 0.0f), "nothing to count down to");
    }

    @Test
    @DisplayName("a partly heated element counts down from where it is, not from cold")
    void countdownAllowsForStoredHeat() {
        final int cold = BreakerTrip.ticksToTrip(2.0f, TRIP, 0.0f);
        final int warm = BreakerTrip.ticksToTrip(2.0f, TRIP, TRIP * 0.75f);
        assertTrue(warm < cold, "an element three quarters full has less time left than a cold one");
        assertEquals(TRIP / 4, warm, 1);
    }

    @Test
    @DisplayName("reset and the saved figure round trip, so a chunk reload is not a free reset")
    void heatSurvivesARoundTrip() {
        final BreakerTrip trip = new BreakerTrip();
        for (int tick = 0; tick < 40; tick++) {
            trip.note(2.0f, TRIP);
        }
        final float saved = trip.heatUnits();
        assertEquals(40.0f, saved, 1.0e-3f);

        final BreakerTrip loaded = new BreakerTrip();
        loaded.setHeatUnits(saved);
        assertEquals(saved, loaded.heatUnits(), 1.0e-6f);
        loaded.setHeatUnits(-5.0f);
        assertEquals(0.0f, loaded.heatUnits(), 1.0e-6f,
                "a corrupt saved figure must clamp, not go negative");
        loaded.setHeatUnits(Float.NaN);
        assertEquals(0.0f, loaded.heatUnits(), 1.0e-6f, "and NaN must not poison the element");

        trip.reset();
        assertEquals(0.0f, trip.heatUnits(), 1.0e-6f);
    }

    @Test
    @DisplayName("the rise curve is the square law, clamped, and flat at or below the rating")
    void riseCurveIsTheSquareLaw() {
        assertEquals(0.0f, BreakerTrip.riseAt(0.0f), 1.0e-6f);
        assertEquals(0.0f, BreakerTrip.riseAt(1.0f), 1.0e-6f);
        assertEquals(1.0f, BreakerTrip.riseAt(2.0f), 1.0e-4f, "2x must rise exactly one a tick");
        assertEquals((2.25f - 1.0f) / 3.0f, BreakerTrip.riseAt(1.5f), 1.0e-4f);
        assertEquals((9.0f - 1.0f) / 3.0f, BreakerTrip.riseAt(3.0f), 1.0e-4f);
        assertTrue(BreakerTrip.riseAt(1000.0f) <= 64.0f,
                "an absurd figure must clamp rather than overflow the element");
    }
}
