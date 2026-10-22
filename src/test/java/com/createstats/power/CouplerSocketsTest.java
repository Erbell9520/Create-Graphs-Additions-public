package com.createstats.power;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerSocketsTest {
    private static final Set<Direction> FACINGS =
            EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    private static final double PROUD = 0.1;

    @Test
    @DisplayName("each socket sits on its own end of the block, for every facing")
    void socketsSitOnTheirOwnEnd() {
        for (Direction facing : FACINGS) {
            assertOnEnd(facing, CouplerSockets.End.FOREIGN, facing);
            assertOnEnd(facing, CouplerSockets.End.GRID, facing.getOpposite());
        }
    }

    private static void assertOnEnd(Direction facing, CouplerSockets.End end, Direction out) {
        final AABB box = CouplerSockets.local(facing, end);
        final Vec3 centre = box.getCenter();
        final double along = out.getAxis() == Direction.Axis.X ? centre.x : centre.z;
        final double across = out.getAxis() == Direction.Axis.X ? centre.z : centre.x;
        final double expected = out.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        assertTrue(Math.abs(along - expected) < 0.25,
                facing + "/" + end + ": socket centre is " + along + " along " + out.getAxis()
                        + ", which is not the " + out + " end");
        assertTrue(across > 0 && across < 1,
                facing + "/" + end + ": socket is outside the block across the axis at " + across);
    }

    @Test
    @DisplayName("a wire's anchor is inside its own socket")
    void anchorsSitInsideTheirSockets() {
        for (Direction facing : FACINGS) {
            for (CouplerSockets.End end : CouplerSockets.End.values()) {
                final AABB box = CouplerSockets.local(facing, end);
                final Vec3 anchor = CouplerSockets.anchor(facing, end);
                assertTrue(box.inflate(1.0e-6).contains(anchor),
                        facing + "/" + end + ": the wire anchor " + anchor
                                + " is outside the socket the outline draws, " + box);
            }
        }
    }

    @Test
    @DisplayName("the two sockets on one Coupler never overlap")
    void theTwoEndsAreDistinct() {
        for (Direction facing : FACINGS) {
            final AABB foreign = CouplerSockets.local(facing, CouplerSockets.End.FOREIGN);
            final AABB grid = CouplerSockets.local(facing, CouplerSockets.End.GRID);
            assertFalse(foreign.intersects(grid),
                    facing + ": the foreign and grid sockets overlap, so the guide would draw both "
                            + "outlines in the same place");

            assertFalse(foreign.inflate(1.0 / 16).intersects(grid.inflate(1.0 / 16)),
                    facing + ": the two posts' click boxes overlap");
        }
    }

    @Test
    @DisplayName("a socket stays within its own block bar the stud standing proud")
    void socketsStayInsideTheirBlock() {
        for (Direction facing : FACINGS) {
            for (CouplerSockets.End end : CouplerSockets.End.values()) {
                final AABB box = CouplerSockets.local(facing, end);
                assertTrue(box.minX >= -PROUD && box.minY >= -PROUD && box.minZ >= -PROUD
                                && box.maxX <= 1.0 + PROUD && box.maxY <= 1.0 + PROUD
                                && box.maxZ <= 1.0 + PROUD,
                        facing + "/" + end + ": socket " + box + " leaves its own block by more than "
                                + PROUD + ", so it would be drawn over a neighbour");
            }
        }
    }

    @Test
    @DisplayName("the four facings give four different sockets")
    void everyFacingIsRotatedForReal() {
        for (CouplerSockets.End end : CouplerSockets.End.values()) {
            final Set<String> seen = new java.util.HashSet<>();
            for (Direction facing : FACINGS) {
                assertTrue(seen.add(CouplerSockets.local(facing, end).toString()),
                        end + ": two facings share a socket box, so one of them was not rotated");
            }
        }
    }

    @Test
    @DisplayName("exactly two faces take a wire, and they are the two the sockets are on")
    void onlyTheTwoEndsTakeAWire() {
        for (Direction facing : FACINGS) {
            for (Direction face : Direction.values()) {
                final CouplerSockets.End end = CouplerSockets.endOn(facing, face);
                if (face == facing) {
                    assertTrue(end == CouplerSockets.End.FOREIGN,
                            facing + ": the marked face must be the seam socket, was " + end);
                } else if (face == facing.getOpposite()) {
                    assertTrue(end == CouplerSockets.End.GRID,
                            facing + ": the opposite face must be the grid socket, was " + end);
                } else {
                    assertTrue(end == null,
                            facing + "/" + face + ": a flank answered " + end);
                }
            }
        }
    }

    @Test
    @DisplayName("a click on a post means that post's socket; elsewhere the end face decides, and a flank or the top nothing")
    void aClickMeansTheSocketItLandedOn() {
        for (Direction facing : FACINGS) {
            for (CouplerSockets.End end : CouplerSockets.End.values()) {
                final AABB box = CouplerSockets.local(facing, end);
                assertTrue(CouplerSockets.endAt(facing, CouplerSockets.anchor(facing, end), null) == end,
                        facing + "/" + end + ": a click on the post's anchor did not pick its socket");

                assertTrue(CouplerSockets.endAt(facing, new Vec3(box.getCenter().x, box.maxY, box.getCenter().z),
                        Direction.UP) == end, facing + "/" + end + ": a click on the top of the post");
                assertTrue(CouplerSockets.endAt(facing, new Vec3(box.minX - 0.03, box.getCenter().y,
                        box.getCenter().z), Direction.WEST) == end, facing + "/" + end + ": a hair off the post");
            }

            assertTrue(CouplerSockets.endAt(facing, new Vec3(0.5, 0.9, 0.5), Direction.UP) == null,
                    facing + ": a click on the middle of the top picked a socket");

            final Vec3 foreignFace = new Vec3(0.5 + facing.getStepX() * 0.5, 0.2, 0.5 + facing.getStepZ() * 0.5);
            assertTrue(CouplerSockets.endAt(facing, foreignFace, facing) == CouplerSockets.End.FOREIGN,
                    facing + ": a click on the foreign end face");
            final Vec3 gridFace = new Vec3(0.5 - facing.getStepX() * 0.5, 0.2, 0.5 - facing.getStepZ() * 0.5);
            assertTrue(CouplerSockets.endAt(facing, gridFace, facing.getOpposite()) == CouplerSockets.End.GRID,
                    facing + ": a click on the grid end face");
        }
    }

    @Test
    @DisplayName("both posts and the lever are inside the block's shape, or no click could reach them")
    void everythingClickableIsInsideTheShape() {
        for (Direction facing : FACINGS) {
            for (CouplerSockets.End end : CouplerSockets.End.values()) {
                assertTrue(CouplerShape.contains(facing, CouplerSockets.local(facing, end)),
                        facing + "/" + end + ": the socket post sticks out of the shape");
            }
            assertTrue(CouplerShape.contains(facing, CouplerLever.local(facing)),
                    facing + ": the lever's swept box sticks out of the shape");
            for (AABB part : CouplerShape.boxes(facing)) {
                assertTrue(part.minX >= 0 && part.minY >= 0 && part.minZ >= 0 && part.maxX <= 1 && part.maxY <= 1
                        && part.maxZ <= 1, facing + ": a shape box leaves its block: " + part);
            }
        }
    }

    @Test
    @DisplayName("a wire is drawn from the end its far block lies toward")
    void endTowardFollowsTheFarBlock() {
        final net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(0, 64, 0);
        for (Direction facing : FACINGS) {
            assertTrue(CouplerSockets.endToward(facing, origin, origin.relative(facing, 4))
                            == CouplerSockets.End.FOREIGN,
                    facing + ": a far end past the marked face must draw from the seam socket");
            assertTrue(CouplerSockets.endToward(facing, origin,
                            origin.relative(facing.getOpposite(), 4)) == CouplerSockets.End.GRID,
                    facing + ": a far end behind the block must draw from the grid socket");

            assertTrue(CouplerSockets.endToward(facing, origin, origin.above(4))
                            == CouplerSockets.End.GRID,
                    facing + ": a far end level with the block must fall to the grid socket");
        }
    }

    @Test
    @DisplayName("what a span means is its stored flag, wherever its far end stands (KI-214)")
    void theDrawnSocketIsTheStoredSocket() {
        assertTrue(CouplerSockets.endOf(true) == CouplerSockets.End.FOREIGN);
        assertTrue(CouplerSockets.endOf(false) == CouplerSockets.End.GRID);
        final net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(0, 64, 0);
        for (Direction facing : FACINGS) {
            final net.minecraft.core.BlockPos beyond = origin.relative(facing, 3);
            assertTrue(CouplerSockets.endToward(facing, origin, beyond) != CouplerSockets.endOf(false),
                    "the case the stored flag exists for: " + facing);
        }
    }

    @Test
    @DisplayName("one socket table for all four wiring routes (KI-215)")
    void theSocketTable() {
        assertTrue(CouplerSockets.refusalKey(CouplerSockets.End.FOREIGN, true, false) == null);
        assertTrue(CouplerSockets.refusalKey(CouplerSockets.End.FOREIGN, true, true) == null);
        assertTrue(CouplerSockets.SEAM_ONLY.equals(CouplerSockets.refusalKey(CouplerSockets.End.FOREIGN, false, false)));

        assertTrue(CouplerSockets.refusalKey(CouplerSockets.End.GRID, false, false) == null);
        assertTrue(CouplerSockets.GRID_WANTS_OURS.equals(CouplerSockets.refusalKey(CouplerSockets.End.GRID, true, false)));
        assertTrue(CouplerSockets.FOREIGN_WANTS_SEAM.equals(
                CouplerSockets.refusalKey(CouplerSockets.End.GRID, true, true)),
                "our wire begun on their connector and finished on the gland used to be filed, unasked");

        assertTrue(CouplerSockets.refusalKey(null, true, true) == null);
    }
}
