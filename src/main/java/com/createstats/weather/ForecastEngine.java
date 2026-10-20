package com.createstats.weather;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

// monte carlo over vanilla's own weather machine, not invented weights
public final class ForecastEngine {
    public static final int PATHS = 256;
    public static final int DAY_TICKS = 24000;
    public static final int DAYS = 5;
    public static final int HORIZON_TICKS = DAY_TICKS * DAYS;

    public static final int STRIP_TICKS = 12000;

    public static final int PROBE_TICKS = 1000;

    private static final int PROBES = HORIZON_TICKS / PROBE_TICKS;
    private static final int STRIP_PROBES = STRIP_TICKS / PROBE_TICKS;

    private static final long DAY_SCRAMBLE = 0x9E3779B97F4A7C15L;

    private ForecastEngine() {
    }

    public static Forecast compute(WeatherSnapshot s) {
        if (!s.hasSkyLight() || !s.weatherCycle()) {
            return Forecast.none();
        }

        final boolean[][] wet = new boolean[PATHS][PROBES];
        final boolean[][] storm = new boolean[PATHS][PROBES];

        for (int p = 0; p < PATHS; p++) {
            final RandomGenerator rng = new Random(s.worldSeed() ^ (s.dayIndex() * DAY_SCRAMBLE) ^ p);
            SimState state = s.sim();
            for (int t = 0; t < PROBES; t++) {
                state = WeatherSim.advanceTo(state, PROBE_TICKS, rng);
                wet[p][t] = WeatherSim.isWet(state);
                storm[p][t] = WeatherSim.isStorm(state);
            }
        }

        final int certainUntil = firstDisagreement(wet, storm);
        return new Forecast(bands(s, wet, storm, certainUntil),
                            days(s, wet, storm, certainUntil),
                            certainUntil);
    }

    private static int firstDisagreement(boolean[][] wet, boolean[][] storm) {
        for (int t = 0; t < PROBES; t++) {
            for (int p = 1; p < PATHS; p++) {
                if (wet[p][t] != wet[0][t] || storm[p][t] != storm[0][t]) {
                    return t * PROBE_TICKS;
                }
            }
        }
        return HORIZON_TICKS;
    }

    private static List<Forecast.Band> bands(WeatherSnapshot s, boolean[][] wet, boolean[][] storm,
                                             int certainUntil) {
        final List<Forecast.Band> out = new ArrayList<>();
        Condition run = majority(s, wet, storm, 0);
        int runStart = 0;
        for (int t = 1; t < STRIP_PROBES; t++) {
            final Condition here = majority(s, wet, storm, t);
            if (here != run) {
                final int end = t * PROBE_TICKS;
                out.add(new Forecast.Band(runStart, end, run, end <= certainUntil));
                run = here;
                runStart = end;
            }
        }
        out.add(new Forecast.Band(runStart, STRIP_TICKS, run, STRIP_TICKS <= certainUntil));
        return List.copyOf(out);
    }

    private static Condition majority(WeatherSnapshot s, boolean[][] wet, boolean[][] storm, int t) {
        int wetCount = 0;
        int stormCount = 0;
        for (int p = 0; p < PATHS; p++) {
            if (wet[p][t]) {
                wetCount++;
            }
            if (storm[p][t]) {
                stormCount++;
            }
        }
        if (wetCount * 2 <= PATHS) {
            return Condition.CLEAR;
        }
        return localise(s, stormCount * 2 > PATHS);
    }

    private static Condition localise(WeatherSnapshot s, boolean thundering) {
        return switch (s.localPrecipitation()) {
            case SNOW -> Condition.SNOW;
            case DRY, NONE -> Condition.DRY;
            default -> thundering ? Condition.STORM : Condition.RAIN;
        };
    }

    private static List<Forecast.DayCell> days(WeatherSnapshot s, boolean[][] wet, boolean[][] storm,
                                               int certainUntil) {
        final List<Forecast.DayCell> out = new ArrayList<>(DAYS);

        final int elapsedToday = (int) (s.dayTime() % DAY_TICKS);
        int cursor = 0;
        for (int d = 0; d < DAYS; d++) {
            final int span = d == 0 ? DAY_TICKS - elapsedToday : DAY_TICKS;
            final int from = Math.min(PROBES - 1, cursor / PROBE_TICKS);

            final int to = Math.max(from + 1, Math.min(PROBES, (cursor + span) / PROBE_TICKS));
            cursor += span;

            int wetPaths = 0;
            int stormPaths = 0;
            for (int p = 0; p < PATHS; p++) {
                boolean anyWet = false;
                boolean anyStorm = false;
                for (int t = from; t < to; t++) {
                    anyWet |= wet[p][t];
                    anyStorm |= storm[p][t];
                }
                if (anyWet) {
                    wetPaths++;
                }
                if (anyStorm) {
                    stormPaths++;
                }
            }

            final boolean certain = cursor <= certainUntil;
            final boolean mostlyStorm = stormPaths * 2 > PATHS;
            final Condition condition = wetPaths * 2 > PATHS
                    ? localise(s, mostlyStorm)
                    : Condition.CLEAR;

            final float wetness = wetPaths / (float) PATHS * (mostlyStorm ? 1.0f : 0.7f);
            float high = Float.NEGATIVE_INFINITY;
            float low = Float.POSITIVE_INFINITY;
            for (int i = 0; i <= 24; i++) {
                final float t = ClimateModel.temperatureAt(s, i / 24.0f, wetness);
                high = Math.max(high, t);
                low = Math.min(low, t);
            }

            out.add(new Forecast.DayCell(s.dayIndex() + d, condition,
                    certain ? 0 : quantise(wetPaths * 100 / PATHS), certain, high, low));
        }
        return List.copyOf(out);
    }

    private static int quantise(int percent) {
        return Math.round(percent / 5.0f) * 5;
    }
}
