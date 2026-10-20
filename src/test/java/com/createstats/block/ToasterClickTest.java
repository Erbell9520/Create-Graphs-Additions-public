package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ToasterClickTest {
    private static final double FRONT_PLANE = 2.2;

    private static final double DECK_PLANE = 11.58;

    private static final BlockPos POS = new BlockPos(-197, 69, -23);

    private static final Object[][] FASCIA = {
            {ToasterControls.Zone.LEVER, 3.60, 9.90},
            {ToasterControls.Zone.DIAL, 11.30, 7.90},
            {ToasterControls.Zone.BAGEL, 8.40, 4.35},
            {ToasterControls.Zone.CANCEL, 5.90, 4.35},
            {ToasterControls.Zone.TRAY, 8.00, 2.30},
    };

    @Test
    @DisplayName("every control answers from every facing, at the exact plane a ray stops on")
    void everyControlIsHitFromEveryFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Object[] control : FASCIA) {
                assertEquals(control[0],
                        ToasterControls.zoneAt(facing, POS,
                                frontFaceHit(facing, (double) control[1], (double) control[2])),
                        control[0] + " missed on facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("and so do they after the position has been through the packet's float")
    void everyControlSurvivesTheFloatRoundTrip() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Object[] control : FASCIA) {
                final Vec3 exact = frontFaceHit(facing, (double) control[1], (double) control[2]);
                assertEquals(control[0], ToasterControls.zoneAt(facing, POS, throughPacket(exact)),
                        control[0] + " missed on facing " + facing + " after the float round trip");
            }
        }
    }

    @Test
    @DisplayName("the client's exact hit and the server's float agree about every control")
    void bothSidesAgree() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Object[] control : FASCIA) {
                final Vec3 exact = frontFaceHit(facing, (double) control[1], (double) control[2]);
                final ToasterControls.Zone client = ToasterControls.zoneAt(facing, POS, exact);
                final ToasterControls.Zone server =
                        ToasterControls.zoneAt(facing, POS, throughPacket(exact));
                assertNotNull(client, "no control at " + control[0] + " facing " + facing);
                assertEquals(client, server,
                        "client and server disagree about " + control[0] + " facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("each slot answers for itself, from above, on every facing")
    void slotsAnswerFromAbove() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(ToasterControls.Zone.SLOT_A,
                    ToasterControls.zoneAt(facing, POS, deckHit(facing, 10.20, 7.80)),
                    "the left slot missed on facing " + facing);
            assertEquals(ToasterControls.Zone.SLOT_B,
                    ToasterControls.zoneAt(facing, POS, deckHit(facing, 5.80, 7.80)),
                    "the right slot missed on facing " + facing);
        }
    }

    @Test
    @DisplayName("the two slots never answer for each other, which would swap a player's bread")
    void slotsAreNotEachOther() {
        for (double x = 7.80; x <= 8.20; x += 0.04) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                assertNull(ToasterControls.zoneAt(facing, POS, deckHit(facing, x, 7.80)),
                        "the deck between the slots answered as a slot at x " + x
                                + " on facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("no control on the crowded fascia answers for its neighbour")
    void controlsDoNotStealFromEachOther() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Object[] control : FASCIA) {
                final ToasterControls.Zone hit = ToasterControls.zoneAt(facing, POS,
                        frontFaceHit(facing, (double) control[1], (double) control[2]));
                for (Object[] other : FASCIA) {
                    if (other != control) {
                        assertNotEquals(other[0], hit,
                                other[0] + " answered for a click aimed at " + control[0]
                                        + " on facing " + facing);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("a click on bare casing routes to nothing, so the block is not one big button")
    void bareCasingIsNotAControl() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertNull(ToasterControls.zoneAt(facing, POS, frontFaceHit(facing, 6.80, 7.15)),
                    "the badge answered as a control on facing " + facing);

            assertNull(ToasterControls.zoneAt(facing, POS, deckHit(facing, 13.00, 7.80)),
                    "the bare deck answered as a slot on facing " + facing);
        }
    }

    private static Vec3 frontFaceHit(Direction facing, double px, double py) {
        return worldHit(facing, px, py, FRONT_PLANE);
    }

    private static Vec3 deckHit(Direction facing, double px, double pz) {
        return worldHit(facing, px, DECK_PLANE, pz);
    }

    private static Vec3 worldHit(Direction facing, double px, double py, double pz) {
        final double x = px / 16.0;
        final double y = py / 16.0;
        final double z = pz / 16.0;
        final Vec3 local = switch (facing) {
            case EAST -> new Vec3(1.0 - z, y, x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
        return new Vec3(POS.getX() + local.x, POS.getY() + local.y, POS.getZ() + local.z);
    }

    private static Vec3 throughPacket(Vec3 hit) {
        final float rx = (float) (hit.x - POS.getX());
        final float ry = (float) (hit.y - POS.getY());
        final float rz = (float) (hit.z - POS.getZ());
        return new Vec3(POS.getX() + rx, POS.getY() + ry, POS.getZ() + rz);
    }
}
