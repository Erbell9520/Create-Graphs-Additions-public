package com.createstats.stormcoil;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilVoiceTest {
    private static final long NOW = 10_000L;

    @Test
    void aDeadCoilIsSilent() {
        final CoilVoice v = new CoilVoice();
        v.tick(NOW, 0, false, false, Long.MIN_VALUE, false);
        assertEquals(0f, v.hum());
        assertEquals(0f, v.run());
    }

    @Test
    void aChargedCoilStandingByHumsAndDoesNotRoar() {
        final CoilVoice v = new CoilVoice();
        v.tick(NOW, 160_000, false, false, Long.MIN_VALUE, false);
        assertEquals(CoilVoice.HUM_VOLUME, v.hum());
        assertEquals(0f, v.run());
    }

    @Test
    void chargingRoarsEvenFromEmpty() {
        final CoilVoice v = new CoilVoice();
        v.tick(NOW, 0, true, false, Long.MIN_VALUE, false);
        assertEquals(CoilVoice.HUM_VOLUME, v.hum());
        assertEquals(CoilVoice.RUN_VOLUME, v.run());
    }

    @Test
    void theWindUpStartsTheRoarBeforeTheFirstCrack() {
        final CoilVoice v = new CoilVoice();
        v.tick(NOW, 1_280_000, false, true, Long.MIN_VALUE, false);
        assertEquals(CoilVoice.RUN_VOLUME, v.run());

        v.tick(NOW, 1_280_000, false, true, Long.MIN_VALUE, true);
        assertEquals(0f, v.run());
    }

    @Test
    void aFightHoldsTheRoarBetweenVolleysWithNothingDrawing() {
        final CoilVoice v = new CoilVoice();
        for (long t = 0; t < CoilVoice.RUN_HOLD; t++) {
            v.tick(NOW + t, 80_000, false, false, NOW, false);
            assertEquals(CoilVoice.RUN_VOLUME, v.run(), "tick " + t + " after a volley");
        }
        v.tick(NOW + CoilVoice.RUN_HOLD, 80_000, false, false, NOW, false);
        assertEquals(0f, v.run());

        assertTrue(CoilVoice.RUN_HOLD > CoilLaw.ZAP_INTERVAL);
    }

    @Test
    void noArcYetNeverReadsAsAFight() {
        final CoilVoice v = new CoilVoice();
        v.tick(NOW, 80_000, false, false, Long.MIN_VALUE, false);
        assertEquals(0f, v.run());
        v.tick(Long.MIN_VALUE + 5, 80_000, false, false, Long.MIN_VALUE, false);
        assertEquals(0f, v.run());
    }

    @Test
    void aHeldCoilStillRoarsWhileItChargesButNotForAnOldFight() {
        final CoilVoice v = new CoilVoice();
        v.tick(NOW + 5, 80_000, false, false, NOW, true);
        assertEquals(0f, v.run());
        v.tick(NOW + 5, 80_000, true, false, NOW, true);
        assertEquals(CoilVoice.RUN_VOLUME, v.run());
    }

    @Test
    void theRunSwellsInFastAndDiesAwaySlowlyNeverOvershooting() {
        float vol = 0f;
        int in = 0;
        while (vol < CoilVoice.RUN_VOLUME) {
            vol = CoilVoice.ease(vol, CoilVoice.RUN_VOLUME, CoilVoice.RUN_VOLUME, CoilVoice.RUN_IN, CoilVoice.RUN_OUT);
            assertTrue(vol <= CoilVoice.RUN_VOLUME);
            in++;
        }
        int out = 0;
        while (vol > 0f) {
            vol = CoilVoice.ease(vol, 0f, CoilVoice.RUN_VOLUME, CoilVoice.RUN_IN, CoilVoice.RUN_OUT);
            assertTrue(vol >= 0f);
            out++;
        }
        assertTrue(in <= CoilVoice.RUN_IN + 1, "swell in " + in);
        assertTrue(out >= CoilVoice.RUN_OUT - 1 && out <= CoilVoice.RUN_OUT + 1, "die away " + out);
    }
}
