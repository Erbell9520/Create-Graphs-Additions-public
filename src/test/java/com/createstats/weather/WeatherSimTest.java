package com.createstats.weather;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherSimTest {
    @Test
    void fastPathMatchesTickByTick() {
        SimState slow = new SimState(0, 4000, 8000, false, false);
        SimState fast = slow;
        for (int chunk = 0; chunk < 50; chunk++) {
            RandomGenerator a = new Random(1234L + chunk);
            RandomGenerator b = new Random(1234L + chunk);
            for (int i = 0; i < 4000; i++) {
                slow = WeatherSim.step(slow, a);
            }
            fast = WeatherSim.advanceTo(fast, 4000, b);
            assertEquals(slow, fast, "diverged in chunk " + chunk);
        }
    }

    @Test
    void forcedClearPinsBothTimers() {
        SimState s = new SimState(100, 500, 500, false, true);
        RandomGenerator rng = new Random(7L);
        s = WeatherSim.step(s, rng);
        s = WeatherSim.step(s, rng);
        assertEquals(1, s.rainTime());
        assertEquals(1, s.thunderTime());
        assertFalse(s.raining());
        assertFalse(s.thundering());
    }

    @Test
    void forcedClearEndsInAStorm() {
        SimState s = new SimState(20, 0, 0, false, false);
        RandomGenerator rng = new Random(7L);
        for (int i = 0; i < 20; i++) {
            s = WeatherSim.step(s, rng);
        }
        assertEquals(0, s.clearWeatherTime());
        s = WeatherSim.step(s, rng);
        assertTrue(s.raining(), "rain should start on the tick after a forced clear expires");
        assertTrue(s.thundering(), "and thunder with it, which is what makes it a storm");
        assertTrue(WeatherSim.isStorm(s));
    }

    @Test
    void weatherRainCommandProducesDryThunder() {
        SimState s = new SimState(0, 600, 600, false, true);
        RandomGenerator rng = new Random(7L);
        for (int i = 0; i < 600; i++) {
            s = WeatherSim.step(s, rng);
        }
        assertFalse(s.raining(), "the rain ended");
        assertTrue(s.thundering(), "and thunder started with nothing falling, which renders nothing");
        assertFalse(WeatherSim.isWet(s), "so the panel must not call this a storm");
    }

    @Test
    void rolledDurationsStayInVanillaBounds() {
        SimState s = new SimState(0, 1, 1, false, false);
        RandomGenerator rng = new Random(99L);
        for (int i = 0; i < 200000; i++) {
            s = WeatherSim.step(s, rng);
            assertTrue(s.rainTime() >= 0 && s.rainTime() <= 180000, "rainTime " + s.rainTime());
            assertTrue(s.thunderTime() >= 0 && s.thunderTime() <= 180000, "thunderTime " + s.thunderTime());
        }
    }
}
