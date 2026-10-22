package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerPriceTest {
    private static final int RATE = 8192;

    @Test
    @DisplayName("a PUSH Coupler is priced at what their side accepts, held to the rating")
    void pushIsWhatTheyAccept() {
        assertEquals(50, CouplerPrice.pushPrice(RATE, 50, false, false), "a 50 FE/t lamp costs 50, not 8,192");
        assertEquals(RATE, CouplerPrice.pushPrice(RATE, 1_000_000, false, false));
        assertEquals(0, CouplerPrice.pushPrice(RATE, 0, false, false));

        final int oldPrice = RATE;
        assertTrue(oldPrice > CouplerPrice.pushPrice(RATE, 50, false, false));
    }

    @Test
    @DisplayName("force off and a loop zero the price; there is no shed argument at all, so shed cannot move it")
    void lockZeroesShedCannot() {
        assertEquals(0, CouplerPrice.pushPrice(RATE, 4000, true, false));
        assertEquals(0, CouplerPrice.pushPrice(RATE, 4000, false, true));

        assertEquals(4000, CouplerPrice.pushPrice(RATE, 4000, false, false));
    }

    @Test
    @DisplayName("a New Age feed is pulled generation less their own demand, held to the rating")
    void newAgeSupply() {
        assertEquals(3000, CouplerPrice.newAgeSupply(RATE, 5000, 2000));
        assertEquals(0, CouplerPrice.newAgeSupply(RATE, 1000, 2000), "their own consumers take it all");
        assertEquals(RATE, CouplerPrice.newAgeSupply(RATE, 100_000, 0));
        assertEquals(0, CouplerPrice.newAgeSupply(RATE, -5, -5));
        assertEquals(RATE, CouplerPrice.newAgeSupply(RATE, Integer.MAX_VALUE, Integer.MIN_VALUE), "no overflow");
    }

    @Test
    @DisplayName("any other feed is what it could give now, not what it gave: room to grow (INV-26)")
    void feedSupplyIsPotential() {
        assertEquals(6000, CouplerPrice.feedSupply(RATE, 6000));
        assertEquals(RATE, CouplerPrice.feedSupply(RATE, 50_000));
        assertEquals(0, CouplerPrice.feedSupply(RATE, -1));
    }

    @Test
    @DisplayName("smoothed: rises at once, falls a quarter of the gap a sample, never compounds within a tick")
    void smoothing() {
        final CouplerPrice.Smoothed s = new CouplerPrice.Smoothed();
        assertEquals(4000, s.observe(4000, 1));
        assertEquals(3000, s.observe(0, 2), "4000 - 4000/4");
        assertEquals(3000, s.observe(0, 2), "a second sample in the same tick does not fall again");
        assertEquals(5000, s.observe(5000, 2), "a rise in the same tick still lands");
        int v = s.value();
        for (long t = 3; t < 200; t++) {
            final int next = s.observe(0, t);
            assertTrue(next <= v);
            v = next;
        }
        assertEquals(0, v, "it reaches zero, never sticks one FE above it");
        s.reset();
        assertEquals(0, s.value());
    }
}
