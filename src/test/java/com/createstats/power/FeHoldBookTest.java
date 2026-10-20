package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeHoldBookTest {
    @Test
    void aSmallerRequestRefreshedLaterNoLongerReplacesALargerOneStillLive() {
        final FeHoldBook book = new FeHoldBook();
        book.accept(1L, 5_000_000, 110L);
        book.accept(2L, 1_000_000, 111L);
        assertEquals(5_000_000, book.largest(100L).fe(), "the larger request is still live, so it holds");

        int held = 5_000_000;
        long heldUntil = 110L;
        final int want = 1_000_000;
        final long until = 111L;
        if (!(until <= heldUntil && want < held)) {
            held = want;
            heldUntil = until;
        }
        assertEquals(1_000_000, held);
        assertEquals(111L, heldUntil);
    }

    @Test
    void eachAskerHoldsUntilItsOwnExpiryAndThenTheNextLargestDoes() {
        final FeHoldBook book = new FeHoldBook();
        book.accept(1L, 5_000_000, 110L);
        book.accept(2L, 1_000_000, 200L);
        assertEquals(5_000_000, book.largest(110L).fe());
        assertEquals(1_000_000, book.largest(111L).fe(), "the large one lapsed; the small one is still asked");
        assertFalse(book.largest(201L).isPresent(), "and then nothing: silence means no hold");
        assertEquals(0, book.askers(201L));
    }

    @Test
    void anAskerReplacesOnlyItsOwnRequestAndZeroWithdrawsIt() {
        final FeHoldBook book = new FeHoldBook();
        book.accept(1L, 5_000_000, 500L);
        book.accept(2L, 3_000_000, 500L);
        book.accept(1L, 2_000_000, 500L);
        assertEquals(3_000_000, book.largest(100L).fe(), "board 1 lowered its own request, board 2's stands");
        book.accept(2L, 0, 500L);
        assertEquals(2_000_000, book.largest(100L).fe());
        assertEquals(1, book.askers(100L));
        assertTrue(book.largest(100L).isPresent());
    }

    @Test
    void theUnnamedFormIsOneAskerLikeAnyOther() {
        final FeHoldBook book = new FeHoldBook();
        book.accept(FeHoldBook.UNNAMED, 700, 50L);
        book.accept(FeHoldBook.UNNAMED, 400, 60L);
        assertEquals(400, book.largest(10L).fe(), "one unnamed asker, so its newer request is its request");
        book.clear();
        assertFalse(book.largest(10L).isPresent());
    }
}
