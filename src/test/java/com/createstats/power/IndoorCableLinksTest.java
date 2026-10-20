package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndoorCableLinksTest {
    private static final class World implements IndoorCableLinks.View {
        private final Set<BlockPos> cable = new HashSet<>();
        private final Set<BlockPos> devices = new HashSet<>();
        private final Map<BlockPos, Integer> colours = new HashMap<>();
        private final Map<BlockPos, Set<BlockPos>> bores = new HashMap<>();

        World cable(int x, int y, int z) {
            cable.add(new BlockPos(x, y, z));
            return this;
        }

        World colour(BlockPos pos, int colour) {
            colours.put(pos, colour);
            return this;
        }

        World device(int x, int y, int z) {
            devices.add(new BlockPos(x, y, z));
            return this;
        }

        World bore(BlockPos a, BlockPos b) {
            bores.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            bores.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            return this;
        }

        @Override
        public boolean isIndoorCable(BlockPos pos) {
            return cable.contains(pos);
        }

        @Override
        public int colourAt(BlockPos pos) {
            return cable.contains(pos) ? colours.getOrDefault(pos, 0) : IndoorCableLinks.NO_COLOUR;
        }

        @Override
        public boolean isDevice(BlockPos pos, Direction fromSide) {
            return devices.contains(pos);
        }

        @Override
        @Nullable
        public BlockPos boreTowards(BlockPos pos, Direction towards) {
            for (BlockPos far : bores.getOrDefault(pos, Set.of())) {
                if (BoreLinks.axisBetween(pos, far) == towards) {
                    return far;
                }
            }
            return null;
        }
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Test
    @DisplayName("cable next door on the same circuit is a plain join")
    void cableJoinsCable() {
        final World world = new World().cable(0, 0, 0).cable(1, 0, 0);
        assertEquals(IndoorCableLinks.Link.CABLE,
                IndoorCableLinks.of(world, at(0, 0, 0), Direction.EAST));
    }

    @Test
    @DisplayName("two colours that touch are two circuits, not one")
    void colourSplitsCircuits() {
        final World world = new World().cable(0, 0, 0).cable(1, 0, 0).colour(at(1, 0, 0), 4);
        assertEquals(IndoorCableLinks.Link.NONE,
                IndoorCableLinks.of(world, at(0, 0, 0), Direction.EAST));
        assertEquals(IndoorCableLinks.Link.NONE,
                IndoorCableLinks.of(world, at(1, 0, 0), Direction.WEST));
        assertTrue(IndoorCableLinks.continuations(world, at(0, 0, 0)).isEmpty(),
                "a different circuit must not be flooded into");
    }

    @Test
    @DisplayName("a machine beside the run is reached, not stopped at")
    void deviceIsReached() {
        final World world = new World().cable(0, 0, 0).device(0, -1, 0);
        assertEquals(IndoorCableLinks.Link.DEVICE,
                IndoorCableLinks.of(world, at(0, 0, 0), Direction.DOWN));

        assertTrue(IndoorCableLinks.continuations(world, at(0, 0, 0)).isEmpty());
    }

    @Test
    @DisplayName("a bore beats the machine it goes through, so the run does not stop inside it")
    void boreOutranksTheDeviceItPassesThrough() {
        final World world = new World().cable(0, 0, 0).cable(2, 0, 0).device(1, 0, 0)
                .bore(at(0, 0, 0), at(2, 0, 0));
        assertEquals(IndoorCableLinks.Link.BORE,
                IndoorCableLinks.of(world, at(0, 0, 0), Direction.EAST));
        assertEquals(List.of(at(2, 0, 0)), IndoorCableLinks.continuations(world, at(0, 0, 0)));
    }

    @Test
    @DisplayName("every kind of join is seen from both ends")
    void everyJoinIsSeenFromBothEnds() {
        final World world = new World()
                .cable(0, 0, 0).cable(1, 0, 0)
                .cable(4, 0, 0).bore(at(1, 0, 0), at(4, 0, 0))
                .cable(0, 3, 0).bore(at(0, 0, 0), at(0, 3, 0));
        for (BlockPos from : List.of(at(0, 0, 0), at(1, 0, 0), at(4, 0, 0), at(0, 3, 0))) {
            for (BlockPos to : IndoorCableLinks.continuations(world, from)) {
                assertTrue(IndoorCableLinks.continuations(world, to).contains(from),
                        from + " reaches " + to + " but " + to + " does not reach back");
            }
        }

        assertEquals(2, IndoorCableLinks.continuations(world, at(0, 0, 0)).size());
    }

    @Test
    @DisplayName("a bore is straight, long enough to be a wall, and short enough not to be a shortcut")
    void boreAxisRules() {
        assertEquals(Direction.EAST, BoreLinks.axisBetween(at(0, 0, 0), at(3, 0, 0)));
        assertEquals(Direction.DOWN, BoreLinks.axisBetween(at(0, 0, 0), at(0, -2, 0)));
        assertNull(BoreLinks.axisBetween(at(0, 0, 0), at(2, 1, 0)), "diagonals are not sleeves");
        assertNull(BoreLinks.axisBetween(at(0, 0, 0), at(1, 0, 0)),
                "an adjacent pair is an ordinary join, not a bore");
        assertNull(BoreLinks.axisBetween(at(0, 0, 0), at(0, 0, 0)), "a position is not its own partner");
        assertNull(BoreLinks.axisBetween(at(0, 0, 0), at(IndoorCable.MAX_BORE + 2, 0, 0)),
                "past the cap is not a wall any more");
        assertNotEquals(null, BoreLinks.axisBetween(at(0, 0, 0), at(IndoorCable.MAX_BORE + 1, 0, 0)),
                "exactly the cap must still be allowed, or the constant is off by one");
    }

    @Test
    @DisplayName("a bore's crossings are the positions between its ends, in order, excluding both")
    void boreCrossings() {
        assertEquals(List.of(at(1, 0, 0), at(2, 0, 0)),
                BoreLinks.crossings(at(0, 0, 0), at(3, 0, 0)));

        assertEquals(List.of(at(2, 0, 0), at(1, 0, 0)),
                BoreLinks.crossings(at(3, 0, 0), at(0, 0, 0)));
        assertEquals(IndoorCable.MAX_BORE,
                BoreLinks.crossings(at(0, 0, 0), at(IndoorCable.MAX_BORE + 1, 0, 0)).size());
        assertTrue(BoreLinks.crossings(at(0, 0, 0), at(1, 1, 1)).isEmpty());
    }

    @Test
    @DisplayName("a two-block host anchors a bore at the nearer of its blocks that is in line")
    void boreAnchorPicksTheNearerHalf() {
        final BlockPos leaf = at(0, 0, 0);
        final BlockPos head = at(0, 1, 0);

        assertEquals(head, BoreLinks.anchorFor(at(0, 3, 0), leaf, head));

        assertEquals(leaf, BoreLinks.anchorFor(at(0, -2, 0), leaf, head));

        assertEquals(head, BoreLinks.anchorFor(at(-2, 1, 0), leaf, head));
        assertEquals(leaf, BoreLinks.anchorFor(at(-2, 0, 0), leaf, head));
        assertEquals(head, BoreLinks.anchorFor(at(0, 1, 3), leaf, head));

        assertEquals(leaf, BoreLinks.anchorFor(at(2, 2, 0), leaf, head));
    }

    @Test
    @DisplayName("a bore never has to thread its own host to reach its far end")
    void boreAnchorNeverCrossesItsOwnHost() {
        final BlockPos leaf = at(0, 0, 0);
        final BlockPos head = at(0, 1, 0);

        final BlockPos under = at(0, -2, 0);
        final BlockPos anchor = BoreLinks.anchorFor(under, leaf, head);
        assertEquals(leaf, anchor);
        assertFalse(BoreLinks.crossings(anchor, under).contains(leaf), "a sleeve does not cross its own end");
        assertFalse(BoreLinks.crossings(anchor, under).contains(head), "nor the host's other block");
    }

    @Test
    @DisplayName("Indoor Cable carries a quarter of Surface Cable, whatever the base is retuned to")
    void rateIsAQuarterOfSurfaceCable() {
        for (int base : new int[] {8192, 4096, 20000, 64, 1024}) {
            assertEquals(CableTier.SURFACE.rate(base), IndoorCable.rate(base) * 4,
                    "base " + base + " must keep the quarter relationship");
        }
        assertEquals(1024, IndoorCable.rate(8192), "the default base must give the advertised figure");
    }

    @Test
    @DisplayName("an anchor puts the cord off its own face and leaves the other two axes centred")
    void anchorOffsetsOnlyItsOwnAxis() {
        for (Direction face : Direction.values()) {
            final IndoorCableAnchor anchor = IndoorCableAnchor.of(face);
            assertEquals(face, anchor.face());
            for (Direction.Axis axis : Direction.Axis.values()) {
                final float centre = anchor.centreOn(axis);
                if (axis == face.getAxis()) {
                    final float expected = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? 1.0f - IndoorCable.STANDOFF : IndoorCable.STANDOFF;
                    assertEquals(expected, centre, 1.0e-6f, face + " on its own axis");
                } else {
                    assertEquals(0.5f, centre, 1.0e-6f, face + " must not move " + axis);
                }
            }
        }
    }

    @Test
    @DisplayName("a free cord is centred on every axis, which is what a bore needs")
    void freeAnchorIsCentred() {
        assertNull(IndoorCableAnchor.FREE.face());
        for (Direction.Axis axis : Direction.Axis.values()) {
            assertEquals(0.5f, IndoorCableAnchor.FREE.centreOn(axis), 1.0e-6f);
        }
        assertEquals(IndoorCableAnchor.FREE, IndoorCableAnchor.of(null));
    }

    @Test
    @DisplayName("the anchor survives a round trip through its serialized name")
    void anchorNamesAreStableAndDistinct() {
        final Set<String> names = new HashSet<>();
        for (IndoorCableAnchor anchor : IndoorCableAnchor.values()) {
            assertTrue(names.add(anchor.getSerializedName()),
                    "duplicate serialized name: " + anchor.getSerializedName());
        }
        assertEquals(IndoorCableAnchor.values().length, names.size());
        assertEquals("free", IndoorCableAnchor.FREE.getSerializedName());

        assertEquals("down", IndoorCableAnchor.DOWN.getSerializedName());
        assertEquals("up", IndoorCableAnchor.UP.getSerializedName());
        assertEquals("north", IndoorCableAnchor.NORTH.getSerializedName());
        assertEquals("south", IndoorCableAnchor.SOUTH.getSerializedName());
        assertEquals("west", IndoorCableAnchor.WEST.getSerializedName());
        assertEquals("east", IndoorCableAnchor.EAST.getSerializedName());
    }

    @Test
    @DisplayName("every pair of faces on different axes is a corner, and it is the same corner either way round")
    void everyEdgeExistsAndIsSymmetric() {
        int edges = 0;
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                final IndoorCableAnchor edge = IndoorCableAnchor.of(a, b);
                if (a.getAxis() == b.getAxis()) {
                    assertEquals(IndoorCableAnchor.of(a), edge, a + " with " + b);
                    continue;
                }
                edges++;
                assertEquals(2, edge.faces().size(), a + " with " + b);
                assertTrue(edge.clipsTo(a) && edge.clipsTo(b), a + " with " + b);
                assertEquals(edge, IndoorCableAnchor.of(b, a), "order must not matter");
            }
        }
        assertEquals(24, edges, "six faces pair with the four off their own axis, both ways round");
    }

    @Test
    @DisplayName("a corner puts the cord off both its faces and leaves the run's own axis centred")
    void cornerOffsetsBothItsAxes() {
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a.getAxis() == b.getAxis()) {
                    continue;
                }
                final IndoorCableAnchor edge = IndoorCableAnchor.of(a, b);
                for (Direction.Axis axis : Direction.Axis.values()) {
                    final float centre = edge.centreOn(axis);
                    final Direction on = axis == a.getAxis() ? a : axis == b.getAxis() ? b : null;
                    if (on == null) {
                        assertEquals(0.5f, centre, 1.0e-6f, edge + " must not move " + axis);
                        assertEquals(axis, edge.freeAxis(), edge + " has one axis left");
                    } else {
                        final float expected = on.getAxisDirection() == Direction.AxisDirection.POSITIVE
                                ? 1.0f - IndoorCable.STANDOFF : IndoorCable.STANDOFF;
                        assertEquals(expected, centre, 1.0e-6f, edge + " on " + axis);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a stub always has an axis to run along, and it prefers the vertical")
    void freeAxisIsAlwaysOpen() {
        assertEquals(Direction.Axis.Y, IndoorCableAnchor.FREE.freeAxis());
        assertEquals(Direction.Axis.Y, IndoorCableAnchor.NORTH.freeAxis(), "a wall stub hangs down");
        assertEquals(Direction.Axis.X, IndoorCableAnchor.DOWN.freeAxis(), "a floor stub lies flat");
        for (IndoorCableAnchor anchor : IndoorCableAnchor.values()) {
            assertFalse(anchor.usesAxis(anchor.freeAxis()), anchor + " must leave its free axis alone");
            assertEquals(0.5f, anchor.centreOn(anchor.freeAxis()), 1.0e-6f);
        }
    }

    @Test
    @DisplayName("an anchor takes a second face and refuses a third")
    void withAddsOneFaceAndStops() {
        assertEquals(IndoorCableAnchor.DOWN, IndoorCableAnchor.FREE.with(Direction.DOWN));
        assertEquals(IndoorCableAnchor.of(Direction.DOWN, Direction.NORTH),
                IndoorCableAnchor.DOWN.with(Direction.NORTH));
        assertEquals(IndoorCableAnchor.DOWN, IndoorCableAnchor.DOWN.with(Direction.DOWN));
        assertEquals(IndoorCableAnchor.DOWN, IndoorCableAnchor.DOWN.with(Direction.UP),
                "a wall either side is a gap, not a corner");
        assertEquals(IndoorCableAnchor.DOWN, IndoorCableAnchor.DOWN.with(null));
        final IndoorCableAnchor corner = IndoorCableAnchor.of(Direction.DOWN, Direction.NORTH);
        assertEquals(corner, corner.with(Direction.EAST), "no third face");
    }

    @Test
    @DisplayName("a lone block reports nothing in every direction")
    void loneBlockHasNoJoins() {
        final World world = new World().cable(0, 0, 0);
        for (Direction dir : Direction.values()) {
            assertEquals(IndoorCableLinks.Link.NONE, IndoorCableLinks.of(world, at(0, 0, 0), dir));
        }
        assertFalse(IndoorCableLinks.continuations(world, at(0, 0, 0)).iterator().hasNext());
    }
}
