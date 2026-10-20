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

class BreadSlicerClickTest {
    private static final double FASCIA_PLANE = 1.06;

    private static final double DRAWER_PLANE = 0.96;

    private static final double DECK_PLANE = 8.15;

    private static final double HOOD_PLANE = 14.30;

    private static final double CANOPY_PLANE = 11.82;

    private static final double RIM_FRONT_PLANE = 1.25;

    private static final BlockPos POS = new BlockPos(-197, 69, -23);

    private static final Object[][] FASCIA = {
            {BreadSlicerControls.Zone.LEVER, 4.70, 5.80},
            {BreadSlicerControls.Zone.START, 9.85, 5.72},
            {BreadSlicerControls.Zone.STOP, 12.60, 5.72},
    };

    @Test
    @DisplayName("every fascia control answers from every facing, in both hood states")
    void everyControlIsHitFromEveryFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean hoodOpen : new boolean[]{false, true}) {
                for (Object[] control : FASCIA) {
                    assertEquals(control[0], BreadSlicerControls.zoneAt(facing, POS,
                                    worldHit(facing, (double) control[1], (double) control[2],
                                            FASCIA_PLANE), hoodOpen),
                            control[0] + " missed on facing " + facing + ", hood open " + hoodOpen);
                }
                assertEquals(BreadSlicerControls.Zone.DRAWER,
                        BreadSlicerControls.zoneAt(facing, POS,
                                worldHit(facing, 8.00, 3.20, DRAWER_PLANE), hoodOpen),
                        "the crumb drawer missed on facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("and so do they after the position has been through the packet's float")
    void everyControlSurvivesTheFloatRoundTrip() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Object[] control : FASCIA) {
                final Vec3 exact = worldHit(facing, (double) control[1], (double) control[2],
                        FASCIA_PLANE);
                assertEquals(control[0],
                        BreadSlicerControls.zoneAt(facing, POS, throughPacket(exact), false),
                        control[0] + " missed on facing " + facing + " after the float round trip");
            }
        }
    }

    @Test
    @DisplayName("the client's exact hit and the server's float agree about every control")
    void bothSidesAgree() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (boolean hoodOpen : new boolean[]{false, true}) {
                for (Object[] control : FASCIA) {
                    final Vec3 exact = worldHit(facing, (double) control[1], (double) control[2],
                            FASCIA_PLANE);
                    final BreadSlicerControls.Zone client =
                            BreadSlicerControls.zoneAt(facing, POS, exact, hoodOpen);
                    final BreadSlicerControls.Zone server =
                            BreadSlicerControls.zoneAt(facing, POS, throughPacket(exact), hoodOpen);
                    assertNotNull(client, "no control at " + control[0] + " facing " + facing);
                    assertEquals(client, server,
                            "client and server disagree about " + control[0] + " facing " + facing);
                }
            }
        }
    }

    @Test
    @DisplayName("the lid opens when it is down and the deck loads when it is up, same aim")
    void theSameClickOpensTheLidThenLoadsTheDeck() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(BreadSlicerControls.Zone.HOOD,
                    BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, HOOD_PLANE, 7.00), false),
                    "a click on the shut lid did not open it, facing " + facing);
            assertEquals(BreadSlicerControls.Zone.DECK,
                    BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, DECK_PLANE, 7.00), true),
                    "a click on the open deck did not reach the deck, facing " + facing);
        }
    }

    @Test
    @DisplayName("with the lid up there is still somewhere to click to shut it")
    void theHoodCanAlwaysBeShutAgain() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(BreadSlicerControls.Zone.HOOD,
                    BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, 10.00, CANOPY_PLANE), true),
                    "the canopy face did not shut the lid, facing " + facing);
            assertEquals(BreadSlicerControls.Zone.HOOD,
                    BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, HOOD_PLANE, 13.00), true),
                    "the top of the canopy did not shut the lid, facing " + facing);

            assertEquals(BreadSlicerControls.Zone.HOOD,
                    BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, 8.00, RIM_FRONT_PLANE), true),
                    "the front lip did not shut the lid, facing " + facing);
        }
    }

    @Test
    @DisplayName("the deck is unreachable while the lid is down, whatever a ray hits")
    void theDeckIsSealedWhileTheLidIsDown() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (double z = 2.0; z <= 11.0; z += 0.5) {
                assertNotEquals(BreadSlicerControls.Zone.DECK,
                        BreadSlicerControls.zoneAt(facing, POS,
                                worldHit(facing, 8.00, HOOD_PLANE, z), false),
                        "the deck answered through a shut lid at z " + z + ", facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("no control on the crowded fascia answers for its neighbour")
    void controlsDoNotStealFromEachOther() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Object[] control : FASCIA) {
                final BreadSlicerControls.Zone hit = BreadSlicerControls.zoneAt(facing, POS,
                        worldHit(facing, (double) control[1], (double) control[2], FASCIA_PLANE),
                        false);
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
    @DisplayName("the two buttons never answer for each other, which would start a stopped machine")
    void theButtonsAreNotEachOther() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (double x = 8.90; x <= 10.80; x += 0.1) {
                assertEquals(BreadSlicerControls.Zone.START,
                        BreadSlicerControls.zoneAt(facing, POS,
                                worldHit(facing, x, 5.72, FASCIA_PLANE), false),
                        "the green cap did not answer at x " + x + ", facing " + facing);
            }
            for (double x = 11.50; x <= 13.70; x += 0.1) {
                assertEquals(BreadSlicerControls.Zone.STOP,
                        BreadSlicerControls.zoneAt(facing, POS,
                                worldHit(facing, x, 5.72, FASCIA_PLANE), false),
                        "the red cap did not answer at x " + x + ", facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("a click on bare casing routes to nothing, so the block is not one big button")
    void bareCasingIsNotAControl() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertNull(BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, 5.72, FASCIA_PLANE), false),
                    "the blank fascia answered as a control on facing " + facing);

            assertNull(BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 15.00, 5.00, 8.00), false),
                    "the cabinet flank answered as a control on facing " + facing);

            assertNull(BreadSlicerControls.zoneAt(facing, POS,
                            worldHit(facing, 8.00, 1.50, 2.10), false),
                    "the plinth answered as a control on facing " + facing);
        }
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
