package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrainBankLedgerTest {
    @Test
    @DisplayName("fills the bottom cell first and drains the top cell first, like a bank does")
    void stackOrder() {
        final TrainBankLedger ledger = new TrainBankLedger(new int[]{0, 0, 0}, new int[]{100, 100, 100});
        assertEquals(150, ledger.fill(150));
        assertEquals(100, ledger.energyAt(0));
        assertEquals(50, ledger.energyAt(1));
        assertEquals(0, ledger.energyAt(2));
        assertEquals(70, ledger.drain(70));
        assertEquals(80, ledger.energyAt(0));
        assertEquals(0, ledger.energyAt(1));
    }

    @Test
    @DisplayName("a merged bank's uneven shares are respected cell by cell")
    void unevenShares() {
        final TrainBankLedger ledger = new TrainBankLedger(new int[]{0, 0}, new int[]{1071, 1070});
        assertEquals(2141, ledger.fill(5000));
        assertEquals(2141, ledger.capacity());
        assertEquals(0, ledger.room());
    }

    @Test
    @DisplayName("a cell over its capacity is full: it takes nothing, gives everything, loses nothing")
    void overfullCell() {
        final TrainBankLedger ledger = new TrainBankLedger(new int[]{120}, new int[]{100});
        assertEquals(0, ledger.fill(50));
        assertEquals(120, ledger.stored());
        assertEquals(120, ledger.capacity(), "capacity is never reported below charge");
        assertEquals(120, ledger.drain(500));
    }

    @Test
    @DisplayName("twenty six huge banks saturate rather than wrapping negative (INV-14)")
    void totalsSaturate() {
        final int[] e = new int[26];
        final int[] c = new int[26];

        java.util.Arrays.fill(e, 90_000_000);
        java.util.Arrays.fill(c, 90_000_000);
        final TrainBankLedger ledger = new TrainBankLedger(e, c);
        assertEquals(Integer.MAX_VALUE, ledger.stored());
        assertEquals(Integer.MAX_VALUE, ledger.capacity());
        assertEquals(1.0, ledger.fraction());
    }

    @Test
    @DisplayName("negative inputs are read as empty, and the caller's arrays are never written")
    void sanitisedAndCopied() {
        final int[] e = {-5, 10};
        final int[] c = {100, 100};
        final TrainBankLedger ledger = new TrainBankLedger(e, c);
        ledger.fill(50);
        assertArrayEquals(new int[]{-5, 10}, e);
        assertEquals(60, ledger.stored());
    }

    @Test
    @DisplayName("nothing aboard reads as zero, never as a division by zero")
    void empty() {
        final TrainBankLedger ledger = new TrainBankLedger(new int[0], new int[0]);
        assertEquals(0, ledger.capacity());
        assertEquals(0.0, ledger.fraction());
        assertEquals(0, TrainBankLedger.comparatorLevel(0, 0));
    }

    @Test
    @DisplayName("comparator: 1 when connected and empty, 15 when full")
    void comparator() {
        assertEquals(1, TrainBankLedger.comparatorLevel(0, 100));
        assertEquals(15, TrainBankLedger.comparatorLevel(100, 100));
        assertEquals(8, TrainBankLedger.comparatorLevel(50, 100));
    }
}
