package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// the 3x3 anchor is a contract. don't move it
class SolarCurveTest {
    private static final int NOON = 6_000;
    private static final int SUNRISE = 0;
    private static final int SUNSET = SolarCurve.DAYLIGHT_TICKS;
    private static final int MIDNIGHT = 18_000;
    private static final int FULL = SolarCurve.FULL_SKY_LIGHT;

    @Test
    @DisplayName("a 3x3 Reinforced roof at noon is one Kinetic Dynamo at 256 RPM")
    void theAnchor() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        final int perPanel = SolarCurve.fePerTick(peak, 9, NOON, SolarCurve.Sky.CLEAR, FULL);
        assertEquals(40, perPanel, "one panel of a nine panel array");
        assertEquals(360, perPanel * 9, "the whole roof, which is DynamoTier.STANDARD at 256 RPM");
    }

    @Test
    @DisplayName("the array bonus is a quarter per factor of nine, capped at 1.5")
    void arrayBonus() {
        assertEquals(1.00, SolarCurve.arrayMultiplier(1), 1e-9);
        assertEquals(1.00, SolarCurve.arrayMultiplier(0), 1e-9, "a group can never be empty");
        assertEquals(1.25, SolarCurve.arrayMultiplier(9), 1e-9);
        assertEquals(1.50, SolarCurve.arrayMultiplier(81), 1e-9, "two full steps, exactly at the cap");
        assertEquals(1.50, SolarCurve.arrayMultiplier(10_000), 1e-9, "and it stops there");

        assertEquals(1.3662, SolarCurve.arrayMultiplier(25), 1e-4);

        assertEquals(1_075, SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK, 25, NOON,
                SolarCurve.Sky.CLEAR, FULL) * 25, "a 5x5 Reinforced roof at noon");
    }

    @Test
    @DisplayName("the bonus rises with every panel added, and never falls")
    void arrayBonusIsMonotonic() {
        double last = 0.0;
        for (int panels = 1; panels <= 200; panels++) {
            final double now = SolarCurve.arrayMultiplier(panels);
            assertTrue(now >= last, "bonus fell going to " + panels + " panels");
            assertTrue(now <= SolarCurve.ARRAY_CAP + 1e-9, "bonus exceeded the cap at " + panels);
            last = now;
        }
    }

    @Test
    @DisplayName("night is zero, and it is zero for the whole night")
    void nightIsZero() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        for (int t = SUNSET; t < SolarCurve.DAY_TICKS; t += 250) {
            assertEquals(0, SolarCurve.fePerTick(peak, 9, t, SolarCurve.Sky.CLEAR, FULL),
                    "made FE at day time " + t);
        }
        assertEquals(0, SolarCurve.fePerTick(peak, 9, MIDNIGHT, SolarCurve.Sky.CLEAR, FULL));
        assertEquals(0, SolarCurve.fePerTick(peak, 9, SUNRISE, SolarCurve.Sky.CLEAR, FULL),
                "the sun is on the horizon at sunrise, not above it");
    }

    @Test
    @DisplayName("day time counts up forever and the curve still repeats")
    void dayTimeWrapsRatherThanRunningOff() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        final long tenDaysOn = SolarCurve.DAY_TICKS * 10L + NOON;
        assertEquals(SolarCurve.fePerTick(peak, 1, NOON, SolarCurve.Sky.CLEAR, FULL),
                SolarCurve.fePerTick(peak, 1, tenDaysOn, SolarCurve.Sky.CLEAR, FULL));

        assertEquals(SolarCurve.sunFactor(NOON), SolarCurve.sunFactor(NOON - SolarCurve.DAY_TICKS),
                1e-9);
    }

    @Test
    @DisplayName("the sun ramps rather than snapping, and peaks at noon")
    void theSunRamps() {
        assertEquals(0.0, SolarCurve.sunFactor(SUNRISE), 1e-9);
        assertEquals(1.0, SolarCurve.sunFactor(NOON), 1e-9);
        assertEquals(0.0, SolarCurve.sunFactor(SUNSET), 1e-9);
        assertEquals(0.7071, SolarCurve.sunFactor(3_000), 1e-4, "three hours after dawn");
        assertEquals(0.7071, SolarCurve.sunFactor(9_000), 1e-4, "and the same before dusk");

        double last = -1.0;
        for (int t = 0; t <= NOON; t += 100) {
            final double now = SolarCurve.sunFactor(t);
            assertTrue(now >= last, "morning went backwards at " + t);
            last = now;
        }
    }

    @Test
    @DisplayName("rain is 0.35 and thunder is 0.15, and both still respect the sun")
    void weather() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        final int clear = SolarCurve.fePerTick(peak, 9, NOON, SolarCurve.Sky.CLEAR, FULL);
        assertEquals(40, clear);
        assertEquals(14, SolarCurve.fePerTick(peak, 9, NOON, SolarCurve.Sky.RAIN, FULL),
                "0.35 of 40");
        assertEquals(6, SolarCurve.fePerTick(peak, 9, NOON, SolarCurve.Sky.THUNDER, FULL),
                "0.15 of 40");

        assertEquals(0, SolarCurve.fePerTick(peak, 9, MIDNIGHT, SolarCurve.Sky.THUNDER, FULL));
    }

    @Test
    @DisplayName("shade is linear and a roof over a panel is zero, with no special case for it")
    void shade() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        assertEquals(0, SolarCurve.fePerTick(peak, 1, NOON, SolarCurve.Sky.CLEAR, 0),
                "indoors, and the Nether, which has no sky light at all");
        assertEquals(14, SolarCurve.fePerTick(peak, 1, NOON, SolarCurve.Sky.CLEAR, 7),
                "under leaves, a little under half");
        assertEquals(32, SolarCurve.fePerTick(peak, 1, NOON, SolarCurve.Sky.CLEAR, FULL));
        assertEquals(32, SolarCurve.fePerTick(peak, 1, NOON, SolarCurve.Sky.CLEAR, 99),
                "a modded dimension claiming more than 15 is clamped, not rewarded");
    }

    @Test
    @DisplayName("the three grades are 0.6, 1.0 and 1.6 of the base")
    void tiers() {
        assertEquals(SolarCurve.BASE_FE_PER_TICK * 0.60,
                SolarTier.BASIC.outputScale() * SolarCurve.BASE_FE_PER_TICK, 1e-9);
        assertEquals(19, SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK * 0.60, 1, NOON,
                SolarCurve.Sky.CLEAR, FULL), "one Basic panel alone at noon");
        assertEquals(51, SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK * 1.60, 1, NOON,
                SolarCurve.Sky.CLEAR, FULL), "one Photovoltaic panel alone at noon");

        for (int t = 500; t < SUNSET; t += 500) {
            final int basic = SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK * 0.60, 9, t,
                    SolarCurve.Sky.CLEAR, FULL);
            final int photo = SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK * 1.60, 9, t,
                    SolarCurve.Sky.CLEAR, FULL);
            assertTrue(photo >= basic, "the ladder inverted at day time " + t);
        }
    }

    @Test
    @DisplayName("looking ahead is the same curve, moved")
    void lookAhead() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        assertEquals(SolarCurve.fePerTick(peak, 9, NOON, SolarCurve.Sky.CLEAR, FULL),
                SolarCurve.fePerTickIn(peak, 9, 3_000, 3_000, SolarCurve.Sky.CLEAR, FULL),
                "three hours before noon, three hours ahead");
        assertEquals(0, SolarCurve.fePerTickIn(peak, 9, 9_000, 6_000, SolarCurve.Sky.CLEAR, FULL),
                "mid afternoon, looking past dusk");

        assertEquals(SolarCurve.fePerTick(peak, 9, NOON, SolarCurve.Sky.CLEAR, FULL),
                SolarCurve.fePerTickIn(peak, 9, NOON, -5_000, SolarCurve.Sky.CLEAR, FULL));
    }

    @Test
    @DisplayName("KI-50: a panel under a panel catches nothing, whatever sky light leaks past the top one")
    void aCoveredPanelIsShaded() {
        assertEquals(0.0, SolarCurve.exposure(FULL, true), 1e-12);
        assertEquals(1.0, SolarCurve.exposure(FULL, false), 1e-12);
        assertEquals(SolarCurve.skyLightFactor(7), SolarCurve.exposure(7, false), 1e-12);
        assertEquals(0, SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK, 81, NOON,
                SolarCurve.Sky.CLEAR, SolarCurve.exposure(FULL, true)));

        for (int light = 0; light <= FULL; light++) {
            assertEquals(SolarCurve.fePerTick(40.0, 9, NOON, SolarCurve.Sky.RAIN, light),
                    SolarCurve.fePerTick(40.0, 9, NOON, SolarCurve.Sky.RAIN,
                            SolarCurve.exposure(light, false)), "sky light " + light);
        }
    }

    @Test
    @DisplayName("KI-50: a stacked shaft of 81 panels makes one panel's worth, not 3,888 FE/t")
    void aShaftIsOnePanelOfRoof() {
        double exposureSum = SolarCurve.exposure(FULL, false);
        for (int below = 1; below < 81; below++) {
            exposureSum += SolarCurve.exposure(FULL, true);
        }
        final int size = SolarCurve.effectiveArraySize(exposureSum);
        assertEquals(1, size, "one collector, one panel of array");
        final int top = SolarCurve.fePerTick(SolarCurve.BASE_FE_PER_TICK, size, NOON,
                SolarCurve.Sky.CLEAR, SolarCurve.exposure(FULL, false));
        assertEquals(SolarCurve.BASE_FE_PER_TICK, top);
        assertTrue(top < 3_888 / 10, "the shaft is still paying out like a roof");
    }

    @Test
    @DisplayName("KI-50: dark panels do not buy the roof a bonus")
    void darkPanelsAreNotArea() {
        final double roof = 9 * SolarCurve.exposure(FULL, false);
        final double basement = 72 * SolarCurve.exposure(1, false);
        final int size = SolarCurve.effectiveArraySize(roof + basement);
        assertTrue(size < 15, "the basement bought " + (size - 9) + " panels of roof");
        assertEquals(9, SolarCurve.effectiveArraySize(roof), "a full 3x3 is still exactly nine");
        assertEquals(1, SolarCurve.effectiveArraySize(0.0), "never less than the panel itself");
        assertEquals(1, SolarCurve.effectiveArraySize(Double.NaN));
    }

    @Test
    @DisplayName("KI-51: a stopped clock forecasts the present, so the drop is exactly zero")
    void aFrozenClockPredictsNoDusk() {
        final double peak = SolarCurve.BASE_FE_PER_TICK;
        final double lit = SolarCurve.exposure(FULL, false);
        final int now = SolarCurve.fePerTick(peak, 81, 11_500, SolarCurve.Sky.CLEAR, lit);
        assertTrue(now > 0, "late afternoon should still be making something");
        for (int ahead : PduSupplySurvey.FORECAST_OFFSETS) {
            assertEquals(now, SolarCurve.fePerTickIn(peak, 81, 11_500, ahead,
                    SolarCurve.Sky.CLEAR, lit, false), "frozen, " + ahead + " ticks ahead");
        }

        assertEquals(0, SolarCurve.fePerTickIn(peak, 81, 11_500, 1_000,
                SolarCurve.Sky.CLEAR, lit, true));
    }

    @Test
    @DisplayName("KI-60: the cap is reached at 81 panels, and the constant says so")
    void theCapCountIsDerived() {
        assertEquals(81, SolarCurve.ARRAY_CAP_PANELS);
        assertEquals(SolarCurve.ARRAY_CAP, SolarCurve.arrayMultiplier(SolarCurve.ARRAY_CAP_PANELS), 1e-9);
        assertTrue(SolarCurve.arrayMultiplier(SolarCurve.ARRAY_CAP_PANELS - 1) < SolarCurve.ARRAY_CAP);
        assertTrue(SolarCurve.arrayMultiplier(62) < SolarCurve.ARRAY_CAP,
                "62, the figure the prose used to quote, is not the cap");
    }

    @Test
    @DisplayName("KI-60: night is the sun, not a rate that floored to zero")
    void nightIsTheSun() {
        assertEquals(0, SolarCurve.fePerTick(SolarTier.BASIC.outputScale() * SolarCurve.BASE_FE_PER_TICK,
                1, 1_000, SolarCurve.Sky.THUNDER, FULL));
        assertTrue(!SolarCurve.isNight(1_000));
        assertTrue(SolarCurve.isNight(18_000));
        assertTrue(SolarCurve.isNight(12_000), "the sine has set at 12,000");
    }

    @Test
    @DisplayName("KI-60: a panel is priced at what can leave its node, never past it")
    void pricingIsCappedAtTheNode() {
        final int rate = SolarCurve.fePerTick(400.0 * 1.6, 81, NOON, SolarCurve.Sky.CLEAR, FULL);
        assertEquals(960, rate);
        assertEquals(512, SolarCurve.deliverable(rate, 512));
        assertEquals(40, SolarCurve.deliverable(40, 512));
        assertEquals(0, SolarCurve.deliverable(-3, 512));
    }
}
