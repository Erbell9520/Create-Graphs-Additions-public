package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeSupplyOutlookTest {
    private static final int SAMPLE_TICKS = 20;
    private static final int PEAK = 400;
    private static final int DAY = FeSupplyMemory.DAY_TICKS;

    private static FeSupplyMemory warmSolarBoard() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        for (long t = 0; t < 4L * DAY; t += SAMPLE_TICKS) {
            memory.observe(t, (int) Math.round(PEAK * SolarCurve.sunFactor(t)));
        }
        return memory;
    }

    private static FeSupplyOutlook.Weather clear() {
        return FeSupplyOutlook.Weather.steady(SolarCurve.Sky.CLEAR);
    }

    @Test
    @DisplayName("a cold board projects nothing at all rather than a confident zero")
    void aColdBoardRefusesToGuess() {
        final FeSupplyOutlook.Result none = FeSupplyOutlook.project(new FeSupplyMemory(), 6_000, DAY,
                clear(), 200);
        assertEquals(FeSupplyOutlook.Result.NONE, none);
        assertFalse(none.isShort(), "a board that has never watched a day must not ration for one");
        assertEquals(FeSupplyOutlook.Result.NONE,
                FeSupplyOutlook.project(null, 6_000, DAY, clear(), 200));
        assertEquals(FeSupplyOutlook.Result.NONE,
                FeSupplyOutlook.project(warmSolarBoard(), 6_000, 0, clear(), 200));
    }

    @Test
    @DisplayName("a warm board knows how much of the day it will be generating nothing")
    void itKnowsHowLongTheDarkIs() {
        final FeSupplyOutlook.Result result =
                FeSupplyOutlook.project(warmSolarBoard(), 0, DAY, clear(), 0);

        assertTrue(result.darkTicks() >= 12_000, "dark was only " + result.darkTicks());
        assertTrue(result.darkTicks() <= 14_000, "dark was " + result.darkTicks());
        assertFalse(result.certain(), "no countdown was supplied, so nothing here is certain");
    }

    @Test
    @DisplayName("a load it can carry is not short, and one it cannot is short by the difference")
    void theShortfallIsNeedMinusSupply() {
        final FeSupplyMemory memory = warmSolarBoard();

        final FeSupplyOutlook.Result easy = FeSupplyOutlook.project(memory, 0, DAY, clear(), 40);
        assertFalse(easy.isShort(), "shortfall on a load a quarter the size of the average supply");
        assertEquals(0, easy.shortfallFe());
        assertTrue(easy.coverage() >= 1.0f);

        final FeSupplyOutlook.Result hard = FeSupplyOutlook.project(memory, 0, DAY, clear(), 400);
        assertTrue(hard.isShort());
        assertEquals(hard.neededFe() - hard.projectedFe(), hard.shortfallFe());
        assertEquals(400 * DAY, hard.neededFe());
        assertTrue(hard.coverage() > 0.2f && hard.coverage() < 0.5f,
                "coverage was " + hard.coverage());
    }

    @Test
    @DisplayName("rain that is already falling is priced in, at the curve's own multiplier")
    void rainNowCostsWhatTheCurveSaysItCosts() {
        final FeSupplyMemory memory = warmSolarBoard();
        final int clearFe = FeSupplyOutlook.project(memory, 0, DAY, clear(), 0).projectedFe();
        final int rainFe = FeSupplyOutlook
                .project(memory, 0, DAY, FeSupplyOutlook.Weather.steady(SolarCurve.Sky.RAIN), 0)
                .projectedFe();
        final int stormFe = FeSupplyOutlook
                .project(memory, 0, DAY, FeSupplyOutlook.Weather.steady(SolarCurve.Sky.THUNDER), 0)
                .projectedFe();
        assertEquals(clearFe * SolarCurve.Sky.RAIN.factor(), rainFe, clearFe * 0.03,
                "rain should cost about the 0.35 the curve charges a panel");
        assertTrue(stormFe < rainFe, "a thunderstorm must cost more than rain");
    }

    @Test
    @DisplayName("the worked example: rain in four thousand ticks, and the board sees it coming")
    void theCountdownChangesTheAnswerMidHorizon() {
        final FeSupplyMemory memory = warmSolarBoard();

        final FeSupplyOutlook.Weather coming =
                new FeSupplyOutlook.Weather(SolarCurve.Sky.CLEAR, 4_000, SolarCurve.Sky.RAIN);
        final FeSupplyOutlook.Result blind = FeSupplyOutlook.project(memory, 0, DAY, clear(), 0);
        final FeSupplyOutlook.Result seeing = FeSupplyOutlook.project(memory, 0, DAY, coming, 0);
        assertTrue(seeing.certain(), "a real countdown went in and the result does not say so");
        assertFalse(blind.certain());
        assertTrue(seeing.projectedFe() < blind.projectedFe(),
                "knowing rain is coming has to lower the projection");

        final int allRain = FeSupplyOutlook
                .project(memory, 0, DAY, FeSupplyOutlook.Weather.steady(SolarCurve.Sky.RAIN), 0)
                .projectedFe();
        assertTrue(seeing.projectedFe() > allRain,
                "the four thousand clear ticks have to count for something");
        assertTrue(seeing.projectedFe() < (allRain + blind.projectedFe()) / 2,
                "rain starting before noon should cost more than half the day");
    }

    @Test
    @DisplayName("rain that starts after the horizon changes nothing")
    void aChangeBeyondTheHorizonIsNotBorrowed() {
        final FeSupplyMemory memory = warmSolarBoard();
        final FeSupplyOutlook.Weather later =
                new FeSupplyOutlook.Weather(SolarCurve.Sky.CLEAR, DAY * 2, SolarCurve.Sky.RAIN);
        assertEquals(FeSupplyOutlook.project(memory, 0, DAY, clear(), 0).projectedFe(),
                FeSupplyOutlook.project(memory, 0, DAY, later, 0).projectedFe());
    }

    @Test
    @DisplayName("rain clearing raises the projection, which is the same mechanism running the other way")
    void clearingIsForecastToo() {
        final FeSupplyMemory memory = warmSolarBoard();
        final FeSupplyOutlook.Weather clearing =
                new FeSupplyOutlook.Weather(SolarCurve.Sky.RAIN, 3_000, SolarCurve.Sky.CLEAR);
        final int stuck = FeSupplyOutlook
                .project(memory, 0, DAY, FeSupplyOutlook.Weather.steady(SolarCurve.Sky.RAIN), 0)
                .projectedFe();
        assertTrue(FeSupplyOutlook.project(memory, 0, DAY, clearing, 0).projectedFe() > stuck);
    }

    @Test
    @DisplayName("with no countdown the answer is exactly what it was before any of this existed")
    void noPanelMeansNoChange() {
        final FeSupplyMemory memory = warmSolarBoard();

        final FeSupplyOutlook.Result steady = FeSupplyOutlook.project(memory, 0, DAY, clear(), 0);
        assertEquals(memory.projectedFeOver(0, DAY), steady.projectedFe(),
                memory.projectedFeOver(0, DAY) / 100.0,
                "the clear-weather projection must be the learned model, undiscounted");
        assertFalse(steady.certain());
    }

    @Test
    @DisplayName("the hold target is capped, so a hopeless night still spends what it has")
    void theHoldIsCapped() {
        final FeSupplyOutlook.Result huge = new FeSupplyOutlook.Result(0, 10_000_000, 10_000_000,
                12_000, true);
        assertEquals(FeSupplyOutlook.MAX_HOLD_PCT, FeSupplyOutlook.holdPctFor(huge, 100_000),
                "an uncapped target would pin the floor at 100% and deliver nothing at all");
        assertEquals(25, FeSupplyOutlook.holdPctFor(
                new FeSupplyOutlook.Result(0, 250_000, 250_000, 0, true), 1_000_000));
        assertEquals(0, FeSupplyOutlook.holdPctFor(FeSupplyOutlook.Result.NONE, 1_000_000));
        assertEquals(0, FeSupplyOutlook.holdPctFor(huge, 0), "no banks, nothing to hold");
        assertEquals(0, FeSupplyOutlook.holdPctFor(null, 1_000_000));
    }

    @Test
    @DisplayName("the shortfall does not depend on anything the hold target would change")
    void itCannotEatItself() {
        final FeSupplyMemory memory = warmSolarBoard();
        final FeSupplyOutlook.Result first = FeSupplyOutlook.project(memory, 11_000, DAY, clear(), 300);
        final FeSupplyOutlook.Result again = FeSupplyOutlook.project(memory, 11_000, DAY, clear(), 300);
        assertEquals(first, again);
        assertTrue(first.isShort(), "dusk with a 300 FE/t critical load should be short");
    }

    @Test
    @DisplayName("an hour it has never watched counts as dark rather than being skipped")
    void unknownHoursAreDark() {
        final FeSupplyMemory memory = new FeSupplyMemory();

        for (int bucket = 0; bucket < FeSupplyMemory.BUCKETS / 2; bucket++) {
            for (int i = 0; i < 20; i++) {
                memory.observe((long) bucket * FeSupplyMemory.BUCKET_TICKS, 500);
            }
        }
        assertTrue(memory.isWarm());
        final FeSupplyOutlook.Result result = FeSupplyOutlook.project(memory, 0, DAY, clear(), 0);
        assertEquals(DAY / 2, result.darkTicks(),
                "the unwatched half of the day has to read as dark, not as absent");
    }

    private static FeSupplyMemory warmFlatBoard(int fe) {
        final FeSupplyMemory memory = new FeSupplyMemory();
        for (long t = 0; t < 4L * DAY; t += SAMPLE_TICKS) {
            memory.observe(t, fe);
        }
        return memory;
    }

    @Test
    @DisplayName("KI-52: a dynamo does not slow in the rain, so a dynamo board is never short for it")
    void rainDoesNotDiscountADynamo() {
        final FeSupplyMemory dynamo = warmFlatBoard(360);
        final FeSupplyMemory noPanels = warmFlatBoard(0);
        for (SolarCurve.Sky sky : SolarCurve.Sky.values()) {
            final FeSupplyOutlook.Result result = FeSupplyOutlook.project(dynamo, noPanels, 0, 6_000,
                    DAY, FeSupplyOutlook.Weather.steady(sky), 200);
            assertFalse(result.isShort(), sky + " made a dynamo board short by " + result.shortfallFe());
        }

        assertFalse(FeSupplyOutlook.project(dynamo, null, 0, 6_000, DAY,
                FeSupplyOutlook.Weather.steady(SolarCurve.Sky.THUNDER), 200).isShort());

        assertTrue(FeSupplyOutlook.project(dynamo, 6_000, DAY,
                FeSupplyOutlook.Weather.steady(SolarCurve.Sky.RAIN), 200).isShort(),
                "the whole-memory discount is what KI-52 was");
    }

    @Test
    @DisplayName("KI-52: on a mixed feed only the solar share pays for the rain")
    void onlyTheSolarShareIsDiscounted() {
        final FeSupplyMemory total = new FeSupplyMemory();
        final FeSupplyMemory solar = new FeSupplyMemory();
        for (long t = 0; t < 4L * DAY; t += SAMPLE_TICKS) {
            final int roof = (int) Math.round(PEAK * SolarCurve.sunFactor(t));
            total.observe(t, 360 + roof);
            solar.observe(t, roof);
        }
        final FeSupplyOutlook.Weather rain = FeSupplyOutlook.Weather.steady(SolarCurve.Sky.RAIN);
        final int clearFe = FeSupplyOutlook.project(total, solar, 0, 0, DAY, clear(), 0).projectedFe();
        final int rainFe = FeSupplyOutlook.project(total, solar, 0, 0, DAY, rain, 0).projectedFe();
        final int roofFe = FeSupplyOutlook.project(solar, 0, DAY, clear(), 0).projectedFe();

        assertEquals(clearFe - roofFe * (1.0 - SolarCurve.Sky.RAIN.factor()), rainFe, clearFe * 0.02,
                "rain should cost 65% of the roof and nothing of the dynamo");
    }

    @Test
    @DisplayName("KI-53: supply the survey cannot price is held flat, not projected as zero")
    void unpricedSupplyIsNotZero() {
        final FeSupplyMemory learnedZero = warmFlatBoard(0);
        final FeSupplyOutlook.Result blind = FeSupplyOutlook.project(learnedZero, null, 0, 6_000,
                DAY, clear(), 200);
        assertTrue(blind.isShort(), "without the flat term the board is short, which is the bug");
        final FeSupplyOutlook.Result seeing = FeSupplyOutlook.project(learnedZero, null, 1_000,
                6_000, DAY, clear(), 200);
        assertFalse(seeing.isShort(), "short by " + seeing.shortfallFe() + " on a 1,000 FE/t feed");
        assertEquals(0, seeing.darkTicks(), "a flat feed is never dark");
    }

    @Test
    @DisplayName("KI-54: at dusk the night must be banked, however bright tomorrow will be")
    void tomorrowDoesNotPayForTonight() {
        final FeSupplyMemory memory = warmSolarBoard();

        final FeSupplyOutlook.Result dusk = FeSupplyOutlook.project(memory, 11_500, DAY, clear(), 100);
        assertTrue(dusk.projectedFe() > dusk.neededFe(), "the day as a whole does cover the load");
        assertTrue(dusk.isShort(), "and the night still has to come out of the banks");
        assertEquals(100.0 * 12_500, dusk.shortfallFe(), 100.0 * 1_500,
                "the shortfall is the night, not the net: " + dusk.shortfallFe());

        final FeSupplyOutlook.Result dawn = FeSupplyOutlook.project(memory, 0, DAY, clear(), 100);
        assertTrue(dawn.shortfallFe() < dusk.shortfallFe() / 10,
                "at sunrise the hold should be the first hour at most: " + dawn.shortfallFe());
    }

    @Test
    @DisplayName("a weather record with nothing in it is a clear sky rather than a crash")
    void weatherDefaults() {
        final FeSupplyOutlook.Weather blank = new FeSupplyOutlook.Weather(null, -1, null);
        assertEquals(SolarCurve.Sky.CLEAR, blank.now());
        assertEquals(SolarCurve.Sky.CLEAR, blank.then());
        assertFalse(blank.isCertain());
        assertEquals(SolarCurve.Sky.CLEAR, blank.at(99_999));
        final FeSupplyOutlook.Weather coming =
                new FeSupplyOutlook.Weather(SolarCurve.Sky.CLEAR, 100, SolarCurve.Sky.THUNDER);
        assertEquals(SolarCurve.Sky.CLEAR, coming.at(99));
        assertEquals(SolarCurve.Sky.THUNDER, coming.at(100));

        final FeSupplyMemory memory = warmSolarBoard();
        assertEquals(FeSupplyOutlook.project(memory, 0, DAY, clear(), 0).projectedFe(),
                FeSupplyOutlook.project(memory, 0, DAY, null, 0).projectedFe());
    }
}
