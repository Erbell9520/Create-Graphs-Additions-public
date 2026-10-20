package com.createstats.battle.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BowGuessTest {
    private static BowGuess.Thrust push(double x, double y, double z, double w) {
        return new BowGuess.Thrust(x, y, z, w);
    }

    @Test
    void twoPropellersPushingTheSameWayNameTheBow() {
        assertEquals(1, BowGuess.guess(List.of(push(0, 0, 1, 10), push(0, 0, 1, 10))));
    }

    @Test
    void oneStrayThrusterUnderSeventyPercentGivesNoBow() {
        assertEquals(-1, BowGuess.guess(List.of(push(0, 0, 1, 10), push(0, 0, 1, 10), push(-1, 0, 0, 10))));
    }

    @Test
    void aSinglePropellerIsNotEnough() {
        assertEquals(-1, BowGuess.guess(List.of(push(1, 0, 0, 50))));
    }

    @Test
    void liftFansSayNothing() {
        assertEquals(-1, BowGuess.guess(List.of(push(0, 1, 0, 30), push(0, -1, 0, 30))));
        assertEquals(2, BowGuess.guess(List.of(push(0, 1, 0, 30), push(-1, 0, 0, 5), push(-1, 0.2, 0, 5))));
    }

    @Test
    void aMostlyVerticalPropellerDoesNotCountAsPointingTheWay() {
        assertEquals(-1, BowGuess.guess(List.of(push(0.3, 1, 0, 10), push(1, 0, 0, 10))));
    }
}
