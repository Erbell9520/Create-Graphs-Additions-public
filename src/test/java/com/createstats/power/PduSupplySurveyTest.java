package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduSupplySurveyTest {
    private static PduSupplySurvey.Builder builder() {
        return new PduSupplySurvey.Builder();
    }

    private static net.neoforged.neoforge.energy.IEnergyStorage tank(int energy, int capacity) {
        return new net.neoforged.neoforge.energy.IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return Math.max(0, Math.min(maxReceive, capacity - energy));
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return Math.max(0, Math.min(maxExtract, energy));
            }

            @Override
            public int getEnergyStored() {
                return energy;
            }

            @Override
            public int getMaxEnergyStored() {
                return capacity;
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    @Test
    void aForeignBatteryOnTheFeedIsPartOfTheCascade() {
        final PduSupplySurvey.Result result = builder()
                .addForeignTank(tank(400_000, 2_000_000), 7L, FeReserve.DEFAULT_RANK, 400_000, 2_000_000)
                .build();
        assertTrue(result.hasReserve(), "a foreign battery is still a battery");
        assertEquals(400_000, result.reserveTotal());
        assertEquals(2_000_000, result.reserveCapacity());
        assertEquals(1, result.reserveDepth());
        assertTrue(result.rideThrough(1_200) > 0, "and it can fund a ride-through");
    }

    @Test
    void oneMultiblockAccumulatorReachedAtSeveralBlocksCountsOnce() {
        final net.neoforged.neoforge.energy.IEnergyStorage shared = tank(400_000, 2_000_000);
        final PduSupplySurvey.Result result = builder()
                .addForeignTank(shared, 10L, FeReserve.DEFAULT_RANK, 400_000, 2_000_000)
                .addForeignTank(shared, 11L, FeReserve.DEFAULT_RANK, 400_000, 2_000_000)
                .addForeignTank(shared, 12L, FeReserve.DEFAULT_RANK, 400_000, 2_000_000)
                .addForeignTank(shared, 13L, FeReserve.DEFAULT_RANK, 400_000, 2_000_000)
                .build();
        assertEquals(1, result.tanks().size(), "one accumulator, one tank");
        assertEquals(400_000, result.reserveTotal());
        assertEquals(2_000_000, result.reserveCapacity());
    }

    @Test
    void twoSeparateForeignBatteriesAreTwoTanks() {
        final PduSupplySurvey.Result result = builder()
                .addForeignTank(tank(100_000, 1_000_000), 20L, FeReserve.DEFAULT_RANK, 100_000, 1_000_000)
                .addForeignTank(tank(250_000, 1_000_000), 21L, FeReserve.DEFAULT_RANK, 250_000, 1_000_000)
                .build();
        assertEquals(2, result.tanks().size());
        assertEquals(350_000, result.reserveTotal());
        assertEquals(2, result.reserveDepth());
    }

    @Test
    void aForeignBatteryAndOneOfOursShareOneCascade() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, FeReserve.MIN_RANK, 500_000, 1_000_000)
                .addForeignTank(tank(400_000, 2_000_000), 2L, FeReserve.DEFAULT_RANK, 400_000, 2_000_000)
                .build();
        assertEquals(2, result.tanks().size());
        assertEquals(FeReserve.MIN_RANK, result.coreRank(), "ours is the core it protects");
        assertEquals(FeReserve.MIN_RANK, result.tanks().get(0).rank(), "and it sorts first");
        assertEquals(900_000, result.reserveTotal());
    }

    @Test
    void aMergedBankReachedSeveralTimesCountsOnce() {
        final PduSupplySurvey.Result result = builder()
                .addTank(42L, 1, 1_000, 5_000)
                .addTank(42L, 1, 1_000, 5_000)
                .addTank(42L, 1, 1_000, 5_000)
                .build();
        assertEquals(1, result.tanks().size());
        assertEquals(1_000, result.reserveTotal(), "and its charge is counted once too");
    }

    @Test
    void aGeneratorReachedFromTwoInputFacesCountsOnce() {
        final PduSupplySurvey.Result result = builder()
                .addSource(7L, 500)
                .addSource(7L, 500)
                .build();
        assertEquals(1, result.sourceCount());
        assertEquals(500, result.knownGeneration());
    }

    @Test
    void tanksComeBackCoreFirstWhateverOrderTheyWereFoundIn() {
        final PduSupplySurvey.Result result = builder()
                .addTank(3L, 9, 100, 1_000)
                .addTank(1L, 1, 100, 1_000)
                .addTank(2L, 4, 100, 1_000)
                .build();
        assertEquals(1, result.tanks().get(0).rank());
        assertEquals(4, result.tanks().get(1).rank());
        assertEquals(9, result.tanks().get(2).rank());
        assertEquals(1, result.coreRank());
    }

    @Test
    void theFingerprintMovesWhenAGeneratorIsAddedOrRemoved() {
        final long one = builder().addSource(1L, 100).build().sourceSignature();
        final long two = builder().addSource(1L, 100).addSource(2L, 100).build().sourceSignature();
        assertNotEquals(one, two, "a generator appearing is a change in the supply");

        final long swapped = builder().addSource(3L, 100).build().sourceSignature();
        assertNotEquals(one, swapped, "so is one generator being replaced by another");
    }

    @Test
    void theFingerprintIgnoresOutputAndOrder() {
        final long fast = builder().addSource(1L, 4_000).addSource(2L, 4_000).build().sourceSignature();
        final long slow = builder().addSource(1L, 5).addSource(2L, 0).build().sourceSignature();
        assertEquals(fast, slow, "output is not identity");

        final long reversed = builder().addSource(2L, 0).addSource(1L, 5).build().sourceSignature();
        assertEquals(slow, reversed, "nor is the order the flood fill happened to find them in");
    }

    @Test
    void foldSourceFingerprintMovesTheSignatureWithoutChangingGenerationOrCount() {
        final PduSupplySurvey.Result plain = builder().addSource(1L, 8_192).build();
        final PduSupplySurvey.Result folded = builder().addSource(1L, 8_192)
                .foldSourceFingerprint(555L).build();
        assertEquals(plain.knownGeneration(), folded.knownGeneration(), "generation is unchanged");
        assertEquals(plain.sourceCount(), folded.sourceCount(), "the source count is unchanged");
        assertNotEquals(plain.sourceSignature(), folded.sourceSignature(),
                "but the fingerprint reacts to the plant's generator set changing");
    }

    @Test
    void foldingDifferentGeneratorSetsGivesDifferentFingerprints() {
        final long two = builder().addSource(1L, 100)
                .foldSourceFingerprint(10L).foldSourceFingerprint(20L).build().sourceSignature();
        final long one = builder().addSource(1L, 100)
                .foldSourceFingerprint(10L).build().sourceSignature();
        assertNotEquals(one, two, "one generator behind the seam versus two is a changed set");
    }

    @Test
    void aSourceWithNoReadablePotentialStillCounts() {
        final PduSupplySurvey.Result result = builder().addSource(5L, 0).build();
        assertEquals(1, result.sourceCount());
        assertEquals(0, result.knownGeneration());
        assertNotEquals(PduSupplySurvey.Result.EMPTY.sourceSignature(), result.sourceSignature());
    }

    @Test
    void depthCountsTanksHoldingChargeNotTanksPresent() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 5_000, 10_000)
                .addTank(2L, 2, 0, 10_000)
                .addTank(3L, 3, 250, 10_000)
                .build();
        assertEquals(3, result.tanks().size());
        assertEquals(2, result.reserveDepth(), "an empty tank is present but is not depth");
        assertEquals(5_250, result.reserveTotal());
        assertEquals(30_000, result.reserveCapacity());
    }

    @Test
    void oneFullBankBesideOneEmptyOneIsNotAnEmergency() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 1_000_000, 1_000_000)
                .addTank(2L, 2, 0, 1_000_000)
                .build();
        assertEquals(1, result.reserveDepth(), "one tank holds charge, which on its own means nothing");
        assertEquals(0.5f, result.reserveFill(), 0.001f);
        assertFalse(result.reserveLow(20), "half a cascade of charge is not a nearly spent one");
        assertEquals(1_666, result.rideThrough(600), "and all of it funds the branch");
    }

    @Test
    void aNearlySpentCascadeIsTheProtectionSignal() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 1_500, 10_000)
                .addTank(2L, 2, 0, 10_000)
                .addTank(3L, 3, 0, 10_000)
                .build();
        assertEquals(0.05f, result.reserveFill(), 0.001f);
        assertTrue(result.reserveLow(20));
    }

    @Test
    void aLoneBankRunningFlatIsProtectedToo() {
        final PduSupplySurvey.Result result = builder().addTank(1L, 1, 300, 10_000).build();
        assertTrue(result.reserveLow(20));
    }

    @Test
    void noStorageAtAllIsNeverLowAndFundsNothing() {
        assertFalse(PduSupplySurvey.Result.EMPTY.reserveLow(20));
        assertFalse(PduSupplySurvey.Result.EMPTY.hasReserve());
        assertEquals(0, PduSupplySurvey.Result.EMPTY.rideThrough(600));

        final PduSupplySurvey.Result drained = builder().addTank(1L, 1, 0, 10_000).build();
        assertFalse(drained.hasReserve(), "a bank at zero holds nothing to hold back");
        assertFalse(drained.reserveLow(20));
    }

    @Test
    void protectionCanBeTurnedOffByThreshold() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 100, 10_000)
                .addTank(2L, 2, 0, 10_000)
                .build();
        assertTrue(result.reserveLow(20));
        assertFalse(result.reserveLow(0), "a zero threshold is how a pack switches this off");
    }

    @Test
    void theReserveFundsTheBranchSpreadOverTheHorizon() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 300_000, 500_000)
                .addTank(2L, 2, 300_000, 500_000)
                .build();
        assertEquals(1_000, result.rideThrough(600), "600,000 FE over 600 ticks");
    }

    @Test
    void aZeroOrNegativeHorizonCannotDivideByZero() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 1_000, 10_000)
                .addTank(2L, 2, 1_000, 10_000)
                .build();
        assertEquals(0, result.rideThrough(0));
        assertEquals(0, result.rideThrough(-5));
    }

    @Test
    void rankIsClampedIntoTheLegalRange() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, -3, 10, 100)
                .addTank(2L, 99, 10, 100)
                .build();
        assertEquals(FeReserve.MIN_RANK, result.tanks().get(0).rank());
        assertEquals(FeReserve.MAX_RANK, result.tanks().get(1).rank());
    }

    @Test
    void chargedTanksListsOnlyWhatIsStillHolding() {
        final PduSupplySurvey.Result result = builder()
                .addTank(1L, 1, 500, 1_000)
                .addTank(2L, 2, 0, 1_000)
                .addTank(3L, 3, 500, 1_000)
                .build();
        assertEquals(2, result.chargedTanks().size());
        assertEquals(1, result.chargedTanks().get(0).rank(), "core first, as the panel lists them");
    }

    private static int[] flat(int fePerTick) {
        final int[] out = new int[PduSupplySurvey.FORECAST_OFFSETS.length];
        java.util.Arrays.fill(out, fePerTick);
        return out;
    }

    @Test
    void aFeedWithNothingPredictableOnItHasNoForecastAtAll() {
        final PduSupplySurvey.Result result = builder()
                .addSource(1L, 360)
                .addSource(2L, 180)
                .build();
        assertFalse(result.hasForecast());
        assertEquals(PduSupplySurvey.NO_FORECAST, result.forecastFloor());
        assertEquals(0, result.forecastSources());
        assertEquals(0, result.expectedDropFePerTick());
        assertEquals(540, result.knownGeneration(), "pricing is untouched by any of this");
        assertFalse(PduSupplySurvey.Result.EMPTY.hasForecast());
        assertEquals(0, PduSupplySurvey.Result.EMPTY.expectedDropFePerTick());
    }

    @Test
    void aFeedOfSourcesThatCannotPredictChangesNothingAtAll() {
        final PduSupplySurvey.Result result = builder()
                .addSource(1L, 360)
                .addForecast(1L, flat(360))
                .addSource(2L, 180)
                .addForecast(2L, flat(180))
                .build();
        assertTrue(result.hasForecast());
        assertEquals(540, result.forecastFloor());
        assertEquals(540, result.forecastNow());
        assertEquals(0, result.expectedDropFePerTick(),
                "a flat forecast moved a board's budget, which is a change to every existing base");
    }

    @Test
    void aFallingForecastIsMeasuredAsWhatGoesAwayRatherThanWhatIsLeft() {
        final PduSupplySurvey.Result result = builder()
                .addForecast(1L, flat(360))
                .addForecast(2L, new int[] {300, 240, 170, 90, 10})
                .build();
        assertEquals(660, result.forecastNow());
        assertEquals(370, result.forecastFloor());
        assertEquals(290, result.expectedDropFePerTick(),
                "the drop has to be the solar half only, never the whole feed");
    }

    @Test
    void aRisingForecastTakesNothingAway() {
        final PduSupplySurvey.Result result = builder()
                .addForecast(1L, new int[] {10, 90, 170, 240, 300})
                .build();
        assertEquals(0, result.expectedDropFePerTick());
    }

    @Test
    void theFloorIsTheWorstPointInTheWindowAndNotTheLastOne() {
        final PduSupplySurvey.Result dusk = builder()
                .addSource(1L, 300)
                .addForecast(1L, new int[] {300, 240, 170, 90, 10})
                .build();
        assertTrue(dusk.hasForecast());
        assertEquals(10, dusk.forecastFloor());

        final PduSupplySurvey.Result dawn = builder()
                .addSource(1L, 10)
                .addForecast(1L, new int[] {10, 90, 170, 240, 300})
                .build();
        assertEquals(10, dawn.forecastFloor());
    }

    @Test
    void sourcesSumAcrossTheWindowAndAreDedupedOnTheirOwnKeys() {
        final PduSupplySurvey.Result result = builder()
                .addForecast(1L, new int[] {300, 240, 170, 90, 10})
                .addForecast(2L, flat(180))
                .addForecast(1L, flat(9_999))
                .build();
        assertEquals(2, result.forecastSources(), "the same source was counted twice");

        assertEquals(190, result.forecastFloor());
    }

    @Test
    void pricingAndForecastingAreSeparateLedgers() {
        final PduSupplySurvey.Result result = builder()
                .addSource(7L, 360)
                .addForecast(7L, flat(120))
                .build();
        assertEquals(360, result.knownGeneration());
        assertEquals(1, result.sourceCount());
        assertEquals(1, result.forecastSources());
        assertEquals(120, result.forecastFloor());
    }

    @Test
    void aForecastOfNothingIsAForecastRatherThanTheAbsenceOfOne() {
        final PduSupplySurvey.Result result = builder()
                .addSource(1L, 0)
                .addForecast(1L, flat(0))
                .build();
        assertTrue(result.hasForecast());
        assertEquals(0, result.forecastFloor());
    }

    @Test
    void aMalformedForecastIsIgnoredRatherThanTrusted() {
        final PduSupplySurvey.Result result = builder()
                .addForecast(1L, new int[] {5, 5})
                .addForecast(2L, null)
                .build();
        assertFalse(result.hasForecast(), "accepted a sample array of the wrong length");
    }

    @Test
    void negativeSamplesAreClampedRatherThanSubtracted() {
        final PduSupplySurvey.Result result = builder()
                .addForecast(1L, new int[] {100, 100, -500, 100, 100})
                .addForecast(2L, flat(50))
                .build();
        assertEquals(50, result.forecastFloor(), "a negative sample ate a real source's contribution");
    }

    @Test
    void knownGenerationSaturatesRatherThanWrapping() {
        final PduSupplySurvey.Result result = builder()
                .addSource(1L, Integer.MAX_VALUE)
                .addSource(2L, 360)
                .build();
        assertEquals(Integer.MAX_VALUE, result.knownGeneration());
    }

    @Test
    void onlyWeatherSensitiveSourcesCountTowardsTheShareRainMayDiscount() {
        final PduSupplySurvey.Result mixed = builder()
                .addSource(1L, 360)
                .addForecast(1L, flat(360))
                .addSource(2L, 300)
                .addForecast(2L, new int[] {300, 240, 170, 90, 10}, true)
                .build();
        assertEquals(300, mixed.weatherSensitiveNow());
        assertEquals(660, mixed.knownGeneration());
        final PduSupplySurvey.Result dynamoOnly = builder()
                .addSource(1L, 360)
                .addForecast(1L, flat(360))
                .build();
        assertEquals(0, dynamoOnly.weatherSensitiveNow());

        final PduSupplySurvey.Result twice = builder()
                .addForecast(2L, flat(300), true)
                .addForecast(2L, flat(300), true)
                .build();
        assertEquals(300, twice.weatherSensitiveNow());
    }

    @Test
    void theWeatherShareIsThePricedFigureNotTheForecast() {
        for (boolean priceFirst : new boolean[] {true, false}) {
            final PduSupplySurvey.Builder b = builder();
            if (priceFirst) {
                b.addSource(2L, 5_544);
                b.addForecast(2L, new int[] {5_520, 5_300, 5_050, 4_800, 4_540}, true);
            } else {
                b.addForecast(2L, new int[] {5_520, 5_300, 5_050, 4_800, 4_540}, true);
                b.addSource(2L, 5_544);
            }
            final PduSupplySurvey.Result r = b.build();
            assertEquals(5_544, r.knownGeneration());
            assertEquals(5_544, r.weatherSensitiveNow(), "order " + priceFirst);

            assertEquals(24, r.knownGeneration() - 5_520);
        }
    }

    @Test
    void unpricedSourcesAreCountedAndAPricedZeroIsNot() {
        final PduSupplySurvey.Result night = builder()
                .addSource(1L, 0)
                .addSource(2L, 0)
                .build();
        assertEquals(2, night.sourceCount());
        assertEquals(0, night.unpricedSources());

        final PduSupplySurvey.Result foreign = builder()
                .addSource(1L, 540)
                .addUnpricedSource(3L)
                .addUnpricedSource(3L)
                .build();
        assertEquals(2, foreign.sourceCount());
        assertEquals(1, foreign.unpricedSources());
        assertEquals(540, foreign.knownGeneration());

        final PduSupplySurvey.Result both = builder()
                .addSource(1L, 540)
                .addUnpricedSource(1L)
                .build();
        assertEquals(0, both.unpricedSources());
    }
}
