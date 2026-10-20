package com.createstats.power;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PduSupplySurvey {
    private PduSupplySurvey() {
    }

    public record Tank(long key, int rank, int energy, int capacity) {
        public Tank {
            rank = FeReserve.clamp(rank);
            energy = Math.max(0, energy);
            capacity = Math.max(0, capacity);
        }

        public boolean hasCharge() {
            return energy > 0;
        }

        public float fill() {
            return capacity <= 0 ? 0.0f : Math.min(1.0f, energy / (float) capacity);
        }
    }

    public static final int[] FORECAST_OFFSETS = {0, 250, 500, 750, 1_000};

    public static final int FORECAST_HORIZON_TICKS = FORECAST_OFFSETS[FORECAST_OFFSETS.length - 1];

    public static final int NO_FORECAST = -1;

    public record Result(int knownGeneration, int sourceCount, long sourceSignature, List<Tank> tanks,
                         int reserveTotal, int reserveCapacity, int reserveDepth, int coreRank,
                         boolean bankCharging, int forecastFloor, int forecastNow,
                         int forecastSources, int weatherSensitiveNow, int unpricedSources) {
        public static final Result EMPTY =
                new Result(0, 0, 0L, List.of(), 0, 0, 0, FeReserve.DEFAULT_RANK, false,
                        NO_FORECAST, 0, 0, 0, 0);

        public Result {
            tanks = List.copyOf(tanks);
        }

        public boolean hasForecast() {
            return forecastSources > 0 && forecastFloor >= 0;
        }

        public int expectedDropFePerTick() {
            return hasForecast() ? Math.max(0, forecastNow - forecastFloor) : 0;
        }

        public boolean hasReserve() {
            return !tanks.isEmpty() && reserveTotal > 0;
        }

        public float reserveFill() {
            return reserveCapacity <= 0 ? 0.0f : Math.min(1.0f, reserveTotal / (float) reserveCapacity);
        }

        public boolean reserveLow(int lowPct) {
            return hasReserve() && reserveFill() * 100.0f < lowPct;
        }

        public int rideThrough(int horizonTicks) {
            if (reserveTotal <= 0 || horizonTicks <= 0) {
                return 0;
            }
            return reserveTotal / horizonTicks;
        }

        public List<Tank> chargedTanks() {
            final List<Tank> charged = new ArrayList<>(tanks.size());
            for (Tank tank : tanks) {
                if (tank.hasCharge()) {
                    charged.add(tank);
                }
            }
            return charged;
        }
    }

    public static final class Builder {
        private final List<Tank> tanks = new ArrayList<>(4);
        private final Set<Long> seenTanks = new HashSet<>();

        private final Set<net.neoforged.neoforge.energy.IEnergyStorage> seenHandlers =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private final Set<Long> seenSources = new HashSet<>();
        private final Set<Long> seenForecasts = new HashSet<>();
        private final int[] forecastTotals = new int[FORECAST_OFFSETS.length];

        private final java.util.Map<Long, Integer> pricedByKey = new java.util.HashMap<>();

        private final java.util.Map<Long, Integer> weatherForecastNow = new java.util.HashMap<>();

        private final java.util.Map<Long, Integer> relayedShares = new java.util.HashMap<>();
        private final Set<Long> unpriced = new HashSet<>();
        private int knownGeneration;
        private long signature;
        private boolean bankCharging;

        public Builder addSource(long key, int knownPotential) {
            if (!seenSources.add(key)) {
                return this;
            }

            knownGeneration = FeRates.plus(knownGeneration, Math.max(0, knownPotential));
            pricedByKey.put(key, Math.max(0, knownPotential));

            signature += scramble(key);
            return this;
        }

        public Builder addUnpricedSource(long key) {
            if (seenSources.contains(key)) {
                return this;
            }
            unpriced.add(key);
            return addSource(key, 0);
        }

        public Builder foldSourceFingerprint(long key) {
            signature += scramble(key);
            return this;
        }

        public Builder addForecast(long key, int[] perOffset) {
            return addForecast(key, perOffset, false);
        }

        public Builder addForecast(long key, int[] perOffset, boolean weatherSensitive) {
            return addForecast(key, perOffset, weatherSensitive, 0);
        }

        public Builder addForecast(long key, int[] perOffset, boolean weatherSensitive,
                                   int relayedWeatherShare) {
            if (perOffset == null || perOffset.length != FORECAST_OFFSETS.length
                    || !seenForecasts.add(key)) {
                return this;
            }
            for (int i = 0; i < forecastTotals.length; i++) {
                forecastTotals[i] = FeRates.plus(forecastTotals[i], Math.max(0, perOffset[i]));
            }
            if (weatherSensitive) {
                weatherForecastNow.put(key, Math.max(0, perOffset[0]));
            } else if (relayedWeatherShare > 0) {
                relayedShares.put(key, relayedWeatherShare);
            }
            return this;
        }

        public Builder addTank(long key, int rank, int energy, int capacity) {
            if (!seenTanks.add(key)) {
                return this;
            }
            tanks.add(new Tank(key, rank, energy, capacity));
            return this;
        }

        public Builder addForeignTank(@Nullable net.neoforged.neoforge.energy.IEnergyStorage handler,
                                      long key, int rank, int energy, int capacity) {
            if (handler != null && !seenHandlers.add(handler)) {
                return this;
            }
            return addTank(key, rank, energy, capacity);
        }

        public Builder markBankCharging() {
            bankCharging = true;
            return this;
        }

        public Result build() {
            tanks.sort(Comparator.<Tank>comparingInt(Tank::rank).thenComparingLong(Tank::key));
            int total = 0;
            int capacity = 0;
            int depth = 0;
            int coreRank = FeReserve.DEFAULT_RANK;
            for (Tank tank : tanks) {
                total = FeRates.plus(total, tank.energy());
                capacity = FeRates.plus(capacity, tank.capacity());
                if (tank.hasCharge()) {
                    depth++;
                }
            }
            if (!tanks.isEmpty()) {
                coreRank = tanks.get(0).rank();
            }

            final long fingerprint = signature * 31L + seenSources.size();

            int floor = NO_FORECAST;
            int now = 0;
            if (!seenForecasts.isEmpty()) {
                floor = Integer.MAX_VALUE;
                for (int sample : forecastTotals) {
                    floor = Math.min(floor, sample);
                }
                floor = Math.max(0, floor);
                now = forecastTotals[0];
            }

            int weatherSensitiveNow = 0;
            for (java.util.Map.Entry<Long, Integer> e : weatherForecastNow.entrySet()) {
                weatherSensitiveNow = FeRates.plus(weatherSensitiveNow,
                        pricedByKey.getOrDefault(e.getKey(), e.getValue()));
            }
            for (java.util.Map.Entry<Long, Integer> e : relayedShares.entrySet()) {
                weatherSensitiveNow = FeRates.plus(weatherSensitiveNow,
                        Math.min(e.getValue(), pricedByKey.getOrDefault(e.getKey(), e.getValue())));
            }
            return new Result(knownGeneration, seenSources.size(), fingerprint, tanks,
                    total, capacity, depth, coreRank, bankCharging, floor, now,
                    seenForecasts.size(), weatherSensitiveNow, unpriced.size());
        }

        private static long scramble(long key) {
            long x = key * 0x9E3779B97F4A7C15L;
            x ^= x >>> 29;
            x *= 0xBF58476D1CE4E5B9L;
            x ^= x >>> 32;
            return x;
        }
    }
}
