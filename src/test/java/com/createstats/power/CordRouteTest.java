package com.createstats.power;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordRouteTest {
    private static CordRoute.Solid world(BiPredicate<Integer, Integer> solidByXY) {
        return (x, y, z) -> {
            final int bx = (int) Math.floor(x);
            final int by = (int) Math.floor(y);
            if (!solidByXY.test(bx, by)) {
                return null;
            }
            final AABB box = new AABB(bx, by, Math.floor(z), bx + 1, by + 1, Math.floor(z) + 1);
            return x > box.minX && x < box.maxX && y > box.minY && y < box.maxY
                    && z > box.minZ && z < box.maxZ ? box : null;
        };
    }

    private static final CordRoute.Solid EMPTY = (x, y, z) -> null;

    private static final CordRoute.Solid FLOOR = world((x, y) -> y < 1);

    @Test
    @DisplayName("in open air the cord hangs, and hangs below both of its ends")
    void itSags() {
        final Vec3 from = new Vec3(0.5, 3.0, 0.5);
        final Vec3 to = new Vec3(3.5, 3.0, 0.5);
        final List<Vec3> cord = CordRoute.settle(EMPTY, from, to);
        assertNotNull(cord, "a cord across open air has to be possible");
        assertTrue(lowest(cord) < 3.0 - 0.15,
                "a cord with slack in it that hangs less than a sixth of a block is a taut wire");

        assertTrue(length(cord) <= from.distanceTo(to) * 1.10,
                "the cord stretched past its own slack: " + length(cord) / from.distanceTo(to));
    }

    @Test
    @DisplayName("a cord run over a floor comes to rest on it, not through it")
    void itRestsOnTheFloor() {
        final Vec3 from = new Vec3(0.5, 1.35, 0.5);
        final Vec3 to = new Vec3(4.0, 1.35, 0.5);
        final List<Vec3> cord = CordRoute.settle(FLOOR, from, to);
        assertNotNull(cord, "there is nothing in the way but the floor");
        assertTrue(lowest(cord) >= 1.0, "the cord sank into the floor, down to " + lowest(cord));
        assertTrue(lowest(cord) < 1.35, "the cord never settled toward the floor: " + lowest(cord));
        assertFree(FLOOR, cord);
    }

    @Test
    @DisplayName("a cord draped over a counter lies on top of it")
    void itLiesOnACounter() {
        final CordRoute.Solid counter = world((x, y) -> y < 1 || (x == 2 && y == 1));
        final List<Vec3> cord = CordRoute.settle(counter, new Vec3(0.5, 2.2, 0.5), new Vec3(4.5, 2.2, 0.5));
        assertNotNull(cord, "a cord may be laid over a counter");
        assertFree(counter, cord);

        assertTrue(cord.stream().anyMatch(p -> p.x > 2.0 && p.x < 3.0 && p.y >= 2.0),
                "the cord did not stay on top of the counter it was laid over");
    }

    @Test
    @DisplayName("a cord will not pass through a wall, and says so by refusing")
    void itRefusesToTunnel() {
        final CordRoute.Solid wall = world((x, y) -> x == 2);
        assertNull(CordRoute.settle(wall, new Vec3(1.5, 2.5, 0.5), new Vec3(3.5, 2.5, 0.5)),
                "a cord was routed straight through a wall");
    }

    @Test
    @DisplayName("a cord is a flex, so it refuses a span")
    void itRefusesToReachTooFar() {
        assertNull(CordRoute.settle(EMPTY, new Vec3(0.5, 3.0, 0.5),
                new Vec3(0.5 + CordRoute.MAX_REACH + 0.5, 3.0, 0.5)));
        assertNull(CordRoute.settle(EMPTY, new Vec3(0.5, 3.0, 0.5), new Vec3(0.5, 3.0, 0.5)),
                "a cord to the point it starts from is not a cord");
    }

    @Test
    @DisplayName("both ends stay exactly where they were plugged in")
    void itsEndsArePinned() {
        final Vec3 from = new Vec3(0.5, 2.4, 0.5);
        final Vec3 to = new Vec3(3.0, 1.6, 0.5);
        final List<Vec3> cord = CordRoute.settle(FLOOR, from, to);
        assertNotNull(cord);
        assertTrue(cord.get(0).distanceTo(from) < 1.0e-6, "the plug moved out of the socket");
        assertTrue(cord.get(cord.size() - 1).distanceTo(to) < 1.0e-6, "the far end left the machine");
    }

    @Test
    @DisplayName("a straight run is stored as a straight run")
    void itSimplifiesWhatIsAlreadyStraight() {
        final List<Vec3> cord = CordRoute.settle(EMPTY, new Vec3(0.5, 4.0, 0.5), new Vec3(0.5, 1.5, 0.5));
        assertNotNull(cord);
        assertTrue(cord.size() <= 4, "a straight cord kept " + cord.size() + " points");
    }

    private static void assertFree(CordRoute.Solid world, List<Vec3> cord) {
        for (Vec3 p : cord) {
            assertTrue(world.at(p.x, p.y, p.z) == null,
                    "the cord passes through something solid at " + p);
        }
    }

    private static double lowest(List<Vec3> cord) {
        return cord.stream().mapToDouble(p -> p.y).min().orElseThrow();
    }

    private static double length(List<Vec3> cord) {
        double total = 0.0;
        for (int i = 1; i < cord.size(); i++) {
            total += cord.get(i).distanceTo(cord.get(i - 1));
        }
        return total;
    }
}
