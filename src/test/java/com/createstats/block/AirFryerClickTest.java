package com.createstats.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AirFryerClickTest {
    private static final double FRONT_PLANE = 2.2;

    private static final BlockPos POS = new BlockPos(-197, 69, -23);

    @Test
    @DisplayName("the start button answers from every facing, at the exact plane a ray stops on")
    void startButtonIsHitFromEveryFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(AirFryerControls.Zone.START, AirFryerControls.zoneAt(facing, POS,
                            frontFaceHit(facing, 5.2, 11.975)),
                    "the start button missed on facing " + facing);
        }
    }

    @Test
    @DisplayName("and so does it after the position has been through the packet's float")
    void startButtonSurvivesTheFloatRoundTrip() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            final Vec3 exact = frontFaceHit(facing, 5.2, 11.975);
            assertEquals(AirFryerControls.Zone.START, AirFryerControls.zoneAt(facing, POS, throughPacket(exact)),
                    "the start button missed on facing " + facing + " after the float round trip");
        }
    }

    @Test
    @DisplayName("the client's exact hit and the server's float agree about every control")
    void bothSidesAgree() {
        final double[][] controls = {
                {5.2, 11.975},
                {12.5, 14.9},
                {8.0, 5.0},
        };
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (double[] c : controls) {
                final Vec3 exact = frontFaceHit(facing, c[0], c[1]);
                final AirFryerControls.Zone client = AirFryerControls.zoneAt(facing, POS, exact);
                final AirFryerControls.Zone server = AirFryerControls.zoneAt(facing, POS, throughPacket(exact));
                assertNotNull(client, "no control at " + c[0] + "," + c[1] + " facing " + facing);
                assertEquals(client, server,
                        "client and server disagree at " + c[0] + "," + c[1] + " facing " + facing);
            }
        }
    }

    @Test
    @DisplayName("a click on bare casing still routes to nothing, so the block is not one big button")
    void emptyCasingIsNotAControl() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(null, AirFryerControls.zoneAt(facing, POS, frontFaceHit(facing, 9.5, 11.9)),
                    "the fascia beside the start button answered as a control on facing " + facing);
            assertEquals(null, AirFryerControls.zoneAt(facing, POS, frontFaceHit(facing, 2.0, 14.9)),
                    "the crown beside the rocker answered as a control on facing " + facing);
        }
    }

    private static Vec3 frontFaceHit(Direction facing, double px, double py) {
        final double x = px / 16.0;
        final double y = py / 16.0;
        final double z = FRONT_PLANE / 16.0;
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
