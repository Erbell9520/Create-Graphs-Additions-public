package com.createstats.timeswitch;

import com.createstats.blockentity.BlastShutterBE;
import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import com.createstats.timeswitch.TimeSwitchProgram.Preset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSwitchProgramTest {
    private static int pins(int... hours) {
        int out = 0;
        for (int h : hours) {
            out |= TimeSwitchProgram.bit(h);
        }
        return out;
    }

    private static final int DUSK_TO_DAWN = pins(19, 20, 21, 22, 23, 0, 1, 2, 3, 4);

    @Test
    void dayTimeZeroIsSixInTheMorningAndEachThousandIsAnHour() {
        assertEquals(6, TimeSwitchProgram.hourOf(0L));
        assertEquals(6, TimeSwitchProgram.hourOf(999L));
        assertEquals(7, TimeSwitchProgram.hourOf(1000L));
        assertEquals(12, TimeSwitchProgram.hourOf(6000L));
        assertEquals(18, TimeSwitchProgram.hourOf(12000L));
        assertEquals(19, TimeSwitchProgram.hourOf(13000L));
        assertEquals(0, TimeSwitchProgram.hourOf(18000L));
        assertEquals(5, TimeSwitchProgram.hourOf(23000L));
        assertEquals(5, TimeSwitchProgram.hourOf(23999L));
        assertEquals(6, TimeSwitchProgram.hourOf(24000L));
    }

    @Test
    void anyDayOfAnyWorldReadsTheSameAndNegativeTimesWrap() {
        assertEquals(19, TimeSwitchProgram.hourOf(24000L * 1000L + 13000L));
        assertEquals(5, TimeSwitchProgram.hourOf(-1L));
        assertEquals(0, TimeSwitchProgram.hourOf(-6000L));
        assertEquals(TimeSwitchProgram.hourOf(13000L), TimeSwitchProgram.hourOf(13000L - 24000L * 7L));
    }

    @Test
    void everyHourStartsWhereItsPinSaysAndEndsAThousandTicksLater() {
        for (int h = 0; h < 24; h++) {
            final long start = TimeSwitchProgram.startTick(h);
            assertEquals(h, TimeSwitchProgram.hourOf(start), "start of " + h);
            assertEquals(h, TimeSwitchProgram.hourOf(start + 999L), "end of " + h);
            assertEquals((h + 1) % 24, TimeSwitchProgram.hourOf(start + 1000L), "after " + h);
        }
        assertEquals(13000L, TimeSwitchProgram.startTick(19));
        assertEquals(23000L, TimeSwitchProgram.startTick(5));
        assertEquals(18000L, TimeSwitchProgram.startTick(0));
    }

    @Test
    void theFractionalHourAgreesWithTheWholeOneAndIsContinuousAtMidnight() {
        assertEquals(19.0005, TimeSwitchProgram.hourOf(13000.5), 1e-9);
        for (long t = 0; t < 24000L; t += 250L) {
            assertEquals(TimeSwitchProgram.hourOf(t), (int) Math.floor(TimeSwitchProgram.hourOf((double) t)),
                    "tick " + t);
        }

        assertTrue(TimeSwitchProgram.hourOf(17999.9) > 23.999);
        assertEquals(0.0, TimeSwitchProgram.hourOf(18000.0), 1e-12);
        assertTrue(TimeSwitchProgram.hourOf(-0.25) < 24.0);
    }

    @Test
    void aPinCoversExactlyItsHourInAuto() {
        final int p = pins(19);
        assertFalse(TimeSwitchProgram.closed(12999L, p, Mode.AUTO), "18:59");
        assertTrue(TimeSwitchProgram.closed(13000L, p, Mode.AUTO), "19:00");
        assertTrue(TimeSwitchProgram.closed(13999L, p, Mode.AUTO), "19:59");
        assertFalse(TimeSwitchProgram.closed(14000L, p, Mode.AUTO), "20:00");
    }

    @Test
    void onAndOffHoldWhateverThePinsSay() {
        for (long t = 0; t < 24000L; t += 500L) {
            assertTrue(TimeSwitchProgram.closed(t, TimeSwitchProgram.NONE, Mode.ON));
            assertFalse(TimeSwitchProgram.closed(t, TimeSwitchProgram.ALL, Mode.OFF));
        }
    }

    @Test
    void duskToDawnIsTheShuttersOwnNightTickForTick() {
        final int program = TimeSwitchProgram.preset(Preset.DUSK_TO_DAWN, 0,
                BlastShutterBE.DUSK, BlastShutterBE.DAWN);
        assertEquals(DUSK_TO_DAWN, program);
        assertEquals(10, TimeSwitchProgram.count(program));
        for (long t = 0; t < 24000L; t++) {
            final boolean night = t >= BlastShutterBE.DUSK && t < BlastShutterBE.DAWN;
            assertEquals(night, TimeSwitchProgram.closed(t, program, Mode.AUTO), "tick " + t);
        }
    }

    @Test
    void daytimeIsTheExactComplementOfDuskToDawn() {
        final int day = TimeSwitchProgram.preset(Preset.DAYTIME, 0, BlastShutterBE.DUSK, BlastShutterBE.DAWN);
        assertEquals(TimeSwitchProgram.ALL & ~DUSK_TO_DAWN, day);
        assertEquals(pins(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18), day);
    }

    @Test
    void invertAndClearWorkOnTheCurrentProgramAndStayInsideTwentyFourBits() {
        final int some = pins(3, 7, 22);
        final int inverted = TimeSwitchProgram.preset(Preset.INVERT, some, 13000L, 23000L);
        assertEquals(21, TimeSwitchProgram.count(inverted));
        assertEquals(0, inverted & ~TimeSwitchProgram.ALL, "no bit above 23");
        assertEquals(some, TimeSwitchProgram.preset(Preset.INVERT, inverted, 13000L, 23000L));
        assertEquals(TimeSwitchProgram.ALL, TimeSwitchProgram.preset(Preset.INVERT, 0, 13000L, 23000L));
        assertEquals(TimeSwitchProgram.NONE, TimeSwitchProgram.preset(Preset.CLEAR, some, 13000L, 23000L));
    }

    @Test
    void aRangeOffTheHourTakesThePinsWhoseHourStartsInIt() {
        assertEquals(pins(20, 21, 22, 23, 0, 1, 2, 3, 4), TimeSwitchProgram.startingIn(13500L, 22500L));

        assertEquals(0, TimeSwitchProgram.startingIn(5000L, 5000L));
        assertEquals(TimeSwitchProgram.ALL, TimeSwitchProgram.startingIn(0L, 23999L));
    }

    @Test
    void settingPinsTouchesOnlyTheMaskAndDropsStrayBits() {
        assertEquals(pins(1, 2), TimeSwitchProgram.set(pins(1), pins(2), true));
        assertEquals(pins(1), TimeSwitchProgram.set(pins(1, 2), pins(2), false));
        assertEquals(TimeSwitchProgram.ALL, TimeSwitchProgram.set(0, -1, true));
    }

    @Test
    void theNextChangeIsTheNextHourWhosePinDiffers() {
        assertEquals(19, TimeSwitchProgram.nextChange(12166L, DUSK_TO_DAWN, Mode.AUTO), "18:10, closes at 19");
        assertEquals(5, TimeSwitchProgram.nextChange(14500L, DUSK_TO_DAWN, Mode.AUTO), "20:30, opens at 05");
        assertEquals(19, TimeSwitchProgram.nextChange(23000L, DUSK_TO_DAWN, Mode.AUTO), "05:00, next is dusk");
        assertEquals(1000L, TimeSwitchProgram.ticksToNextChange(12000L, DUSK_TO_DAWN, Mode.AUTO));
        assertEquals(500L, TimeSwitchProgram.ticksToNextChange(22500L, DUSK_TO_DAWN, Mode.AUTO));
    }

    @Test
    void thereIsNoNextChangeWhenEveryPinAgreesOrTheSlideHolds() {
        assertEquals(-1, TimeSwitchProgram.nextChange(0L, TimeSwitchProgram.NONE, Mode.AUTO));
        assertEquals(-1, TimeSwitchProgram.nextChange(0L, TimeSwitchProgram.ALL, Mode.AUTO));
        assertEquals(-1, TimeSwitchProgram.nextChange(0L, DUSK_TO_DAWN, Mode.ON));
        assertEquals(-1, TimeSwitchProgram.nextChange(0L, DUSK_TO_DAWN, Mode.OFF));
        assertEquals(-1L, TimeSwitchProgram.ticksToNextChange(0L, TimeSwitchProgram.ALL, Mode.AUTO));

        final int allButNoon = TimeSwitchProgram.ALL & ~pins(12);
        assertEquals(12, TimeSwitchProgram.nextChange(0L, allButNoon, Mode.AUTO));
        assertEquals(13, TimeSwitchProgram.nextChange(6000L, allButNoon, Mode.AUTO));
    }

    @Test
    void aRunAcrossMidnightIsOneRange() {
        final List<int[]> runs = TimeSwitchProgram.ranges(DUSK_TO_DAWN);
        assertEquals(1, runs.size());
        assertArrayEquals(new int[] {19, 5}, runs.get(0));
        assertEquals("19:00 to 05:00", TimeSwitchProgram.describe(DUSK_TO_DAWN, "to", ", "));
        assertArrayEquals(new int[] {23, 0}, TimeSwitchProgram.ranges(pins(23)).get(0));
        assertArrayEquals(new int[] {22, 2}, TimeSwitchProgram.ranges(pins(22, 23, 0, 1)).get(0));
    }

    @Test
    void severalRangesReadInOrderOfTheirStart() {
        final int program = DUSK_TO_DAWN | pins(7, 8);
        assertEquals("07:00 to 09:00, 19:00 to 05:00", TimeSwitchProgram.describe(program, "to", ", "));
        assertEquals(12, TimeSwitchProgram.ranges(0x555555).size(), "every other hour is twelve runs");
    }

    @Test
    void noPinsAndEveryPinAreTheirOwnCases() {
        assertTrue(TimeSwitchProgram.ranges(TimeSwitchProgram.NONE).isEmpty());
        assertEquals("", TimeSwitchProgram.describe(TimeSwitchProgram.NONE, "to", ", "));
        assertArrayEquals(new int[] {0, 24}, TimeSwitchProgram.ranges(TimeSwitchProgram.ALL).get(0));
        assertEquals("00:00 to 24:00", TimeSwitchProgram.describe(TimeSwitchProgram.ALL, "to", ", "));
        assertEquals("07:00", TimeSwitchProgram.clock(7));
    }

    @Test
    void theSavedIdsNeverMoveAndAnythingUnknownLoadsSafe() {
        assertEquals(0, Mode.AUTO.id());
        assertEquals(1, Mode.ON.id());
        assertEquals(2, Mode.OFF.id());
        assertEquals(Mode.AUTO, Mode.byId(7));
        assertEquals(Mode.AUTO, Mode.byId(-1));
        assertEquals(Preset.CLEAR, Preset.byId(9));
        for (Preset p : Preset.values()) {
            assertEquals(p, Preset.byId(p.id()));
        }
    }
}
