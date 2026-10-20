package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoseRuleTest {
    private static final int NORTH_X = 0;
    private static final int NORTH_Z = -1;

    private static List<double[]> row(int n, double x, double z) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new double[] {x, 0.0, z, 1.0});
        return out;
    }

    @Test
    void theOwnersZeppelinFacesWhereItsNinePropellersPush() {
        assertEquals(1, NoseRule.proposeTurns(-1, 0, row(9, 0.0, -1.0)));
    }

    @Test
    void aFacingAlongTheEnginesStandsEitherWayAlongThem() {
        assertEquals(0, NoseRule.proposeTurns(NORTH_X, NORTH_Z, row(4, 0.0, -1.0)), "facing where they point");
        assertEquals(0, NoseRule.proposeTurns(0, 1, row(4, 0.0, -1.0)), "facing the other way along them: the player's bow");
    }

    @Test
    void aFacingAcrossIsTurnedTowardWhereThePropellersPoint() {
        assertEquals(1, NoseRule.proposeTurns(0, 1, row(2, -1.0, 0.0)), "facing south, pointing west: a quarter clockwise");
        assertEquals(3, NoseRule.proposeTurns(0, 1, row(2, 1.0, 0.0)), "facing south, pointing east: three quarters");
        assertEquals(1, NoseRule.proposeTurns(1, 0, row(1, 0.0, 1.0)), "facing east, one pointing south: a quarter clockwise");
    }

    @Test
    void aMajorityOfPropellersPicksTheBow() {
        List<double[]> p = row(3, 0.0, 1.0);
        p.addAll(row(2, 0.0, -1.0));
        assertEquals(1, NoseRule.proposeTurns(1, 0, p), "facing east, three point south and two north: south, a quarter clockwise");
    }

    @Test
    void propellersEvenlyBothWaysAlongTheAxisTurnAQuarterClockwise() {
        List<double[]> p = row(2, 0.0, 1.0);
        p.addAll(row(2, 0.0, -1.0));
        assertEquals(1, NoseRule.proposeTurns(-1, 0, p));
    }

    @Test
    void noClearAxisLeavesTheFacing() {
        List<double[]> quad = row(1, 0.0, 1.0);
        quad.addAll(row(1, 0.0, -1.0));
        quad.addAll(row(1, 1.0, 0.0));
        quad.addAll(row(1, -1.0, 0.0));
        assertEquals(0, NoseRule.proposeTurns(-1, 0, quad), "a quad pushing every way");
        assertEquals(0, NoseRule.proposeTurns(-1, 0, List.of()), "no engines at all");
        assertEquals(0, NoseRule.proposeTurns(-1, 0, List.of(new double[] {0.0, 1.0, 0.0, 5.0})), "a lifting rotor only");
    }

    @Test
    void strongerPropellersCountForMore() {
        List<double[]> p = new ArrayList<>();
        p.add(new double[] {1.0, 0.0, 0.0, 1.0});
        p.add(new double[] {0.0, 0.0, -1.0, 10.0});
        assertEquals(1, NoseRule.proposeTurns(-1, 0, p), "one big bearing pushing north outweighs a small prop pushing east");
    }
}
