package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableRouteTest {
    private static final class Blocks implements CableRoute.SurfaceView {
        private final Set<BlockPos> solid = new HashSet<>();

        Blocks solidAt(int x, int y, int z) {
            solid.add(new BlockPos(x, y, z));
            return this;
        }

        Blocks slab(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        solidAt(x, y, z);
                    }
                }
            }
            return this;
        }

        @Override
        public boolean isFree(BlockPos pos) {
            return !solid.contains(pos);
        }

        @Override
        public boolean isSolid(BlockPos pos) {
            return solid.contains(pos);
        }
    }

    private static CableRoute.Step step(int x, int y, int z, Direction face) {
        return new CableRoute.Step(new BlockPos(x, y, z), face);
    }

    @Test
    void aFlatRunCarriesOnAlongTheSameSurface() {
        final Blocks world = new Blocks().slab(0, 0, 0, 5, 0, 0);
        final List<CableRoute.Step> route = CableRoute.find(world,
                step(0, 1, 0, Direction.DOWN), step(4, 1, 0, Direction.DOWN), 32);

        assertEquals(5, route.size(), "five blocks from x=0 to x=4 inclusive");
        for (CableRoute.Step s : route) {
            assertEquals(Direction.DOWN, s.face(), "a flat floor run never leaves the floor");
        }
    }

    @Test
    void hittingAWallTurnsOntoItWithoutChangingBlockPosition() {
        final Blocks world = new Blocks().slab(0, 0, 0, 3, 0, 0).slab(3, 1, 0, 3, 4, 0);
        final List<CableRoute.Step> neighbours = CableRoute.neighbours(world, step(2, 1, 0, Direction.DOWN));

        assertTrue(neighbours.contains(step(2, 1, 0, Direction.EAST)),
                "the run should be able to turn up the wall it just met");
        assertFalse(neighbours.contains(step(3, 1, 0, Direction.DOWN)),
                "and must not walk into the wall block itself");
    }

    @Test
    void aSurfaceRunningOutFoldsAroundTheOutsideEdge() {
        final Blocks world = new Blocks().slab(0, 0, 0, 2, 0, 0);
        final List<CableRoute.Step> neighbours = CableRoute.neighbours(world, step(2, 1, 0, Direction.DOWN));

        assertTrue(neighbours.contains(step(3, 0, 0, Direction.WEST)),
                "the run should fold around the edge onto the far side of its own support");
    }

    @Test
    void aRouteClimbsAWallAndCrossesACeiling() {
        final Blocks world = new Blocks()
                .slab(0, 0, 0, 6, 0, 0)
                .slab(6, 1, 0, 6, 5, 0)
                .slab(0, 5, 0, 6, 5, 0);

        final List<CableRoute.Step> route = CableRoute.find(world,
                step(0, 1, 0, Direction.DOWN), step(1, 4, 0, Direction.UP), 64);

        assertFalse(route.isEmpty(), "a route around the inside of a room must exist");
        assertEquals(step(0, 1, 0, Direction.DOWN), route.get(0));
        assertEquals(step(1, 4, 0, Direction.UP), route.get(route.size() - 1));

        for (CableRoute.Step s : route) {
            assertTrue(CableRoute.isPlaceable(world, s), "unplaceable step in route: " + s);
        }

        for (int i = 1; i < route.size(); i++) {
            assertTrue(CableRoute.neighbours(world, route.get(i - 1)).contains(route.get(i)),
                    "route jumps between unconnected steps at index " + i);
        }

        final Set<Direction> faces = new HashSet<>();
        route.forEach(s -> faces.add(s.face()));
        assertTrue(faces.contains(Direction.DOWN), "starts on the floor");
        assertTrue(faces.contains(Direction.EAST), "climbs the wall");
        assertTrue(faces.contains(Direction.UP), "finishes on the ceiling");
    }

    @Test
    void theShortestOfTwoRoutesIsTheOneReturned() {
        final Blocks world = new Blocks().slab(0, 0, 0, 8, 0, 8);
        final List<CableRoute.Step> route = CableRoute.find(world,
                step(0, 1, 0, Direction.DOWN), step(3, 1, 3, Direction.DOWN), 64);

        assertEquals(7, route.size(), "a flat route should be Manhattan-shortest");
    }

    @Test
    void anUnreachableTargetReturnsNothingRatherThanAPartialRun() {
        final Blocks world = new Blocks().slab(0, 0, 0, 2, 0, 0).slab(20, 0, 0, 22, 0, 0);
        final List<CableRoute.Step> route = CableRoute.find(world,
                step(0, 1, 0, Direction.DOWN), step(21, 1, 0, Direction.DOWN), 64);

        assertTrue(route.isEmpty(),
                "half a run that stops in mid air would offer something that cannot be placed");
    }

    @Test
    void theStepCapStopsAHopelessSearchRatherThanWalkingForever() {
        final Blocks world = new Blocks().slab(0, 0, 0, 200, 0, 0);
        final List<CableRoute.Step> route = CableRoute.find(world,
                step(0, 1, 0, Direction.DOWN), step(150, 1, 0, Direction.DOWN), 16);

        assertTrue(route.isEmpty(), "a target beyond the cap is simply not offered");
    }

    @Test
    void aTargetOnTheSameBlockAsTheStartIsASingleStep() {
        final Blocks world = new Blocks().slab(0, 0, 0, 2, 0, 0);
        final List<CableRoute.Step> route = CableRoute.find(world,
                step(1, 1, 0, Direction.DOWN), step(1, 1, 0, Direction.DOWN), 16);
        assertEquals(1, route.size());
    }

    @Test
    void aStepWithNothingBehindItIsNeverPlaceable() {
        final Blocks world = new Blocks().slab(0, 0, 0, 2, 0, 0);
        assertFalse(CableRoute.isPlaceable(world, step(1, 5, 0, Direction.DOWN)),
                "floating in the air with no support is not a cable position");
        assertFalse(CableRoute.isPlaceable(world, step(1, 0, 0, Direction.DOWN)),
                "and neither is inside a solid block");
    }
}
