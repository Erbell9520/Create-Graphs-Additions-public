package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CouplerRatingTest {
    private static final int STORE = 16384;

    @Test
    @DisplayName("at both mods' defaults the rating is 8,192, the figure the block always quoted")
    void defaults() {
        assertEquals(8192, CouplerRating.rating(8192, 8192L, STORE));
        assertEquals(8192, CouplerRating.rating(8192, 0L, STORE), "no New Age: our base");
    }

    @Test
    @DisplayName("a pack that raises their multiplier raises the rating, held to the store")
    void followsTheirMultiplier() {
        assertEquals(12288, CouplerRating.rating(8192, 12288L, STORE));
        assertEquals(STORE, CouplerRating.rating(8192, 8192L * 4, STORE),
                "the store is the per-tick ceiling (§3.6); a rating above it is a figure the block cannot reach");
    }

    @Test
    @DisplayName("a pack that lowers our base keeps their ceiling; a wired seam prints the bound that binds")
    void bindingBound() {
        final int rating = CouplerRating.rating(2048, 8192L, STORE);
        assertEquals(8192, rating);
        assertEquals(2048, CouplerRating.binding(rating, 2048), "a Copper Wire at base 2,048 binds");
        assertEquals(8192, CouplerRating.binding(rating, 16384), "an Insulated Wire does not");
        assertEquals(8192, CouplerRating.binding(rating, -1), "bolted: no wire");
    }

    @Test
    @DisplayName("never zero, never negative")
    void floor() {
        assertEquals(1, CouplerRating.rating(0, 0L, STORE));
        assertEquals(1, CouplerRating.rating(-5, -5L, STORE));
        assertEquals(0, CouplerRating.binding(8192, 0));
    }
}
