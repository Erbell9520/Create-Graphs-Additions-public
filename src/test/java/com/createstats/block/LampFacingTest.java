package com.createstats.block;

import com.createstats.vehiclelight.VehicleLightShapes;
import com.createstats.vehiclelight.VehicleLightKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LampFacingTest {
    private static final Path BLOCKSTATES =
            Path.of("src/main/resources/assets/create_stats/blockstates");

    private static final Path MODELS =
            Path.of("src/main/resources/assets/create_stats/models/block");

    private static final Map<Direction, Map<String, Integer>> ROTATION = Map.of(
            Direction.UP, Map.of(),
            Direction.DOWN, Map.of("x", 180),
            Direction.NORTH, Map.of("x", 90),
            Direction.SOUTH, Map.of("x", 90, "y", 180),
            Direction.WEST, Map.of("x", 90, "y", 270),
            Direction.EAST, Map.of("x", 90, "y", 90));

    private static final Map<String, VoxelShape> MOUNTED = Map.ofEntries(
            Map.entry("caged_bulkhead_lamp", LampShapes.CAGED_BULKHEAD),
            Map.entry("high_bay_lamp", LampShapes.HIGH_BAY),
            Map.entry("strip_lamp", LampShapes.STRIP),
            Map.entry("dome_lamp", LampShapes.DOME),
            Map.entry("wall_sconce", LampShapes.SCONCE),
            Map.entry("panel_lamp", LampShapes.PANEL),
            Map.entry("pendant_lamp", LampShapes.PENDANT),
            Map.entry("flood_lamp", LampShapes.FLOOD),
            Map.entry("vapour_tight_lamp", LampShapes.VAPOUR_TIGHT),
            Map.entry("well_glass_lamp", LampShapes.WELL_GLASS),
            Map.entry("filament_bulb", LampShapes.FILAMENT),
            Map.entry("emergency_lamp", LampShapes.EMERGENCY),
            Map.entry("bankers_lamp", LampShapes.BANKERS),
            Map.entry("rocker_switch", LampShapes.ROCKER),
            Map.entry("rotary_dimmer_dial", LampShapes.DIMMER_DIAL),
            Map.entry("rotary_dimmer_puck", LampShapes.DIMMER_PUCK),
            Map.entry("time_switch", LampShapes.TIME_SWITCH),
            Map.entry("toggle_switch", LampShapes.TOGGLE),
            Map.entry("double_switch", LampShapes.DOUBLE),
            Map.entry("isolator_switch", LampShapes.ISOLATOR),
            Map.entry("main_breaker", LampShapes.MAIN_BREAKER),
            Map.entry("creative_main_breaker", LampShapes.MAIN_BREAKER),
            Map.entry("pa_speaker", LampShapes.PA_SPEAKER),
            Map.entry("pa_megaphone", LampShapes.PA_MEGAPHONE),

            Map.entry("vehicle_light_white", VehicleLightShapes.up(VehicleLightKind.WHITE)),
            Map.entry("vehicle_light_red", VehicleLightShapes.up(VehicleLightKind.RED)),
            Map.entry("vehicle_light_amber", VehicleLightShapes.up(VehicleLightKind.AMBER)));

    private static final Set<String> LAMPS = Set.of(
            "caged_bulkhead_lamp", "high_bay_lamp", "strip_lamp", "dome_lamp", "wall_sconce",
            "panel_lamp", "pendant_lamp", "flood_lamp", "vapour_tight_lamp", "well_glass_lamp",
            "filament_bulb", "emergency_lamp", "bankers_lamp");

    private static final Set<String> MODEL_ONLY = Set.of("push_button", "portable_power_interface",

            "vacuum_sniffer");

    @Test
    void everyHitboxSitsAgainstTheFaceItsFacingImplies() {
        for (Map.Entry<String, VoxelShape> entry : MOUNTED.entrySet()) {
            for (Direction facing : Direction.values()) {
                for (int spin = 0; spin < 4; spin++) {
                    final VoxelShape rotated = LampShapes.rotate(entry.getValue(), facing, spin);
                    assertEquals(facing.getOpposite(), mountingFace(rotated),
                            entry.getKey() + " hitbox facing=" + facing + " spin=" + spin);
                }
            }
        }
    }

    @Test
    void turningNeverMovesAFittingOffItsFace() {
        for (Direction facing : Direction.values()) {
            for (int spin = 0; spin < 4; spin++) {
                assertEquals(facing.getOpposite(), MountedPart.baseFace(facing),
                        "base face at spin=" + spin);
                final Direction front = MountedPart.authoredFront(facing, spin);
                assertTrue(front.getAxis() != facing.getAxis(),
                        "front of facing=" + facing + " spin=" + spin + " left the mounting plane");
            }
        }
    }

    @Test
    void everyTurnIsReachableFromThePlacementRule() {
        for (Direction facing : Direction.values()) {
            final Set<Direction> fronts = new HashSet<>();
            for (int spin = 0; spin < 4; spin++) {
                final Direction front = MountedPart.authoredFront(facing, spin);
                assertTrue(fronts.add(front),
                        "facing=" + facing + " reaches " + front + " from two different turns");
                assertEquals(spin, MountedPart.spinTowards(facing, front),
                        "facing=" + facing + " spin=" + spin + " does not round trip");
            }
            assertEquals(4, fronts.size(), "facing=" + facing);
        }
    }

    @Test
    void theRendererRotationAgreesWithTheBlockstateOne() {
        for (Direction facing : Direction.values()) {
            assertEquals(facing.getOpposite(), MountedPart.baseFace(facing),
                    "renderer rotation for facing=" + facing);
        }
    }

    @Test
    void everyMountedBlockstateIsCovered() throws IOException {
        final List<String> mounted = new ArrayList<>();
        try (Stream<Path> files = Files.list(BLOCKSTATES)) {
            for (Path file : files.sorted().toList()) {
                final String file_name = file.getFileName().toString();
                if (file_name.endsWith(".json") && usesTheMountedTable(file)) {
                    mounted.add(file_name.substring(0, file_name.length() - ".json".length()));
                }
            }
        }
        assertTrue(mounted.size() >= MOUNTED.size(),
                "found fewer mounted blockstates than MOUNTED lists: " + mounted);
        for (String name : mounted) {
            assertTrue(MOUNTED.containsKey(name) || MODEL_ONLY.contains(name),
                    name + " mounts on a surface but is not covered by LampFacingTest. Add its "
                            + "LampShapes constant to MOUNTED, or its name to MODEL_ONLY if it has "
                            + "none.");
        }
    }

    private static boolean usesTheMountedTable(Path file) throws IOException {
        final JsonObject root = JsonParser
                .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.has("variants") || !root.get("variants").isJsonObject()) {
            return false;
        }
        final JsonObject variants = root.getAsJsonObject("variants");
        final Set<Direction> seen = new HashSet<>();
        for (String key : variants.keySet()) {
            final Direction facing = facingOrNull(key);
            if (facing == null || !variants.get(key).isJsonObject()) {
                return false;
            }
            final JsonObject variant = variants.getAsJsonObject(key);
            final Map<String, Integer> want = ROTATION.get(facing);
            for (String axis : List.of("x", "y")) {
                final int got = variant.has(axis) ? variant.get(axis).getAsInt() : 0;
                if (got != want.getOrDefault(axis, 0)) {
                    return false;
                }
            }
            seen.add(facing);
        }
        return seen.size() == Direction.values().length;
    }

    @Test
    void everyModelIsDrawnOnTheFaceItsFacingImplies() throws IOException {
        final List<String> names = new ArrayList<>(MOUNTED.keySet());
        names.addAll(MODEL_ONLY);
        for (String name : names) {
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
            assertTrue(seen >= 6, name + " should cover all six facings, saw " + seen + " variants");
        }
    }

    @Test
    void everyLampHitboxIsTheSizeOfItsOwnModel() throws IOException {
        for (String name : LAMPS) {
            final double[] model = modelBounds(name + "_item");
            final VoxelShape shape = MOUNTED.get(name);
            assertTrue(shape != null, name + " is a lamp but has no LampShapes constant");
            final double[] got = {
                    shape.min(Direction.Axis.X) * 16.0, shape.min(Direction.Axis.Y) * 16.0,
                    shape.min(Direction.Axis.Z) * 16.0, shape.max(Direction.Axis.X) * 16.0,
                    shape.max(Direction.Axis.Y) * 16.0, shape.max(Direction.Axis.Z) * 16.0};
            for (int i = 0; i < 6; i++) {
                assertEquals(model[i], got[i], 1.0e-4,
                        name + " hitbox bound " + i + " does not match its model. Run "
                                + "python gen_lamps.py --shared and paste the LampShapes line.");
            }
        }
    }

    @Test
    void everyTurnedModelIsTurnedTheWayTheHitboxIs() throws IOException {
        int checked = 0;
        for (String name : MOUNTED.keySet()) {
            final double[] flat = modelBounds(name);
            for (int spin = 1; spin < 4; spin++) {
                final Path turnedFile = MODELS.resolve(name + "_spin" + spin + ".json");
                if (!Files.exists(turnedFile)) {
                    continue;
                }

                final VoxelShape turned = LampShapes.rotate(
                        Shapes.box(flat[0] / 16.0, flat[1] / 16.0, flat[2] / 16.0,
                                flat[3] / 16.0, flat[4] / 16.0, flat[5] / 16.0),
                        Direction.UP, spin);
                final double[] want = {
                        turned.min(Direction.Axis.X) * 16.0, turned.min(Direction.Axis.Y) * 16.0,
                        turned.min(Direction.Axis.Z) * 16.0, turned.max(Direction.Axis.X) * 16.0,
                        turned.max(Direction.Axis.Y) * 16.0, turned.max(Direction.Axis.Z) * 16.0};
                final double[] got = modelBounds(name + "_spin" + spin);
                for (int i = 0; i < 6; i++) {
                    assertEquals(want[i], got[i], 1.0e-4,
                            name + " spin=" + spin + " bound " + i + ": the baked model and "
                                    + "LampShapes.rotate disagree about which way a quarter turn "
                                    + "goes. Re-run python gen_lamps.py.");
                }
                checked++;
            }
        }
        assertTrue(checked >= 3, "found no turned models at all, saw " + checked);
    }

    private static double[] modelBounds(String name) throws IOException {
        final JsonObject model = JsonParser
                .parseString(Files.readString(MODELS.resolve(name + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        final double[] bounds = {
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (var element : model.getAsJsonArray("elements")) {
            final JsonObject box = element.getAsJsonObject();
            for (int axis = 0; axis < 3; axis++) {
                bounds[axis] = Math.min(bounds[axis], box.getAsJsonArray("from").get(axis).getAsDouble());
                bounds[axis + 3] = Math.max(bounds[axis + 3], box.getAsJsonArray("to").get(axis).getAsDouble());
            }
        }
        return bounds;
    }

    private static Direction mountingFace(VoxelShape shape) {
        Direction found = null;
        for (Direction dir : Direction.values()) {
            final Direction.Axis axis = dir.getAxis();
            final boolean atMin = shape.min(axis) < 1.0e-6;
            final boolean atMax = shape.max(axis) > 1.0 - 1.0e-6;
            final boolean touches = dir.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                    ? atMin : atMax;
            if (touches && !(atMin && atMax)) {
                assertEquals(null, found, "shape hugs more than one face");
                found = dir;
            }
        }
        assertTrue(found != null, "shape hugs no face at all");
        return found;
    }

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

    private static Direction facingOrNull(String variantKey) {
        for (String part : variantKey.split(",")) {
            if (part.startsWith("facing=")) {
                return Direction.byName(part.substring("facing=".length()));
            }
        }
        return null;
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
