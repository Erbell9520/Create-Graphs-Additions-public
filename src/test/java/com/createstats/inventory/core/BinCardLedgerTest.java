package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinCardLedgerTest {
    @Test
    void theBooksAlwaysBalanceAgainstTheStore() {
        final Random random = new Random(20261012L);
        for (int round = 0; round < 50; round++) {
            final String[] keys = {"iron", "coal", "bread", "bone", "sand", "gravel", "string"};
            final Map<String, Long> store = new HashMap<>();
            for (String k : keys) {
                store.put(k, (long) random.nextInt(500));
            }

            final StockFlow flow = new StockFlow();
            final BinCardLedger books = new BinCardLedger(4);
            long time = 0;
            flow.observe(time, store);
            books.open(0, store);
            for (int scan = 0; scan < 400; scan++) {
                time += 1 + random.nextInt(9);
                for (String k : keys) {
                    if (random.nextInt(3) == 0) {
                        store.put(k, Math.max(0L, store.get(k) + random.nextInt(41) - 20));
                    }
                }
                final Map<String, Long> deltas = flow.observe(time, store);
                books.apply((int) (time / 600), deltas);
            }
            long total = 0;
            for (String k : books.keys()) {
                final BinCardLedger.Card c = books.card(k);
                assertEquals(c.opening() + c.received() - c.issued(), c.balance());
            }
            for (long v : store.values()) {
                total += v;
            }
            assertEquals(total, books.total().balance(), "the books hold what the store holds, OTHER included");
            for (String k : books.keys()) {
                if (!k.equals(BinCardLedger.OTHER)) {
                    assertEquals((long) store.get(k), books.card(k).balance(), k);
                }
            }
        }
    }

    @Test
    void theNextDayRollsOnceAndKeepsTheClose() {
        final BinCardLedger b = new BinCardLedger();
        b.open(40, Map.of("iron", 100L));
        b.apply(40, Map.of("iron", 30L));
        b.apply(40, Map.of("iron", -10L));
        b.apply(41, Map.of("iron", 5L));
        final BinCardLedger.Card c = b.card("iron");
        assertTrue(c.hasYesterday());
        assertEquals(30L, c.yesterdayIn());
        assertEquals(10L, c.yesterdayOut());
        assertEquals(120L, c.yesterdayClose(), "the balance as day 40 ended");
        assertEquals(5L, c.todayIn());
        assertEquals(125L, c.balance());
        b.apply(41, Map.of("iron", 1L));
        assertEquals(30L, b.card("iron").yesterdayIn(), "a second booking the same day does not roll again");
    }

    @Test
    void aJumpOfSeveralDaysClearsYesterday() {
        final BinCardLedger b = new BinCardLedger();
        b.open(10, Map.of("coal", 50L));
        b.apply(10, Map.of("coal", 7L));
        b.apply(14, Map.of("coal", 1L));
        final BinCardLedger.Card c = b.card("coal");
        assertFalse(c.hasYesterday(), "day 13 was not seen; there is no yesterday to report");
        assertEquals(0L, c.yesterdayIn());
        assertEquals(1L, c.todayIn());
        assertEquals(58L, c.balance());
    }

    @Test
    void closingTheBooksStartsNewOnesFromTheBalance() {
        final BinCardLedger b = new BinCardLedger();
        b.open(37, Map.of("iron", 1000L));
        b.apply(37, Map.of("iron", 600L));
        b.apply(38, Map.of("iron", -200L));
        b.close(38);
        final BinCardLedger.Card c = b.card("iron");
        assertEquals(1400L, c.opening());
        assertEquals(0L, c.received());
        assertEquals(0L, c.issued());
        assertEquals(1400L, c.balance());
        assertEquals(38, b.openedDay());
        assertEquals(200L, c.todayOut(), "today's movement survives a close");
    }

    @Test
    void aReloadBooksWhatChangedWhileAway() {
        final BinCardLedger b = new BinCardLedger();
        b.open(5, Map.of("iron", 10L, "coal", 4L));
        b.reconcile(5, Map.of("iron", 25L, "gold", 3L));
        assertEquals(25L, b.card("iron").balance());
        assertEquals(0L, b.card("coal").balance());
        assertEquals(4L, b.card("coal").issued());
        assertEquals(3L, b.card("gold").received());
        assertEquals(28L, b.total().balance());
    }

    @Test
    void anExportedLedgerRestoresExactly() {
        final BinCardLedger b = new BinCardLedger();
        b.open(3, Map.of("a", 1L, "b", 2L));
        b.apply(4, Map.of("a", 5L, "b", -1L));
        final BinCardLedger r = new BinCardLedger();
        r.restore(b.today(), b.openedDay(), b.export());
        assertEquals(b.export(), r.export());
        assertEquals(4, r.today());
        assertEquals(3, r.openedDay());
    }

    @Test
    void theDayIsTheWorldsDay() {
        assertEquals(41, BinCardLedger.dayOf(41 * 24_000L + 9_000L));
        assertEquals(0, BinCardLedger.dayOf(23_999L));
    }
}
