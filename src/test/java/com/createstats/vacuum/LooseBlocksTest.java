package com.createstats.vacuum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LooseBlocksTest {
    @Test
    void grainsThatFallAreLoose() {
        assertTrue(LooseBlocks.isLoose(false, true, false, 0.5f), "sand");
        assertTrue(LooseBlocks.isLoose(false, true, false, 0.6f), "gravel");
        assertTrue(LooseBlocks.isLoose(false, true, false, LooseBlocks.MAX_GRAIN_HARDNESS), "the heaviest grain");
    }

    @Test
    void heavyThingsThatAlsoFallAreNot() {
        assertFalse(LooseBlocks.isLoose(false, true, false, 3.0f), "dragon egg");
        assertFalse(LooseBlocks.isLoose(false, true, false, 5.0f), "anvil");
    }

    @Test
    void breakingInstantlyIsNotEnough() {
        assertFalse(LooseBlocks.isLoose(false, false, false, 0.0f));
    }

    @Test
    void theTagCoversDebrisHowToughSoever() {
        assertTrue(LooseBlocks.isLoose(true, false, false, 0.0f), "short grass");
        assertTrue(LooseBlocks.isLoose(true, false, false, 0.1f), "snow layer");
        assertTrue(LooseBlocks.isLoose(true, false, false, 4.0f), "cobweb");
    }

    @Test
    void neverABlockEntityOrAnUnbreakableBlock() {
        assertFalse(LooseBlocks.isLoose(true, true, true, 0.5f), "a tagged, falling block with a block entity");
        assertFalse(LooseBlocks.isLoose(true, false, false, -1.0f), "bedrock, even if a pack tags it");
        assertFalse(LooseBlocks.isLoose(true, true, false, Float.NaN), "a nonsense hardness");
    }

    @Test
    void shovelGroundGoesAtANetheriteShovelsPace() {
        assertEquals(2, LooseBlocks.ticksToLift(0.5f, true, false), "sand, and concrete powder");
        assertEquals(3, LooseBlocks.ticksToLift(0.6f, true, false), "gravel");
        assertEquals(1, LooseBlocks.ticksToLift(0.1f, true, true), "a snow layer, which a shovel harvests");
        assertEquals(4, LooseBlocks.ticksToLift(1.0f, true, false), "the heaviest grain there is");
        assertTrue(LooseBlocks.ticksToLift(0.5f, true, false) < LooseBlocks.ticksBefore(0.5f),
                "sand goes faster than it did in 1.14.37");
    }

    @Test
    void debrisGoesAtOnceAndACobwebNeverSlowsDown() {
        assertEquals(1, LooseBlocks.ticksToLift(0.0f, false, false), "grass, flowers, petals");
        assertEquals(3, LooseBlocks.ticksToLift(0.1f, false, false), "moss carpet, bare handed");
        assertEquals(36, LooseBlocks.ticksToLift(4.0f, false, true),
                "a cobweb: a shovel is no tool for it and a hand takes 400 ticks, so the 1.14.37 time stands");
    }

    @Test
    void liftTimeIsClampedAndGrowsWithHardness() {
        assertEquals(1, LooseBlocks.ticksToLift(-1.0f, true, false), "a nonsense hardness goes at once");
        assertEquals(LooseBlocks.MAX_LIFT_TICKS, LooseBlocks.ticksToLift(50.0f, false, true), "clamped above");
        for (boolean shovel : new boolean[] {true, false}) {
            for (boolean needs : new boolean[] {true, false}) {
                for (int h = 0; h < 12; h++) {
                    assertTrue(LooseBlocks.ticksToLift(h + 1, shovel, needs) >= LooseBlocks.ticksToLift(h, shovel, needs),
                            "monotonic at " + h + ", shovel " + shovel + ", needs a tool " + needs);
                }
            }
        }
    }

    @Test
    void cracksRunZeroToNineAndNeverTen() {
        final int needed = LooseBlocks.ticksToLift(4.0f, false, true);
        assertEquals(0, LooseBlocks.crackStage(0, needed));
        int last = 0;
        for (int t = 1; t <= needed + 3; t++) {
            final int stage = LooseBlocks.crackStage(t, needed);
            assertTrue(stage >= last && stage <= 9, "tick " + t + " stage " + stage);
            last = stage;
        }
        assertEquals(9, LooseBlocks.crackStage(needed, needed));
        assertEquals(0, LooseBlocks.crackStage(3, 0), "a zero length lift draws nothing");
    }
}
