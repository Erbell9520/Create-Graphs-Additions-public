package com.createstats.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PduBankDrawTest {
    private static final int CAP = 512_000;
    private static final int FLOOR = Math.round(CAP * 0.15f);

    @Test
    void aBufferWellAboveItsFloorCoversTheTickOnItsOwn() {
        assertEquals(0, PduBankDraw.shortfall(8_704, 16_384, Math.round(CAP * 0.26f), FLOOR));
    }

    @Test
    void theBankStartsAtTheFloorNotAtAnEmptyBuffer() {
        assertEquals(6_704, PduBankDraw.shortfall(8_704, 16_384, FLOOR + 2_000, FLOOR));

        assertEquals(8_704, PduBankDraw.shortfall(8_704, 16_384, FLOOR, FLOOR));

        assertEquals(0, Math.max(0, Math.min(8_704, 16_384) - FLOOR));
    }

    @Test
    void belowTheFloorTheBankCoversTheWholeTickAndNoMore() {
        assertEquals(8_704, PduBankDraw.shortfall(8_704, 16_384, FLOOR / 2, FLOOR));
        assertEquals(8_704, PduBankDraw.shortfall(8_704, 16_384, 0, FLOOR));
    }

    @Test
    void theWiringStillBoundsIt() {
        assertEquals(16_384, PduBankDraw.shortfall(40_000, 16_384, FLOOR, FLOOR));
        assertEquals(0, PduBankDraw.shortfall(8_704, 0, 0, FLOOR));
    }

    @Test
    void nothingNegativeGetsThrough() {
        assertEquals(0, PduBankDraw.shortfall(-5, 16_384, 0, FLOOR));
        assertEquals(100, PduBankDraw.shortfall(100, 16_384, 0, -50));
        assertEquals(0, PduBankDraw.shortfall(0, 16_384, CAP, FLOOR));
    }
}
