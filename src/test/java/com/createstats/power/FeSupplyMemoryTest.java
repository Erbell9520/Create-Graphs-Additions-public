package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeSupplyMemoryTest {
    private static final int SAMPLE_TICKS = 20;

    private static int solarDay(long dayTime, int peak) {
        return (int) Math.round(peak * SolarCurve.sunFactor(dayTime));
    }

    private static void runDays(FeSupplyMemory memory, int days, int peak) {
        for (long t = 0; t < (long) days * FeSupplyMemory.DAY_TICKS; t += SAMPLE_TICKS) {
            memory.observe(t, solarDay(t, peak));
        }
    }

    @Test
    @DisplayName("an unwatched bucket says it does not know, and never says zero")
    void coldIsNotZero() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        assertEquals(FeSupplyMemory.UNKNOWN, memory.expectedFePerTick(6_000));
        assertEquals(0, memory.warmBuckets());
        assertTrue(!memory.isWarm());

        for (int i = 0; i < FeSupplyMemory.WARMUP_SAMPLES - 1; i++) {
            memory.observe(6_000, 400);
        }
        assertEquals(FeSupplyMemory.UNKNOWN, memory.expectedFePerTick(6_000),
                "believed a bucket before it was warm");
        memory.observe(6_000, 400);
        assertEquals(400, memory.expectedFePerTick(6_000));
    }

    @Test
    @DisplayName("after a few days it knows the shape of the day")
    void itLearnsTheDay() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        assertTrue(memory.isWarm(), "four days and still cold: " + memory.warmBuckets());

        assertEquals(360, memory.expectedFePerTick(6_000), 12);
        assertEquals(0, memory.expectedFePerTick(18_000));
        assertTrue(memory.expectedFePerTick(3_000) > memory.expectedFePerTick(1_000),
                "mid morning should beat early morning");
        assertTrue(memory.expectedFePerTick(6_000) > memory.expectedFePerTick(10_000),
                "noon should beat late afternoon");
    }

    @Test
    @DisplayName("half a day at dusk projects almost nothing, and at dawn projects a day's worth")
    void theProjectionKnowsWhichHalfIsComing() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        final int night = memory.projectedFeOver(12_000, FeSupplyMemory.DAY_TICKS / 2);
        final int day = memory.projectedFeOver(0, FeSupplyMemory.DAY_TICKS / 2);
        assertEquals(0, night, "the night half of the day is not zero");

        assertTrue(day > 2_000_000 && day < 3_200_000, "a day's projection was " + day);
        assertTrue(day > night * 10 + 1_000, "dawn and dusk projected the same thing");
    }

    @Test
    @DisplayName("an hour it has never watched is counted as nothing, not skipped")
    void coldBucketsAreCountedAsZero() {
        final FeSupplyMemory memory = new FeSupplyMemory();

        for (int i = 0; i < 40; i++) {
            memory.observe(6_000, 1_000);
        }
        final int projected = memory.projectedFeOver(0, FeSupplyMemory.DAY_TICKS);

        assertEquals(1_000 * FeSupplyMemory.BUCKET_TICKS, projected);
    }

    @Test
    @DisplayName("the projection moves smoothly through a bucket boundary rather than stepping")
    void theProjectionDoesNotStep() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        final int horizon = 6_000;
        int last = memory.projectedFeOver(1_000 - 40, horizon);
        for (int t = 1_000 - 20; t <= 1_000 + 60; t += 20) {
            final int now = memory.projectedFeOver(t, horizon);

            assertTrue(Math.abs(now - last) < 40_000,
                    "projection jumped by " + Math.abs(now - last) + " at day time " + t);
            last = now;
        }
    }

    @Test
    @DisplayName("one strange observation barely moves a learned bucket")
    void itDoesNotFlap() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        final int before = memory.expectedFePerTick(6_000);

        memory.observe(6_000, 0);
        final int after = memory.expectedFePerTick(6_000);
        assertTrue(Math.abs(before - after) <= Math.max(2, before / 40),
                "one bad sample moved the bucket from " + before + " to " + after);
    }

    @Test
    @DisplayName("but a real change is learned within a couple of days")
    void itDoesAdapt() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        final int before = memory.expectedFePerTick(6_000);

        for (long t = 0; t < 2L * FeSupplyMemory.DAY_TICKS; t += SAMPLE_TICKS) {
            memory.observe(t, solarDay(t, 720));
        }
        final int after = memory.expectedFePerTick(6_000);
        assertTrue(after > before * 3 / 2,
                "doubled the roof and the model still said " + after + " against " + before);
        assertTrue(after <= 720, "learned more than ever actually arrived: " + after);
    }

    @Test
    @DisplayName("a floor over a window with a cold hour in it is unknown, not a number")
    void theFloorRefusesToGuess() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        for (int i = 0; i < 40; i++) {
            memory.observe(6_000, 300);
        }
        assertEquals(300, memory.expectedFloorOver(6_000, 500), "inside one warm bucket");
        assertEquals(FeSupplyMemory.UNKNOWN, memory.expectedFloorOver(6_000, 4_000),
                "a window crossing a bucket it has never watched");
    }

    @Test
    @DisplayName("it survives a save and a reload, and refuses a save of the wrong shape")
    void persistence() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        final FeSupplyMemory restored = new FeSupplyMemory();
        restored.restore(memory.snapshotRates(), memory.snapshotSamples());
        for (int t = 0; t < FeSupplyMemory.DAY_TICKS; t += 500) {
            assertEquals(memory.expectedFePerTick(t), restored.expectedFePerTick(t),
                    "day time " + t + " came back different");
        }

        final FeSupplyMemory odd = new FeSupplyMemory();
        odd.restore(new int[] {1, 2, 3}, new int[] {99, 99, 99});
        assertEquals(FeSupplyMemory.UNKNOWN, odd.expectedFePerTick(0));
        odd.restore(null, null);
        assertEquals(FeSupplyMemory.UNKNOWN, odd.expectedFePerTick(0));
    }

    @Test
    @DisplayName("day time wraps, and a bucket is the same bucket a week later")
    void dayTimeWraps() {
        assertEquals(FeSupplyMemory.bucketOf(6_000),
                FeSupplyMemory.bucketOf(6_000 + 7L * FeSupplyMemory.DAY_TICKS));
        assertEquals(FeSupplyMemory.bucketOf(6_000), FeSupplyMemory.bucketOf(6_000 - FeSupplyMemory.DAY_TICKS));
        assertEquals(0, FeSupplyMemory.bucketOf(0));
        assertEquals(FeSupplyMemory.BUCKETS - 1, FeSupplyMemory.bucketOf(FeSupplyMemory.DAY_TICKS - 1));
        assertNotEquals(FeSupplyMemory.bucketOf(0), FeSupplyMemory.bucketOf(FeSupplyMemory.BUCKET_TICKS));
    }

    @Test
    @DisplayName("a projection past a full day wraps round the clock rather than running off it")
    void projectionWrapsTheClock() {
        final FeSupplyMemory memory = new FeSupplyMemory();
        runDays(memory, 4, 360);
        final int oneDay = memory.projectedFeOver(0, FeSupplyMemory.DAY_TICKS);
        final int twoDays = memory.projectedFeOver(0, FeSupplyMemory.DAY_TICKS * 2);
        assertEquals(oneDay * 2, twoDays, oneDay / 100.0,
                "two days should be twice one day, so the second lap read the same buckets");
    }
}
