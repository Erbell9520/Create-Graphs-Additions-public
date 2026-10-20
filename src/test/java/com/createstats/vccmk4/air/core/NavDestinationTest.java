package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavDestinationTest {
    private static final double[] LODESTONE = {448.5, 63.5, 540.5};
    private static final double[] SPAWN = {160.5, 69.5, -351.5};

    @Test
    void theOwnersCompassGoesToItsLodestoneNotToSpawn() {
        NavDestination d = NavDestination.choose(true, LODESTONE, false, false, SPAWN);
        assertEquals(NavDestination.Source.LODESTONE, d.source());
        assertEquals(448.5, d.x(), 1.0e-9);
        assertEquals(540.5, d.z(), 1.0e-9);
        assertTrue(d.tableElsewhere(), "the table's own pointer shows spawn, and the console says so");
        assertEquals(448.5, d.xz()[0], 1.0e-9);
        assertEquals(540.5, d.xz()[1], 1.0e-9);
    }

    @Test
    void aTableThatAgreesSaysNothing() {
        NavDestination d = NavDestination.choose(true, LODESTONE, false, false, new double[] {448.5, 63.5, 541.5});
        assertFalse(d.tableElsewhere());
    }

    @Test
    void aPlainCompassOrAMapGoesWhereTheTablePoints() {
        NavDestination d = NavDestination.choose(true, null, false, false, SPAWN);
        assertEquals(NavDestination.Source.TABLE, d.source());
        assertEquals(-351.5, d.z(), 1.0e-9);
        assertFalse(d.tableElsewhere());
    }

    @Test
    void aLostOrFarLodestoneIsNowhereAndSaysWhy() {
        NavDestination lost = NavDestination.choose(true, null, true, false, SPAWN);
        assertFalse(lost.found(), "not spawn, where the table would send it");
        assertNull(lost.xz());
        assertTrue(lost.problem().contains("lost its lodestone"));
        NavDestination far = NavDestination.choose(true, null, false, true, SPAWN);
        assertFalse(far.found());
        assertTrue(far.problem().contains("another dimension"));
    }

    @Test
    void noTableOrNothingOnItIsNowhere() {
        assertTrue(NavDestination.choose(false, LODESTONE, false, false, SPAWN).problem().contains("No Navigation Table"));
        NavDestination empty = NavDestination.choose(true, null, false, false, null);
        assertFalse(empty.found());
        assertTrue(empty.problem().contains("Nothing on the Navigation Table"));
    }
}
