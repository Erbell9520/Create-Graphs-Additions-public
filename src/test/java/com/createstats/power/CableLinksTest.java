package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CableLinksTest {
    private static final class World implements CableLinks.View, CableRoute.SurfaceView {
        private final Set<BlockPos> solid = new HashSet<>();
        private final Set<Long> faces = new HashSet<>();
        private final Set<BlockPos> devices = new HashSet<>();
        private final java.util.Map<BlockPos, Integer> colours = new java.util.HashMap<>();

        World colour(BlockPos pos, int colour) {
            colours.put(pos, colour);
            return this;
        }

        World solid(int x, int y, int z) {
            solid.add(new BlockPos(x, y, z));
            return this;
        }

        World slab(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        solid(x, y, z);
                    }
                }
            }
            return this;
        }

        World cable(CableRoute.Step step) {
            faces.add(step.pos().asLong() * 6L + step.face().ordinal());
            return this;
        }

        World device(int x, int y, int z) {
            devices.add(new BlockPos(x, y, z));
            return this;
        }

        @Override
        public boolean hasCableFace(BlockPos pos, Direction face) {
            return faces.contains(pos.asLong() * 6L + face.ordinal());
        }

        @Override
        public boolean isCable(BlockPos pos) {
            for (Direction face : Direction.values()) {
                if (hasCableFace(pos, face)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isDevice(BlockPos pos, Direction fromSide) {
            return devices.contains(pos);
        }

        @Override
        public int colourAt(BlockPos pos) {
            if (!isCable(pos)) {
                return CableLinks.NO_COLOUR;
            }
            return colours.getOrDefault(pos, 0);
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

    private static CableLinks.Link link(World world, CableRoute.Step from, Direction towards) {
        return CableLinks.of(world, from.pos(), from.face(), towards);
    }

    private static Direction moveDirection(CableRoute.Step from, CableRoute.Step to) {
        for (Direction d : Direction.values()) {
            if (d.getAxis() == from.face().getAxis()) {
                continue;
            }

            if (to.equals(new CableRoute.Step(from.pos().relative(d), from.face()))
                    || to.equals(new CableRoute.Step(from.pos(), d))
                    || to.equals(new CableRoute.Step(from.pos().relative(d).relative(from.face()),
                            d.getOpposite()))) {
                return d;
            }
        }
        throw new AssertionError("no direction produced " + to + " from " + from);
    }

    @Test
    void runsCarryOnFlatAlongTheSameSurface() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .cable(step(1, 1, 0, Direction.DOWN));
        assertEquals(CableLinks.Link.FLAT, link(world, step(0, 1, 0, Direction.DOWN), Direction.EAST));
        assertEquals(CableLinks.Link.FLAT, link(world, step(1, 1, 0, Direction.DOWN), Direction.WEST));
    }

    @Test
    void runsTurnConcaveOntoAnotherFaceOfTheSameBlock() {
        final World world = new World().slab(0, 0, 0, 0, 0, 2).solid(0, 1, 0)
                .cable(step(0, 1, 1, Direction.DOWN))
                .cable(step(0, 1, 1, Direction.NORTH));
        assertEquals(CableLinks.Link.CONCAVE,
                link(world, step(0, 1, 1, Direction.DOWN), Direction.NORTH));
        assertEquals(CableLinks.Link.CONCAVE,
                link(world, step(0, 1, 1, Direction.NORTH), Direction.DOWN));
    }

    @Test
    void runsWrapConvexAroundTheEdgeOfTheirSupport() {
        final World world = new World().solid(0, 0, 0)
                .cable(step(0, 1, 0, Direction.DOWN))
                .cable(step(0, 0, -1, Direction.SOUTH));
        assertEquals(CableLinks.Link.CONVEX,
                link(world, step(0, 1, 0, Direction.DOWN), Direction.NORTH));
        assertEquals(CableLinks.Link.CONVEX,
                link(world, step(0, 0, -1, Direction.SOUTH), Direction.UP));
    }

    @Test
    void runsEnterAnythingThatTakesPower() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .device(1, 1, 0);
        assertEquals(CableLinks.Link.DEVICE, link(world, step(0, 1, 0, Direction.DOWN), Direction.EAST));
    }

    @Test
    void runsFoldOverAnEdgeIntoAMachineOnTheFarSide() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .device(0, 0, -1);
        assertEquals(CableLinks.Link.DEVICE_WRAP,
                link(world, step(0, 1, 0, Direction.DOWN), Direction.NORTH));
    }

    @Test
    void aMachineInLineBeatsOneAroundTheEdge() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .device(0, 1, -1)
                .device(0, 0, -1);
        assertEquals(CableLinks.Link.DEVICE,
                link(world, step(0, 1, 0, Direction.DOWN), Direction.NORTH));
    }

    @Test
    void cableAroundTheEdgeStillBeatsAMachineThere() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .cable(step(0, 0, -1, Direction.SOUTH))
                .device(0, 0, -1);
        assertEquals(CableLinks.Link.CONVEX,
                link(world, step(0, 1, 0, Direction.DOWN), Direction.NORTH));
    }

    @Test
    void aFilledCornerBlocksTheFold() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .solid(0, 1, -1)
                .device(0, 0, -1);
        assertEquals(CableLinks.Link.NONE,
                link(world, step(0, 1, 0, Direction.DOWN), Direction.NORTH));
    }

    @Test
    void parallelRunsDoNotSproutRungs() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4)
                .cable(step(0, 1, 0, Direction.DOWN))
                .cable(step(0, 1, 1, Direction.UP));
        assertEquals(CableLinks.Link.NONE, link(world, step(0, 1, 0, Direction.DOWN), Direction.SOUTH));
    }

    @Test
    void cableNeverLeavesAlongItsOwnMountingNormal() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4).cable(step(0, 1, 0, Direction.DOWN));
        assertEquals(CableLinks.Link.NONE, link(world, step(0, 1, 0, Direction.DOWN), Direction.DOWN));
        assertEquals(CableLinks.Link.NONE, link(world, step(0, 1, 0, Direction.DOWN), Direction.UP));
    }

    @Test
    void aLoneStubJoinsNothing() {
        final World world = new World().slab(0, 0, 0, 4, 0, 4).cable(step(2, 1, 2, Direction.DOWN));
        for (Direction d : Direction.values()) {
            assertEquals(CableLinks.Link.NONE, link(world, step(2, 1, 2, Direction.DOWN), d),
                    "stub should not reach " + d);
        }
    }

    @Test
    void everyRouterMoveJoinsBothWays() {
        final World world = new World().slab(0, 0, 0, 3, 0, 3).slab(0, 1, 0, 3, 2, 0);

        int checked = 0;
        for (int x = 0; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                final CableRoute.Step here = step(x, 1, z, Direction.DOWN);
                for (CableRoute.Step there : CableRoute.neighbours(world, here)) {
                    final World w = new World().slab(0, 0, 0, 3, 0, 3).slab(0, 1, 0, 3, 2, 0)
                            .cable(here).cable(there);
                    final Direction out = moveDirection(here, there);
                    final Direction back = moveDirection(there, here);
                    assertNotEquals(CableLinks.Link.NONE, link(w, here, out),
                            here + " should reach " + there);
                    assertNotEquals(CableLinks.Link.NONE, link(w, there, back),
                            there + " should reach back to " + here);
                    assertEquals(link(w, here, out), link(w, there, back),
                            "both ends of " + here + " -> " + there + " must agree on the kind");
                    checked++;
                }
            }
        }
        assertTrue(checked > 30, "expected a decent spread of moves, got " + checked);
    }

    @Test
    void theGridFloodReachesEveryBlockTheRouterLays() {
        final World world = new World().slab(0, 0, 0, 2, 0, 2);
        final CableRoute.Step from = step(0, 1, 0, Direction.DOWN);
        final CableRoute.Step to = step(3, 0, 0, Direction.WEST);
        final List<CableRoute.Step> route = CableRoute.find(world, from, to, 32);
        assertTrue(route.size() >= 4, "expected a real route, got " + route);

        boolean sawDiagonalStep = false;
        for (int i = 1; i < route.size(); i++) {
            final BlockPos a = route.get(i - 1).pos();
            final BlockPos b = route.get(i).pos();
            if (a.distManhattan(b) > 1) {
                sawDiagonalStep = true;
            }
        }
        assertTrue(sawDiagonalStep,
                "route never leaves orthogonal neighbours, so it cannot prove anything: " + route);

        for (CableRoute.Step laid : route) {
            world.cable(laid);
        }

        final Set<BlockPos> reached = new HashSet<>();
        final java.util.Deque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(from.pos());
        reached.add(from.pos());
        while (!queue.isEmpty()) {
            for (BlockPos next : CableLinks.continuations(world, queue.poll())) {
                if (world.isCable(next) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (CableRoute.Step laid : route) {
            assertTrue(reached.contains(laid.pos()),
                    "grid never reached " + laid + "; run laid was " + route);
        }
    }

    @Test
    void exactlyOneSideOfACornerOwnsIt() {
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a.getAxis() == b.getAxis()) {
                    continue;
                }
                assertNotEquals(CableLinks.ownsCorner(a, b), CableLinks.ownsCorner(b, a),
                        a + " and " + b + " must not both own, or both disown, their corner");
            }
        }
    }

    @Test
    void bothHalvesOfAConvexWrapAgreeOnTheOwner() {
        final List<Direction> mine = List.of(Direction.DOWN, Direction.SOUTH);
        final List<Direction> towards = List.of(Direction.NORTH, Direction.UP);
        final boolean firstOwns = CableLinks.ownsCorner(mine.get(0), towards.get(0).getOpposite());
        final boolean secondOwns = CableLinks.ownsCorner(mine.get(1), towards.get(1).getOpposite());
        assertNotEquals(firstOwns, secondOwns);
    }

    @Test
    @DisplayName("two colours sharing a wall are two circuits, from both ends")
    void differentColoursDoNotJoin() {
        final World w = new World().slab(0, 0, 0, 4, 0, 4);
        final CableRoute.Step a = new CableRoute.Step(new BlockPos(1, 1, 1), Direction.DOWN);
        final CableRoute.Step b = new CableRoute.Step(new BlockPos(2, 1, 1), Direction.DOWN);
        w.cable(a).cable(b);

        assertEquals(CableLinks.Link.FLAT, CableLinks.of(w, a.pos(), Direction.DOWN, Direction.EAST));
        assertEquals(CableLinks.Link.FLAT, CableLinks.of(w, b.pos(), Direction.DOWN, Direction.WEST));

        w.colour(b.pos(), 5);
        assertEquals(CableLinks.Link.NONE, CableLinks.of(w, a.pos(), Direction.DOWN, Direction.EAST));
        assertEquals(CableLinks.Link.NONE, CableLinks.of(w, b.pos(), Direction.DOWN, Direction.WEST));
        assertTrue(CableLinks.continuations(w, a.pos()).isEmpty());
        assertTrue(CableLinks.continuations(w, b.pos()).isEmpty());
    }

    @Test
    @DisplayName("colour splits a convex wrap too, not just the flat case")
    void colourAppliesToWraps() {
        final World w = new World().slab(0, 0, 0, 2, 2, 0);
        final CableRoute.Step onFace = new CableRoute.Step(new BlockPos(1, 2, 1), Direction.NORTH);
        final CableRoute.Step overTop = new CableRoute.Step(new BlockPos(1, 3, 0), Direction.DOWN);
        w.cable(onFace).cable(overTop);
        assertEquals(CableLinks.Link.CONVEX,
                CableLinks.of(w, onFace.pos(), Direction.NORTH, Direction.UP));

        w.colour(overTop.pos(), 9);
        assertEquals(CableLinks.Link.NONE,
                CableLinks.of(w, onFace.pos(), Direction.NORTH, Direction.UP));
    }

    @Test
    @DisplayName("a run collects every block on its own circuit and none of the neighbour's")
    void runCollectionFollowsColour() {
        final World w = new World().slab(0, 0, 0, 6, 0, 6);
        for (int x = 1; x <= 4; x++) {
            w.cable(new CableRoute.Step(new BlockPos(x, 1, 1), Direction.DOWN));
        }

        for (int x = 1; x <= 4; x++) {
            final BlockPos at = new BlockPos(x, 1, 2);
            w.cable(new CableRoute.Step(at, Direction.DOWN));
            w.colour(at, 11);
        }
        assertEquals(4, CableRun.collect(w, new BlockPos(1, 1, 1), 64).size());
        assertEquals(4, CableRun.collect(w, new BlockPos(1, 1, 2), 64).size());
    }
}
