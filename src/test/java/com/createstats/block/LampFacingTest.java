package com.createstats.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class LampFacingTest {

    private static final Path BLOCKSTATES =
            Path.of("src/main/resources/assets/create_stats/blockstates");

    /** Every block that mounts on a surface, with the shape its hitbox is authored from. */
    private static final Map<String, VoxelShape> MOUNTED = Map.of(
            "caged_bulkhead_lamp", LampShapes.CAGED_BULKHEAD,
            "high_bay_lamp", LampShapes.HIGH_BAY,
            "strip_lamp", LampShapes.STRIP,
            "dome_lamp", LampShapes.DOME,
            "wall_sconce", LampShapes.SCONCE,
            "panel_lamp", LampShapes.PANEL,
            "rocker_switch", LampShapes.ROCKER,
            "toggle_switch", LampShapes.TOGGLE,
            "double_switch", LampShapes.DOUBLE,
            "isolator_switch", LampShapes.ISOLATOR);

    @Test
    void everyHitboxSitsAgainstTheFaceItsFacingImplies() {
        for (Map.Entry<String, VoxelShape> entry : MOUNTED.entrySet()) {
            for (Direction facing : Direction.values()) {
                final VoxelShape rotated = LampShapes.rotate(entry.getValue(), facing);
                assertEquals(facing.getOpposite(), mountingFace(rotated),
                        entry.getKey() + " hitbox facing=" + facing);
            }
        }
    }

    @Test
    void everyModelIsDrawnOnTheFaceItsFacingImplies() throws IOException {
        for (String name : List.of("caged_bulkhead_lamp", "high_bay_lamp", "strip_lamp", "dome_lamp",
                "wall_sconce", "panel_lamp", "rocker_switch", "toggle_switch", "double_switch",
                "isolator_switch", "push_button")) {
            final JsonObject variants = JsonParser
                    .parseString(Files.readString(BLOCKSTATES.resolve(name + ".json"), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("variants");
            int seen = 0;
            for (String key : variants.keySet()) {
                final Direction facing = facingOf(key);
                final JsonObject variant = variants.getAsJsonObject(key);
                final int x = variant.has("x") ? variant.get("x").getAsInt() : 0;
                final int y = variant.has("y") ? variant.get("y").getAsInt() : 0;
                assertEquals(facing.getOpposite(), baseFaceAfter(x, y),
                        name + " model " + key + " (x=" + x + ", y=" + y + ")");
                seen++;
            }
            assertTrue(seen >= 12, name + " should cover all six facings, saw " + seen + " variants");
        }
    }

    /** [2] */
    private static Direction mountingFace(VoxelShape shape) {
        Direction found = null;
        for (Direction dir : Direction.values()) {
            final Direction.Axis axis = dir.getAxis();
            final double thickness = shape.max(axis) - shape.min(axis);
            final boolean touches = dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    ? shape.min(axis) < 1.0e-6
                    : shape.max(axis) > 1.0 - 1.0e-6;
            if (touches && thickness <= 0.5 + 1.0e-6) {
                assertEquals(null, found, "shape hugs more than one face");
                found = dir;
            }
        }
        assertTrue(found != null, "shape hugs no face at all");
        return found;
    }

    /** [3] */
    private static Direction baseFaceAfter(int x, int y) {
        int[] v = {0, -1, 0};
        v = rotateAboutX(v, x);
        v = rotateAboutY(v, y);
        for (Direction dir : Direction.values()) {
            if (dir.getStepX() == v[0] && dir.getStepY() == v[1] && dir.getStepZ() == v[2]) {
                return dir;
            }
        }
        throw new AssertionError("rotation produced no axis direction");
    }

    private static int[] rotateAboutX(int[] v, int degrees) {
        final int c = cos(degrees);
        final int s = sin(degrees);
        return new int[] {v[0], v[1] * c + v[2] * s, -v[1] * s + v[2] * c};
    }

    private static int[] rotateAboutY(int[] v, int degrees) {
        final int c = cos(degrees);
        final int s = sin(degrees);
        return new int[] {v[0] * c - v[2] * s, v[1], v[0] * s + v[2] * c};
    }

    private static int cos(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 0 -> 1;
            case 180 -> -1;
            default -> 0;
        };
    }

    private static int sin(int degrees) {
        return switch (Math.floorMod(degrees, 360)) {
            case 90 -> 1;
            case 270 -> -1;
            default -> 0;
        };
    }

    private static Direction facingOf(String variantKey) {
        for (String part : variantKey.split(",")) {
            if (part.startsWith("facing=")) {
                return Direction.byName(part.substring("facing=".length()));
            }
        }
        throw new AssertionError("no facing in variant key: " + variantKey);
    }
}
