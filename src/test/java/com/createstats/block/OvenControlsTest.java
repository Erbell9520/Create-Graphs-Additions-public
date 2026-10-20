package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvenControlsTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    private static Vec3 authored(double px, double py, double pz) {
        return new Vec3(px / 16.0, py / 16.0, pz / 16.0);
    }

    private static OvenControls.Zone at(double px, double py, double pz, boolean doorOpen) {
        return OvenControls.zoneAt(Direction.NORTH, ORIGIN, authored(px, py, pz), doorOpen);
    }

    @Test
    @DisplayName("every fascia control claims its own centre and nobody else's")
    void theFasciaControlsAreDistinct() {
        assertEquals(OvenControls.Zone.DIAL, at(12.70, 13.55, 1.30, false));
        assertEquals(OvenControls.Zone.START, at(9.70, 13.55, 1.42, false));
        assertEquals(OvenControls.Zone.LAMP, at(8.10, 13.55, 1.42, false));
    }

    @Test
    @DisplayName("the two buttons do not bleed into each other")
    void theButtonsDoNotOverlap() {
        assertEquals(OvenControls.Zone.START, at(9.03, 13.55, 1.42, false));
        assertEquals(OvenControls.Zone.LAMP, at(8.77, 13.55, 1.42, false));

        assertNull(at(8.90, 13.55, 1.42, false));
    }

    @Test
    @DisplayName("the slack is on the depth axis and only on the depth axis")
    void theMarginIsDepthOnly() {
        final double slack = OvenControls.DEPTH_MARGIN / 2.0;

        assertEquals(OvenControls.Zone.START, at(9.70, 13.55, 0.90 - slack, false));

        assertNull(at(10.38 + OvenControls.DEPTH_MARGIN, 13.55, 1.42, false));
    }

    @Test
    @DisplayName("the cavity is the shelf with the door down and the door with it shut")
    void theDoorIsAMode() {
        assertEquals(OvenControls.Zone.SHELF, at(8.0, 6.0, 1.0, true));
        assertEquals(OvenControls.Zone.DOOR, at(8.0, 6.0, 0.0, false));

        assertEquals(OvenControls.Zone.SHELF, at(8.0, 6.0, 6.0, true));
    }

    @Test
    @DisplayName("an open oven still has a door to click")
    void anOpenOvenCanBeShut() {
        assertEquals(OvenControls.Zone.DOOR, at(8.0, 2.0, 0.5, true));
        assertEquals(OvenControls.Zone.DOOR, at(8.0, 11.5, 0.5, true));
    }

    @Test
    @DisplayName("every control routes the same on all four facings")
    void everyFacingAgrees() {
        final double[][] probes = {
                {12.70, 13.55, 1.30},
                {9.70, 13.55, 1.42},
                {8.10, 13.55, 1.42},
                {8.00, 6.00, 1.00},
                {3.00, 2.00, 0.50},
        };
        for (final double[] probe : probes) {
            final OvenControls.Zone expected =
                    OvenControls.zoneAt(Direction.NORTH, ORIGIN,
                            authored(probe[0], probe[1], probe[2]), true);
            assertNotNull(expected, "the probe at " + probe[0] + ", " + probe[1] + ", " + probe[2]
                    + " lands on no control at all, so this test proves nothing about it");
            for (final Direction facing : Direction.Plane.HORIZONTAL) {
                final Vec3 turned = turn(facing, probe[0], probe[1], probe[2]);
                assertEquals(expected, OvenControls.zoneAt(facing, ORIGIN, turned, true),
                        "facing " + facing + " disagrees about the point "
                                + probe[0] + ", " + probe[1] + ", " + probe[2]);
            }
        }
    }

    private static Vec3 turn(Direction facing, double px, double py, double pz) {
        final double x = px / 16.0;
        final double y = py / 16.0;
        final double z = pz / 16.0;
        return switch (facing) {
            case EAST -> new Vec3(1.0 - z, y, x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
    }

    @Test
    @DisplayName("a click on the hob or the back lands on no control")
    void missesAreMisses() {
        assertNull(at(8.0, 15.8, 8.0, false), "the hob is not a control");
        assertNull(at(8.0, 8.0, 15.5, false), "the back is not a control");
        assertNull(at(8.0, 0.5, 8.0, false), "the plinth is not a control");
    }

    @Test
    @DisplayName("the small controls are declared before the surfaces they sit on")
    void orderPutsSmallControlsFirst() {
        final OvenControls.Zone[] all = OvenControls.Zone.values();
        final int door = indexOf(all, OvenControls.Zone.DOOR);
        final int shelf = indexOf(all, OvenControls.Zone.SHELF);
        for (final OvenControls.Zone zone : all) {
            if (zone == OvenControls.Zone.DOOR || zone == OvenControls.Zone.SHELF) {
                continue;
            }
            assertTrue(indexOf(all, zone) < shelf && indexOf(all, zone) < door,
                    zone + " is declared after the shelf or the door, so a click on it would be "
                            + "answered by whichever of those it sits inside");
        }
        assertTrue(shelf < door,
                "the shelf has to be declared before the door, or a click into an open cavity is "
                        + "answered by the door it just passed through");
    }

    private static int indexOf(OvenControls.Zone[] all, OvenControls.Zone wanted) {
        for (int i = 0; i < all.length; i++) {
            if (all[i] == wanted) {
                return i;
            }
        }
        return -1;
    }
}
