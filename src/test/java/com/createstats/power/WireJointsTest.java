package com.createstats.power;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WireJointsTest {
    private static final class Faces {
        private final Set<Direction> offering = EnumSet.noneOf(Direction.class);

        boolean offers(Direction dir) {
            return offering.contains(dir);
        }

        Faces set(Direction dir, boolean offers) {
            if (offers) {
                offering.add(dir);
            } else {
                offering.remove(dir);
            }
            return this;
        }
    }

    @Test
    @DisplayName("a standing state fires once and then never again, however odd it is")
    void aStandingStateCannotStorm() {
        final Faces world = new Faces();
        final WireJoints joints = new WireJoints();

        assertTrue(joints.note(Direction.NORTH, world::offers),
                "the first call has no baseline, so it rediscovers once and takes one");
        for (int update = 0; update < 100; update++) {
            assertFalse(joints.note(Direction.NORTH, world::offers),
                    "nothing about this joint has moved, so nothing is owed");
        }
    }

    @Test
    @DisplayName("an open switch, which offers a handle and is deliberately on no grid, also settles")
    void anOpenSwitchSettles() {
        final Faces world = new Faces().set(Direction.EAST, true);
        final WireJoints joints = new WireJoints();

        joints.note(Direction.EAST, world::offers);
        for (int update = 0; update < 100; update++) {
            assertFalse(joints.note(Direction.EAST, world::offers));
        }
    }

    @Test
    @DisplayName("a machine appearing or being mined is news exactly once")
    void aRealChangeFiresOnce() {
        final Faces world = new Faces();
        final WireJoints joints = new WireJoints();
        joints.note(Direction.UP, world::offers);

        world.set(Direction.UP, true);
        assertTrue(joints.note(Direction.UP, world::offers), "a machine was placed against the run");
        assertFalse(joints.note(Direction.UP, world::offers), "and is still there");

        world.set(Direction.UP, false);
        assertTrue(joints.note(Direction.UP, world::offers), "and now it has been mined");
        assertFalse(joints.note(Direction.UP, world::offers));
    }

    @Test
    @DisplayName("the first call samples every face, so a change elsewhere is not reported twice")
    void thefirstCallSamplesAllSixFaces() {
        final Faces world = new Faces().set(Direction.WEST, true).set(Direction.DOWN, true);
        final WireJoints joints = new WireJoints();

        joints.note(Direction.NORTH, world::offers);
        assertFalse(joints.note(Direction.WEST, world::offers),
                "WEST was already answering when the baseline was taken");
        assertFalse(joints.note(Direction.DOWN, world::offers));
    }

    @Test
    @DisplayName("each face is remembered on its own")
    void facesAreIndependent() {
        final Faces world = new Faces();
        final WireJoints joints = new WireJoints();
        joints.note(Direction.NORTH, world::offers);

        world.set(Direction.SOUTH, true);
        assertTrue(joints.note(Direction.SOUTH, world::offers));
        assertFalse(joints.note(Direction.NORTH, world::offers), "the other five are untouched");
    }
}
