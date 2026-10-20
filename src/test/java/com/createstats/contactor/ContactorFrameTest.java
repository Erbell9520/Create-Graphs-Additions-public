package com.createstats.contactor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactorFrameTest {
    private static final Path BLOCKSTATE = Path.of("src/main/resources/assets/create_stats/blockstates/contactor.json");

    private static double[] variant(double x, double y, double z, int rx, int ry) {
        double[] v = {x - 0.5, y - 0.5, z - 0.5};
        v = aboutX(v, -rx);
        v = aboutY(v, -ry);
        return new double[] {v[0] + 0.5, v[1] + 0.5, v[2] + 0.5};
    }

    private static double[] aboutX(double[] v, int degrees) {
        final double a = Math.toRadians(degrees);
        final double c = Math.round(Math.cos(a));
        final double s = Math.round(Math.sin(a));
        return new double[] {v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    private static double[] aboutY(double[] v, int degrees) {
        final double a = Math.toRadians(degrees);
        final double c = Math.round(Math.cos(a));
        final double s = Math.round(Math.sin(a));
        return new double[] {v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }

    @Test
    @DisplayName("every variant in the blockstate turns the model exactly as ContactorFrame turns a point")
    void theBlockstateAgrees() throws IOException {
        final JsonObject variants = JsonParser.parseString(Files.readString(BLOCKSTATE, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("variants");

        final double[][] points = {{4.5, 14.45, 2.65}, {2.6, 13.45, 5.2}, {1.22, 6.55, 4.22}, {11.5, 1.55, 2.65}};
        int checked = 0;
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < 4; spin++) {
                final JsonObject v = variants.getAsJsonObject("facing=" + facing.getSerializedName() + ",spin=" + spin);
                assertNotNull(v, "no variant for " + facing + " spin " + spin);
                assertEquals("create_stats:block/contactor/base", v.get("model").getAsString());
                final int rx = v.has("x") ? v.get("x").getAsInt() : 0;
                final int ry = v.has("y") ? v.get("y").getAsInt() : 0;
                final int effective = ContactorFrame.effectiveSpin(facing, spin);
                for (double[] p : points) {
                    final double[] model = {(16.0 - p[0]) / 16.0, p[2] / 16.0, p[1] / 16.0};
                    final double[] fromJson = variant(model[0], model[1], model[2], rx, ry);
                    final double[] fromJava = ContactorFrame.toBlock(facing, effective, p[0], p[1], p[2]);
                    for (int i = 0; i < 3; i++) {
                        assertEquals(fromJson[i], fromJava[i], 1e-9,
                                facing + " spin " + spin + " point " + java.util.Arrays.toString(p));
                    }
                }
                checked++;
            }
        }
        assertEquals(24, checked);
    }

    @Test
    @DisplayName("on a wall the left coil reads the block on the viewer's left, and the top is up")
    void wallsKnowLeftFromRight() {
        assertEquals(Direction.EAST, ContactorFrame.leftOf(Direction.NORTH, 0));
        assertEquals(Direction.WEST, ContactorFrame.leftOf(Direction.SOUTH, 0));
        assertEquals(Direction.SOUTH, ContactorFrame.leftOf(Direction.EAST, 0));
        assertEquals(Direction.NORTH, ContactorFrame.leftOf(Direction.WEST, 0));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            assertEquals(Direction.UP, ContactorFrame.topOf(facing, 0), "upright on " + facing);
            assertEquals(ContactorFrame.leftOf(facing, 0).getOpposite(), ContactorFrame.rightOf(facing, 0));
            assertEquals(0, ContactorFrame.effectiveSpin(facing, 3), "a wall never turns");
            assertEquals(0, ContactorFrame.spinFor(facing, Direction.NORTH));
        }
    }

    @Test
    @DisplayName("on a floor the top points away from the player and on a ceiling back at them; either way its left is theirs")
    void floorsAndCeilingsFaceThePlayer() {
        for (Direction look : Direction.Plane.HORIZONTAL) {
            final Direction playersLeft = look.getCounterClockWise();
            final int floor = ContactorFrame.spinFor(Direction.UP, look);
            assertEquals(look, ContactorFrame.topOf(Direction.UP, floor), "floor, looking " + look);
            assertEquals(playersLeft, ContactorFrame.leftOf(Direction.UP, floor), "floor, looking " + look);
            final int ceiling = ContactorFrame.spinFor(Direction.DOWN, look);
            assertEquals(look.getOpposite(), ContactorFrame.topOf(Direction.DOWN, ceiling), "ceiling, looking " + look);
            assertEquals(playersLeft, ContactorFrame.leftOf(Direction.DOWN, ceiling), "ceiling, looking " + look);
        }
    }

    @Test
    @DisplayName("every stud's anchor and guidance box sit on the block's own hitbox, on every face and turn")
    void studsSitOnTheHitbox() {
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < 4; spin++) {
                final AABB bounds = ContactorStuds.shape(facing, spin).bounds();
                for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
                    final Vec3 anchor = ContactorStuds.anchor(facing, spin, stud);
                    assertTrue(bounds.inflate(1e-6).contains(anchor), facing + " " + spin + " stud " + stud);
                    assertTrue(ContactorStuds.box(facing, spin, stud).contains(anchor));

                    assertEquals(stud, ContactorStuds.pick(facing, spin, anchor));
                }
            }
        }
    }

    @Test
    @DisplayName("the hitbox hugs the face it is mounted on and stands no further out than the flag bezels")
    void theHitboxIsMounted() {
        for (Direction facing : Direction.values()) {
            final AABB b = ContactorStuds.shape(facing, ContactorFrame.effectiveSpin(facing, 1)).bounds();
            final Direction back = facing.getOpposite();
            final double backFace = switch (back) {
                case DOWN -> b.minY;
                case UP -> 1.0 - b.maxY;
                case NORTH -> b.minZ;
                case SOUTH -> 1.0 - b.maxZ;
                case WEST -> b.minX;
                case EAST -> 1.0 - b.maxX;
            };
            assertEquals(0.0, backFace, 1e-9, "the back of a contactor on " + facing + " is against its wall");
            final double depth = switch (facing.getAxis()) {
                case X -> b.getXsize();
                case Y -> b.getYsize();
                case Z -> b.getZsize();
            };
            assertEquals(5.42 / 16.0, depth, 1e-9, "depth on " + facing);
        }
    }
}
