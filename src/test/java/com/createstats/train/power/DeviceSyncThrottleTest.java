package com.createstats.train.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceSyncThrottleTest {
    @Test
    @DisplayName("the first value is always sent")
    void firstIsSent() {
        assertTrue(new DeviceSyncThrottle<String>().chargeWorthSending("a", 5, 6_400, 0));
    }

    @Test
    @DisplayName("a move under a 64th waits, a move of a 64th goes")
    void bucket() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        t.sent("a", 1_000, 0);
        assertFalse(t.chargeWorthSending("a", 1_099, 6_400, 1));
        assertTrue(t.chargeWorthSending("a", 1_100, 6_400, 1));
    }

    @Test
    @DisplayName("reaching empty or full goes at once, however small the step")
    void edges() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        t.sent("a", 3, 0);
        assertTrue(t.chargeWorthSending("a", 0, 6_400, 1));
        t.sent("b", 6_398, 0);
        assertTrue(t.chargeWorthSending("b", 6_400, 6_400, 1));
    }

    @Test
    @DisplayName("a small difference is sent once it has waited SETTLE_TICKS")
    void settles() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        t.sent("a", 1_000, 100);
        assertFalse(t.chargeWorthSending("a", 1_001, 6_400, 100 + DeviceSyncThrottle.SETTLE_TICKS - 1));
        assertTrue(t.chargeWorthSending("a", 1_001, 6_400, 100 + DeviceSyncThrottle.SETTLE_TICKS));
        assertFalse(t.chargeWorthSending("a", 1_000, 6_400, 10_000), "an unchanged value is never resent");
    }

    @Test
    @DisplayName("a rate moves on a sixteenth of itself, like the ground bank's face")
    void rates() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        t.sent("r", 1_600, 0);

        assertFalse(t.rateWorthSending("r", 1_699, 1));
        assertFalse(t.rateWorthSending("r", 1_705, 1));
        assertTrue(t.rateWorthSending("r", 1_706, 1));
        t.sent("z", 3, 0);
        assertTrue(t.rateWorthSending("z", 0, 1), "stopping is always news");
        t.sent("n", -1_600, 0);
        assertFalse(t.rateWorthSending("n", -1_699, 1), "a draining rate is bucketed the same way");
        assertTrue(t.rateWorthSending("n", 1_600, 1), "a sign change is a real move");
    }

    @Test
    @DisplayName("a rate difference also settles")
    void rateSettles() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        t.sent("r", 1_600, 0);
        assertFalse(t.rateWorthSending("r", 1_601, DeviceSyncThrottle.SETTLE_TICKS - 1));
        assertTrue(t.rateWorthSending("r", 1_601, DeviceSyncThrottle.SETTLE_TICKS));
    }

    @Test
    @DisplayName("differs is false for an unknown key and for the value last sent")
    void differs() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        assertFalse(t.differs("a", 5));
        t.sent("a", 5, 0);
        assertFalse(t.differs("a", 5));
        assertTrue(t.differs("a", 6));
    }

    @Test
    @DisplayName("retain forgets keys, which are then sent fresh")
    void retainForgets() {
        final DeviceSyncThrottle<String> t = new DeviceSyncThrottle<>();
        t.sent("a", 1_000, 0);
        t.retain(k -> !k.equals("a"));
        assertTrue(t.chargeWorthSending("a", 1_000, 6_400, 1));
        assertEquals(0, t.size());
    }
}
