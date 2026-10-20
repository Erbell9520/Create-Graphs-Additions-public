package com.createstats.powertrack;

import com.createstats.powertrack.core.RailPower;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackFactsTest {
    @Test
    @DisplayName("each state reads as what the rail is doing, and a train with no collectors shows nothing")
    void lines() {
        assertEquals("no live rail", PowerTrackFacts.line(PowerTrackService.TrainFact.UNPOWERED, 0));
        assertEquals("in a rail gap", PowerTrackFacts.line(PowerTrackService.TrainFact.IN_GAP, 0));
        assertEquals("live rail, idle", PowerTrackFacts.line(PowerTrackService.TrainFact.LIVE_IDLE, 0));
        assertEquals("charging 2.0k FE/t", PowerTrackFacts.line(PowerTrackService.TrainFact.CHARGING, 2048));
        assertEquals("giving 512 FE/t", PowerTrackFacts.line(PowerTrackService.TrainFact.GIVING, -512));
        assertEquals("charging 16k FE/t", PowerTrackFacts.line(PowerTrackService.TrainFact.CHARGING, 16_384));
        assertEquals("", PowerTrackFacts.line(PowerTrackService.TrainFact.NO_COLLECTORS, 0));
    }

    @Test
    @DisplayName("no line is ever longer than the page's narrowest value column shows, for any train up to sixty cars")
    void fitsTheColumn() {
        final int perCar = RailPower.carRate(8192, RailPower.MAX_COLLECTORS_PER_CAR);
        for (int state = 0; state <= PowerTrackService.TrainFact.IN_GAP; state++) {
            for (int cars = 0; cars <= 60; cars++) {
                for (int sign : new int[]{1, -1}) {
                    final String line = PowerTrackFacts.line(state, sign * cars * perCar);
                    assertTrue(line.length() <= PowerTrackFacts.MAX_CHARS, "'" + line + "' is too long");
                }
            }
        }
    }
}
