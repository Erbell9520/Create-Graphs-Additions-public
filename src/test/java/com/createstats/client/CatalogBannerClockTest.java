package com.createstats.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogBannerClockTest {
    private static final CatalogBannerSpec SPEC = CatalogBannerSpec.POWER;
    private static final long FRAME = SPEC.frameTicks * (long) CatalogBannerSpec.TICK_MILLIS;

    @Test
    void atRestNothingIsLive() {
        final CatalogBannerClock clock = new CatalogBannerClock(SPEC);
        clock.update(false, 1000L);
        clock.update(false, 1016L);
        assertEquals(0, clock.liveFrame(1016L));
        assertEquals(0.0F, clock.power(), 0.0F);
    }

    @Test
    void theIntroPlaysOnceThenTheLoopCycles() {
        final CatalogBannerClock clock = new CatalogBannerClock(SPEC);
        final long t0 = 5000L;
        clock.update(true, t0);
        assertEquals(1, clock.liveFrame(t0), "the first intro frame the moment the cursor arrives");
        assertEquals(1 + SPEC.intro - 1, clock.liveFrame(t0 + FRAME * (SPEC.intro - 1)), "the last intro frame");
        assertEquals(1 + SPEC.intro, clock.liveFrame(t0 + FRAME * SPEC.intro), "the loop's first frame");
        final long loopEnd = t0 + FRAME * (SPEC.intro + SPEC.loop);
        assertEquals(1 + SPEC.intro + SPEC.loop - 1, clock.liveFrame(loopEnd - 1));
        assertEquals(1 + SPEC.intro, clock.liveFrame(loopEnd), "the loop wraps to its own start, not the intro");
        for (long t = t0; t < t0 + 60_000L; t += 37L) {
            final int f = clock.liveFrame(t);
            assertTrue(f >= 1 && f < SPEC.frames(), "frame " + f + " is outside the strip");
        }
    }

    @Test
    void powerRisesQuicklyAndTheSameAtAnyFrameRate() {
        final float slow = powerAfter(250L, 33L);
        final float fast = powerAfter(250L, 4L);
        assertTrue(slow > 0.9F && fast > 0.9F, "powered up within a quarter second: " + slow + ", " + fast);
        assertEquals(slow, fast, 0.03F);
    }

    private static float powerAfter(long millis, long step) {
        final CatalogBannerClock clock = new CatalogBannerClock(SPEC);
        for (long t = 0L; t <= millis; t += step) {
            clock.update(true, 10_000L + t);
        }
        return clock.power();
    }

    @Test
    void leavingFadesOutStillMovingThenRestsAndReplaysThePowerUp() {
        final CatalogBannerClock clock = new CatalogBannerClock(SPEC);
        long t = 0L;
        for (; t <= 2000L; t += 16L) {
            clock.update(true, t);
        }
        assertEquals(1.0F, clock.power(), 0.0F);
        clock.update(false, t);
        t += 16L;
        clock.update(false, t);
        assertTrue(clock.power() < 1.0F && clock.power() > 0.0F, "fading");
        assertTrue(clock.liveFrame(t) > SPEC.intro, "still looping while it fades");
        for (; t <= 5000L; t += 16L) {
            clock.update(false, t);
        }
        assertEquals(0.0F, clock.power(), 0.0F);
        assertEquals(0, clock.liveFrame(t), "back at rest");
        clock.update(true, t);
        assertEquals(1, clock.liveFrame(t), "a fresh hover powers up from the start again");
    }

    @Test
    void comingBackMidFadeCarriesOnInsteadOfRestarting() {
        final CatalogBannerClock clock = new CatalogBannerClock(SPEC);
        long t = 0L;
        for (; t <= 1000L; t += 16L) {
            clock.update(true, t);
        }
        for (int k = 0; k < 4; k++, t += 16L) {
            clock.update(false, t);
        }
        clock.update(true, t);
        assertTrue(clock.liveFrame(t) > SPEC.intro, "no second power-up for a cursor that only slipped off");
    }

    @Test
    void anAltTabDoesNotSnapThePower() {
        final CatalogBannerClock clock = new CatalogBannerClock(SPEC);
        clock.update(true, 0L);
        clock.update(true, 30_000L);
        final float oneStep = 1.0F - (float) Math.exp(-CatalogBannerClock.RATE_UP * CatalogBannerClock.MAX_STEP_SECONDS);
        assertEquals(oneStep, clock.power(), 1e-4F);
    }

    @Test
    void everyStripHasAtLeastAFrameToShowWhenLive() {
        for (CatalogBannerSpec spec : CatalogBannerSpec.values()) {
            assertTrue(spec.intro + spec.loop > 0, spec + " has nothing to show when hovered");
            assertEquals(spec.frames() * CatalogBannerSpec.FRAME_HEIGHT, spec.textureHeight());
        }
    }
}
