package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PduLegFeedTest {
    @Test
    @DisplayName("a full panel is priced at its output, not at the 512 its banked buffer simulates")
    void aBufferedGeneratorIsPricedByOutput() {
        int leg = 0;
        for (int panel = 0; panel < 9; panel++) {
            leg = PduLegFeed.addToLeg(leg, PduLegFeed.price(true, 40, 512, 512), 4_096);
        }
        assertEquals(360, leg, "the roof makes 360, the simulate claimed 4,608");

        assertEquals(0, PduLegFeed.price(true, 0, 512, 512));
    }

    @Test
    @DisplayName("a source nobody can ask still falls back to its simulate, capped")
    void aForeignSourceUsesTheSimulate() {
        assertEquals(700, PduLegFeed.price(false, 0, 700, 1_024));
        assertEquals(1_024, PduLegFeed.price(false, 0, 5_000, 1_024));
        assertEquals(0, PduLegFeed.price(false, 0, -5, 1_024));
    }

    @Test
    @DisplayName("several feeds on one leg are priced no higher than the leg can carry")
    void theLegCapsItsSum() {
        int leg = 0;
        for (int source = 0; source < 5; source++) {
            leg = PduLegFeed.addToLeg(leg, 400, 1_024);
        }
        assertEquals(1_024, leg);
        assertEquals(1_024, PduLegFeed.addToLeg(leg, 400, 1_024), "a full leg takes no more");
        assertEquals(0, PduLegFeed.addToLeg(0, 400, 0), "a leg with no rating carries nothing");
    }
}
