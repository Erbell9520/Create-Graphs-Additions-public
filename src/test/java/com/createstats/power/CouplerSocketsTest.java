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
        assertTrue(Math.abs(across - 0.5) < 1.0e-6,
                facing + "/" + end + ": socket is off the bar's centre line at " + across);
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
    @DisplayName("a socket is on the face its own box pokes out of")
    void endOnAgreesWithTheGeometry() {
        for (Direction facing : FACINGS) {
            for (CouplerSockets.End end : CouplerSockets.End.values()) {
                final Vec3 anchor = CouplerSockets.anchor(facing, end);

                Direction pokes = null;
                for (Direction face : Direction.values()) {
                    final double coord = anchor.get(face.getAxis());
                    if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? coord > 1 : coord < 0) {
                        pokes = face;
                    }
                }
                assertTrue(pokes != null, facing + "/" + end + ": anchor is inside the block");
                assertTrue(CouplerSockets.endOn(facing, pokes) == end,
                        facing + "/" + end + ": the anchor pokes out of " + pokes
                                + ", which endOn calls " + CouplerSockets.endOn(facing, pokes));
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
}
