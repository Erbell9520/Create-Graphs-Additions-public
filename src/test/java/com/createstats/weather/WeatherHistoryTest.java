package com.createstats.weather;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherHistoryTest {
    @Test
    void readsBackChronologicallyBeforeItWraps() {
        final WeatherHistory h = new WeatherHistory();
        for (int i = 0; i < 10; i++) {
            h.record(i * 10, 50, 1000 + i, Condition.CLEAR);
        }
        assertEquals(10, h.size());
        final int[] temps = h.temperatureTenths();
        assertEquals(0, temps[0]);
        assertEquals(90, temps[9]);
    }

    @Test
    void readsBackChronologicallyAfterItWraps() {
        final WeatherHistory h = new WeatherHistory();
        for (int i = 0; i < WeatherHistory.SIZE + 7; i++) {
            h.record(i, 50, 1000, Condition.CLEAR);
        }
        assertEquals(WeatherHistory.SIZE, h.size());
        final int[] temps = h.temperatureTenths();
        assertEquals(7, temps[0], "oldest surviving sample");
        assertEquals(WeatherHistory.SIZE + 6, temps[WeatherHistory.SIZE - 1], "newest");
    }

    @Test
    void negativeTemperaturesSurviveThePacking() {
        final WeatherHistory h = new WeatherHistory();
        h.record(-185, 80, 998, Condition.SNOW);
        assertEquals(-185, h.temperatureTenths()[0]);
        assertEquals(998, h.pressures()[0]);
        assertEquals(80, h.humidities()[0]);
        assertEquals(Condition.SNOW, h.conditions()[0]);
    }

    @Test
    void tendencyLooksExactlyOneHourBack() {
        final WeatherHistory h = new WeatherHistory();
        for (int i = 0; i < 20; i++) {
            h.record(0, 50, 1000 + i, Condition.CLEAR);
        }
        assertEquals(1014.0f, h.pressureOneHourAgo(0f), 0.001f);
    }

    @Test
    void tendencyFallsBackWhenThereIsNotAnHourOfHistory() {
        final WeatherHistory h = new WeatherHistory();
        h.record(0, 50, 1013, Condition.CLEAR);
        assertEquals(999.0f, h.pressureOneHourAgo(999f), 0.001f);
    }

    @Test
    void survivesASaveAndLoadRoundTrip() {
        final WeatherHistory h = new WeatherHistory();
        for (int i = 0; i < 40; i++) {
            h.record(i * 3 - 60, i % 100, 1000 + i, Condition.values()[i % Condition.values().length]);
        }
        final WeatherHistory back = WeatherHistory.load(h.save());
        assertEquals(h.size(), back.size());
        assertEquals(h.temperatureTenths()[0], back.temperatureTenths()[0]);
        assertEquals(h.temperatureTenths()[39], back.temperatureTenths()[39]);
        assertEquals(h.pressures()[39], back.pressures()[39]);
        assertEquals(h.humidities()[17], back.humidities()[17]);
        assertEquals(h.conditions()[17], back.conditions()[17]);
    }

    @Test
    void loadingRubbishGivesAnEmptyRingRatherThanACrash() {
        assertEquals(0, WeatherHistory.load(null).size());
        assertEquals(0, WeatherHistory.load(new int[3]).size());
    }
}
