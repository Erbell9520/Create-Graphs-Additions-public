package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BurstReliefTest {
    @Test
    @DisplayName("what each device was left short of, summed")
    void sums() {
        final BurstRelief r = new BurstRelief();
        r.add(500, 200, false, true);
        r.add(1_000, 1_000, false, true);
        r.add(300, 0, false, true);
        assertEquals(600, r.total());
    }

    @Test
    @DisplayName("a top-up past one device's cost does not pay for another device's shortfall")
    void noCredit() {
        final BurstRelief r = new BurstRelief();
        r.add(100, 5_000, false, true);
        r.add(400, 0, false, true);
        assertEquals(400, r.total());
    }

    @Test
    @DisplayName("a capacitor's own charging never counts, or it fires its sibling to feed it")
    void burstSourceExcluded() {
        final BurstRelief r = new BurstRelief();
        r.add(CapacitorDischarge.CHARGE_FE_PER_TICK, 0, true, true);
        assertEquals(0, r.total());
    }

    @Test
    @DisplayName("short of conductor is not short of supply: a burst crosses the same wire")
    void unreachableExcluded() {
        final BurstRelief r = new BurstRelief();
        r.add(8_000, 0, false, false);
        r.add(100, 40, false, true);
        assertEquals(60, r.total());
    }

    @Test
    @DisplayName("saturates rather than wrapping negative (INV-14), and ignores negative flows")
    void saturates() {
        final BurstRelief r = new BurstRelief();
        r.add(Integer.MAX_VALUE, 0, false, true);
        r.add(Integer.MAX_VALUE, 0, false, true);
        assertEquals(Integer.MAX_VALUE, r.total());
        final BurstRelief s = new BurstRelief();
        s.add(100, -500, false, true);
        assertEquals(100, s.total(), "a node that gave FE this tick was handed nothing");
    }
}
