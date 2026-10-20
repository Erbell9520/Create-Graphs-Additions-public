package com.createstats.power;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FeStackFillTest {
    private static final int CAP = 1000;

    private static int total(int[] energies) {
        return Arrays.stream(energies).sum();
    }

    @Test
    void fillingLandsInTheLowestBlockFirst() {
        int[] stack = new int[4];
        assertEquals(2_500, FeStackFill.fillBottomUp(stack, CAP, 2_500));
        assertArrayEquals(new int[]{1000, 1000, 500, 0}, stack);
        assertEquals(2_500, total(stack));
    }

    @Test
    void drainingEmptiesTheHighestBlockFirst() {
        int[] stack = {1000, 1000, 500, 0};
        assertEquals(700, FeStackFill.drainTopDown(stack, 700));
        assertArrayEquals(new int[]{1000, 800, 0, 0}, stack);
        assertEquals(1_800, total(stack));
    }

    @Test
    void fillThenDrainReturnsTheStackToWhereItStarted() {
        int[] stack = new int[6];
        final int placed = FeStackFill.fillBottomUp(stack, CAP, 3_333);
        final int taken = FeStackFill.drainTopDown(stack, placed);
        assertEquals(placed, taken, "everything put in comes back out");
        assertArrayEquals(new int[6], stack);
    }

    @Test
    void aFullStackRefusesTheOverflowRatherThanSwallowingIt() {
        int[] stack = new int[3];
        assertEquals(3_000, FeStackFill.fillBottomUp(stack, CAP, 9_999));
        assertEquals(3_000, total(stack), "capacity is 3 x 1000 and not one FE more");
        assertEquals(0, FeStackFill.fillBottomUp(stack, CAP, 500), "a full stack accepts nothing");
        assertEquals(3_000, total(stack));
    }

    @Test
    void anEmptyStackGivesNothingRatherThanGoingNegative() {
        int[] stack = new int[3];
        assertEquals(0, FeStackFill.drainTopDown(stack, 500));
        assertArrayEquals(new int[3], stack);

        int[] partial = {200, 0, 0};
        assertEquals(200, FeStackFill.drainTopDown(partial, 5_000), "gives what it has, no more");
        assertArrayEquals(new int[3], partial);
    }

    @Test
    void repeatedPartialCyclesConserveExactly() {
        int[] stack = new int[5];
        int inWorld = 0;
        for (int round = 0; round < 50; round++) {
            inWorld += FeStackFill.fillBottomUp(stack, CAP, 777);
            inWorld -= FeStackFill.drainTopDown(stack, 300);
            assertEquals(inWorld, total(stack), "conserved on round " + round);
        }
    }

    @Test
    void gapsInTheStackAreToppedUpBeforeMovingOn() {
        int[] stack = {1000, 0, 1000, 0};
        assertEquals(1_500, FeStackFill.fillBottomUp(stack, CAP, 1_500));
        assertArrayEquals(new int[]{1000, 1000, 1000, 500}, stack);
    }

    @Test
    void zeroAndNegativeRequestsAreNoOps() {
        int[] stack = {500, 500};
        assertEquals(0, FeStackFill.fillBottomUp(stack, CAP, 0));
        assertEquals(0, FeStackFill.fillBottomUp(stack, CAP, -100));
        assertEquals(0, FeStackFill.drainTopDown(stack, 0));
        assertEquals(0, FeStackFill.drainTopDown(stack, -100));
        assertArrayEquals(new int[]{500, 500}, stack);
    }
}
