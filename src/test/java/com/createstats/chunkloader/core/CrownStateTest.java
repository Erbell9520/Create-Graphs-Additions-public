package com.createstats.chunkloader.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownStateTest {
    private static CrownState random(Random r) {
        return new CrownState(r.nextInt(26), r.nextInt(26), r.nextInt(26), r.nextInt(1 << 25),
                CrownHold.Phase.values()[r.nextInt(3)], r.nextFloat(), r.nextLong() & Long.MAX_VALUE,
                r.nextLong() & Long.MAX_VALUE, r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt(51),
                r.nextInt(26), r.nextInt(1_000), r.nextInt(4), r.nextInt(65_537), r.nextInt(65_537), r.nextInt(),
                r.nextInt(16),
                CrownState.Reason.values()[r.nextInt(CrownState.Reason.values().length)], r.nextLong(),
                r.nextBoolean(), r.nextInt(3) - 1, r.nextInt(3), r.nextInt(3) - 1, "minecraft:stone_" + r.nextInt(9),
                r.nextInt(), r.nextInt());
    }

    @Test
    @DisplayName("every field survives the packing, so nothing the client draws is a field it was never sent")
    void everyFieldSurvivesTheWire() {
        final Random r = new Random(1_008L);
        for (int i = 0; i < 2_000; i++) {
            final CrownState s = random(r);
            assertEquals(s, CrownState.unpack(s.pack(), s.blockingId()));
        }
        assertEquals(CrownState.EMPTY, CrownState.unpack(CrownState.EMPTY.pack(), ""));
    }

    @Test
    @DisplayName("an old save, a Ponder scene or a missing array reads as a station that has never ticked")
    void aShortArrayIsEmpty() {
        assertEquals(CrownState.EMPTY, CrownState.unpack(null, ""));
        assertEquals(CrownState.EMPTY, CrownState.unpack(new long[3], "x"));
        assertEquals(CrownState.EMPTY, CrownState.unpack(new long[27], "x"), "one short of the 28 is an old layout");
        assertEquals(CrownHold.Phase.DARK, CrownState.unpack(new long[28], null).phase());
        assertEquals(28, CrownState.EMPTY.pack().length);
    }

    @Test
    @DisplayName("the stud bars split the intake A first, then B, each against its own wire's rating")
    void theStudBars() {
        final CrownState two = withIntake(100_707, 3, 65_536, 65_536);
        assertEquals(1f, two.studLoad(0), 1e-6);
        assertEquals(35_171f / 65_536f, two.studLoad(1), 1e-6);
        final CrownState one = withIntake(58_650, 1, 65_536, 0);
        assertEquals(58_650f / 65_536f, one.studLoad(0), 1e-6, "concept B's 89% at 20 chunks");
        assertEquals(0f, one.studLoad(1));
        assertTrue(two.studAWired() && two.studBWired());
        assertTrue(one.studAWired());
        assertFalse(one.studBWired());
    }

    @Test
    @DisplayName("the ticking mask and the ride-through read what was sent")
    void derivedReadouts() {
        final CrownState s = new CrownState(3, 3, 3, 0b101, CrownHold.Phase.RIDING, 0.5f, 2_048_000L, 4_096_000L,
                20_480, 20_480, 0, 0, 3, 25, 50, 0, 0, 0, 0, 2, CrownState.Reason.NONE, 0L, false, 0, 0, 0, "", 0, 0);
        assertTrue(s.ticking(0));
        assertFalse(s.ticking(1));
        assertTrue(s.ticking(2));
        assertFalse(s.ticking(40));
        assertEquals(5f, s.rideThroughSeconds(), 1e-6, "2,048,000 FE at 20,480 FE/t is 100 ticks");
        assertEquals(0f, CrownState.EMPTY.rideThroughSeconds());
        assertTrue(CrownState.Reason.NONE.mayHold());
        assertTrue(CrownState.Reason.NO_POWER.mayHold());
        assertFalse(CrownState.Reason.REGION_TAKEN.mayHold());
        assertFalse(CrownState.Reason.NO_OWNER.mayHold());
    }

    private static CrownState withIntake(int intake, int studs, int ratingA, int ratingB) {
        return new CrownState(0, 0, 0, 0, CrownHold.Phase.FED, 1f, 0L, 0L, 0, 0, intake, 0, 0, 25, 50, studs, ratingA,
                ratingB, 0, 0, CrownState.Reason.NONE, 0L, false, 0, 0, 0, "", 0, 0);
    }
}
