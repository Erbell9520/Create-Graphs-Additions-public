package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeDeviceDemandTest {
    @Test
    @DisplayName("an empty rated device costs its rating to run, not its buffer")
    void aColdRatedDeviceStillCostsItsRating() {
        final FeDeviceDemand cold = FeDeviceDemand.rated(16, 0, 0, 2000, 40);
        assertEquals(16, cold.steady(), "running cost is the rating whatever the buffer holds");
        assertEquals(50, cold.fill(), "and its buffer fills at 2,000 over 40 ticks");
        assertEquals(66, cold.total());
    }

    @Test
    @DisplayName("the same device full asks for nothing beyond its running cost")
    void aFullDeviceWantsNoTopUp() {
        final FeDeviceDemand warm = FeDeviceDemand.rated(16, 16, 2000, 2000, 40);
        assertEquals(16, warm.steady());
        assertEquals(0, warm.fill(), "there is no room, so there is nothing to ask for");
        assertEquals(16, warm.total());
    }

    @Test
    @DisplayName("the steady cost does not move as the buffer does, which is the whole point")
    void steadyIsBufferIndependent() {
        for (int stored = 0; stored <= 2000; stored += 250) {
            assertEquals(16, FeDeviceDemand.rated(16, 0, stored, 2000, 40).steady(),
                    "cost must not depend on how full it happens to be");
        }
    }

    @Test
    @DisplayName("a top-up is paced, never the whole hole at once")
    void fillIsPaced() {
        assertEquals(200, FeDeviceDemand.fillRate(0, 8000, 40));

        assertEquals(30, FeDeviceDemand.fillRate(7970, 8000, 40));
        assertEquals(0, FeDeviceDemand.fillRate(8000, 8000, 40));
    }

    @Test
    @DisplayName("a device with no readable buffer has nothing to fill")
    void noBufferNoFill() {
        assertEquals(0, FeDeviceDemand.fillRate(0, 0, 40));
        final FeDeviceDemand lamp = FeDeviceDemand.rated(4, 4, 0, 0, 40);
        assertEquals(0, lamp.fill());
        assertEquals(1.0f, lamp.fillFraction(), 0.0001f, "nothing to fill reads as already full");
        assertFalse(lamp.isCold(0.5f));
    }

    @Test
    @DisplayName("a consumer prefers its measurement over its nameplate, and still gets a fill term")
    void consumerKeepsTheOldRuleAndGainsATopUp() {
        final FeDeviceDemand running = FeDeviceDemand.consumer(90, 128, true, 500, 4000, 40);
        assertEquals(90, running.steady(), "measured beats the nameplate once it has run");
        assertEquals(100, running.fill());
        final FeDeviceDemand idle = FeDeviceDemand.consumer(0, 128, true, 4000, 4000, 40);
        assertEquals(128, idle.steady(), "and the nameplate carries it until then");
    }

    @Test
    @DisplayName("a browned-out consumer is priced at its nameplate, not at the size of its brownout")
    void aBrownOutIsNotACost() {
        final FeDeviceDemand starved = FeDeviceDemand.consumer(7, 128, false, 0, 4000, 40);
        assertEquals(128, starved.steady(), "the measurement measured the starvation, not the device");
        assertEquals(7, starved.reportedDraw(), "the row still shows what it actually took");

        final FeDeviceDemand dark = FeDeviceDemand.consumer(0, 128, false, 0, 4000, 40);
        assertEquals(128, dark.steady());
    }

    @Test
    @DisplayName("an unwatched foreign block is still priced at headroom, and says so")
    void opaqueIsHonestAboutBeingAGuess() {
        final FeDeviceDemand guess = FeDeviceDemand.opaque(512, 0, 8000, 40);
        assertEquals(512, guess.steady());
        assertFalse(guess.metered(), "everything downstream has to know this number is soft");
        assertEquals(0, guess.fill(), "headroom IS the top-up here, so billing one on top would double it");
    }

    @Test
    @DisplayName("once watched, the same block is an ordinary device with a cost and a top-up")
    void observedReplacesTheGuessEntirely() {
        final FeDeviceDemand learned = FeDeviceDemand.observed(96, 0, 0, 8000, 40);
        assertEquals(96, learned.steady(), "what it burns, not what it will accept");
        assertTrue(learned.metered());
        assertEquals(200, learned.fill(), "and its empty buffer is an ordinary paced top-up");
    }

    @Test
    @DisplayName("a board that cannot afford top-ups can strip them without touching the run cost")
    void fillCanBeDroppedOnItsOwn() {
        final FeDeviceDemand cold = FeDeviceDemand.rated(16, 0, 0, 2000, 40);
        final FeDeviceDemand lean = cold.withoutFill();
        assertEquals(16, lean.steady());
        assertEquals(0, lean.fill());
        assertEquals(16, lean.total());
    }

    @Test
    @DisplayName("coldness is measured against the buffer, so a starving device can be found")
    void coldnessIsReadable() {
        assertTrue(FeDeviceDemand.rated(16, 0, 100, 2000, 40).isCold(0.25f));
        assertFalse(FeDeviceDemand.rated(16, 16, 1900, 2000, 40).isCold(0.25f));
    }

    @Test
    @DisplayName("a learned price is held to what the device's node carries in a tick (KI-193)")
    void aLearnedPriceIsHeldToWhatItsNodeCarries() {
        final FeDeviceDemand arc = FeDeviceDemand.learned(160_000, 8192, 12_032, 1_280_000, 40);
        assertEquals(8192, arc.steady(), "never above what one run pass can hand it");
        assertTrue(arc.metered());
        assertEquals(0, arc.reportedDraw(), "the roster says what was handed over, as before");

        final FeDeviceDemand quiet = FeDeviceDemand.learned(300, 8192, 0, 2000, 40);
        assertEquals(FeDeviceDemand.observed(300, 0, 0, 2000, 40), quiet);
        assertEquals(0, FeDeviceDemand.learned(5000, 0, 0, 0, 40).steady(), "a node that carries nothing");
        assertEquals(0, FeDeviceDemand.learned(-1, 8192, 0, 0, 40).steady());
    }

    @Test
    @DisplayName("negatives never survive construction")
    void nothingGoesNegative() {
        final FeDeviceDemand odd = new FeDeviceDemand(-5, -5, -5, -5, -5, true);
        assertEquals(0, odd.steady());
        assertEquals(0, odd.fill());
        assertEquals(0, odd.reportedDraw());
        assertEquals(0, odd.stored());
        assertEquals(0, odd.capacity());
    }
}
