package com.createstats.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** [1] */
public final class LampShapes {

    private LampShapes() {
    }

    // Outer bounds of each lamp, matching gen_lamps.py. Authored pointing UP.
    public static final VoxelShape CAGED_BULKHEAD = Block.box(3, 0, 3, 13, 7, 13);
    public static final VoxelShape HIGH_BAY = Block.box(2.5, 0, 2.5, 13.5, 8, 13.5);
    public static final VoxelShape STRIP = Block.box(0, 0, 5.5, 16, 3.5, 10.5);
    public static final VoxelShape DOME = Block.box(4, 0, 4, 12, 4, 12);
    public static final VoxelShape SCONCE = Block.box(4.5, 0, 3, 11.5, 5, 13);
    public static final VoxelShape PANEL = Block.box(1, 0, 1, 15, 2, 15);

    // Switches, authored the same way: pointing UP, rotated onto the other five faces.
    public static final VoxelShape ROCKER = Block.box(3.5, 0, 2.5, 12.5, 3.0, 13.5);
    public static final VoxelShape TOGGLE = Block.box(4.5, 0, 3.5, 11.5, 4.4, 12.5);
    public static final VoxelShape DOUBLE = Block.box(1.5, 0, 2.5, 14.5, 3.0, 13.5);
    public static final VoxelShape ISOLATOR = Block.box(2.5, 0, 2.5, 13.5, 5.8, 13.5);
    public static final VoxelShape BUTTON = Block.box(4, 0, 4, 12, 4.2, 12);

    private static final Map<VoxelShape, Map<Direction, VoxelShape>> CACHE = new java.util.IdentityHashMap<>();

    /** The shape of an upward-authored lamp once it points {@code facing}. */
    public static synchronized VoxelShape rotate(VoxelShape up, Direction facing) {
        return CACHE.computeIfAbsent(up, LampShapes::buildAll).get(facing);
    }

    private static Map<Direction, VoxelShape> buildAll(VoxelShape up) {
        final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            map.put(facing, transform(up, facing));
        }
        return map;
    }

    /** [2] */
    private static VoxelShape transform(VoxelShape up, Direction facing) {
        if (facing == Direction.UP) {
            return up;
        }
        final VoxelShape[] result = {Shapes.empty()};
        up.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            final double[] b = switch (facing) {
                // Half turn about X: y and z both flip.
                case DOWN -> new double[] {x0, 1 - y1, 1 - z1, x1, 1 - y0, 1 - z0};
                // Quarter turn about X: up becomes south, so y maps onto z.
                case SOUTH -> new double[] {x0, 1 - z1, y0, x1, 1 - z0, y1};
                case NORTH -> new double[] {1 - x1, 1 - z1, 1 - y1, 1 - x0, 1 - z0, 1 - y0};
                case EAST -> new double[] {y0, 1 - z1, 1 - x1, y1, 1 - z0, 1 - x0};
                case WEST -> new double[] {1 - y1, 1 - z1, x0, 1 - y0, 1 - z0, x1};
                default -> new double[] {x0, y0, z0, x1, y1, z1};
            };
            result[0] = Shapes.or(result[0], Shapes.box(b[0], b[1], b[2], b[3], b[4], b[5]));
        });
        return result[0].optimize();
    }
}
