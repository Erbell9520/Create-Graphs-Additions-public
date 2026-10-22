package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerLeverTest {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @Test
    @DisplayName("the box turns with the block: a quarter turn of the facing is a quarter turn of the box")
    void turnsWithTheFacing() {
        final AABB probe = new AABB(0.4, 0.0, 0.0, 0.6, 0.1, 0.1);

        assertBox(new AABB(0.9, 0.0, 0.4, 1.0, 0.1, 0.6), CouplerLever.turn(probe, Direction.EAST));
        assertBox(new AABB(0.4, 0.0, 0.9, 0.6, 0.1, 1.0), CouplerLever.turn(probe, Direction.SOUTH));
        assertBox(new AABB(0.0, 0.0, 0.4, 0.1, 0.1, 0.6), CouplerLever.turn(probe, Direction.WEST));
        for (Direction d : HORIZONTAL) {
            final AABB box = CouplerLever.local(d);
            assertTrue(box.minX >= 0 && box.maxX <= 1 && box.minY >= 0 && box.maxY <= 1 && box.minZ >= 0
                    && box.maxZ <= 1, "the lever stays in its block: " + d);
        }
    }

    @Test
    @DisplayName("a click on the lever's centre counts for every facing; a click on a socket end never does")
    void onlyTheLever() {
        for (Direction d : HORIZONTAL) {
            assertTrue(CouplerLever.hit(d, CouplerLever.local(d).getCenter()), d.toString());
            for (CouplerSockets.End end : CouplerSockets.End.values()) {
                assertFalse(CouplerLever.hit(d, CouplerSockets.anchor(d, end)), end + " socket anchor " + d);
                assertFalse(CouplerLever.hit(d, CouplerSockets.local(d, end).getCenter()), end + " socket " + d);
                assertFalse(CouplerLever.local(d).inflate(1.0 / 64).intersects(
                        CouplerSockets.local(d, end).inflate(1.0 / 16)), "the lever overlaps the " + end + " post " + d);
            }
        }
        assertFalse(CouplerLever.hit(Direction.UP, new Vec3(0.5, 0.5, 0.5)));
        assertFalse(CouplerLever.hit(null, new Vec3(0.5, 0.5, 0.5)));
        assertFalse(CouplerLever.hit(Direction.NORTH, null));
    }

    private static void assertBox(AABB want, AABB got) {
        assertEquals(want.minX, got.minX, 1e-9);
        assertEquals(want.minY, got.minY, 1e-9);
        assertEquals(want.minZ, got.minZ, 1e-9);
        assertEquals(want.maxX, got.maxX, 1e-9);
        assertEquals(want.maxY, got.maxY, 1e-9);
        assertEquals(want.maxZ, got.maxZ, 1e-9);
    }
}
