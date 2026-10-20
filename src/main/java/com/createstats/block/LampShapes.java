package com.createstats.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LampShapes {
    private LampShapes() {
    }

    // real model extents. the high bay used to have a pixel of unclickable aluminium all round

    public static final VoxelShape CAGED_BULKHEAD = Block.box(2.5, 0, 1, 13.5, 7.9, 13.5);
    public static final VoxelShape HIGH_BAY = Block.box(1.5, 0, 1.5, 14.5, 8.0, 14.5);
    public static final VoxelShape STRIP = Block.box(0.5, 0, 4, 15.5, 4.4, 10.4);
    public static final VoxelShape DOME = Block.box(4.4, 0, 4.4, 11.6, 5.6, 11.6);
    public static final VoxelShape SCONCE = Block.box(4.2, 0, 2.6, 11.8, 5.3, 13.4);
    public static final VoxelShape PANEL = Block.box(0.5, 0, 0.5, 15.5, 1.9, 15.5);

    public static final VoxelShape PENDANT = Block.box(2.6, 0, 2.6, 13.4, 12.9, 13.4);
    public static final VoxelShape FLOOD = Block.box(2.6, 0, 2.6, 13.4, 9.2, 13.4);
    public static final VoxelShape VAPOUR_TIGHT = Block.box(0.8, 0, 3.2, 15.2, 5.0, 11.6);
    public static final VoxelShape WELL_GLASS = Block.box(3.0, 0, 1.6, 13.0, 9.8, 13.0);
    public static final VoxelShape FILAMENT = Block.box(5.4, 0, 5.4, 10.6, 9.1, 10.6);
    public static final VoxelShape EMERGENCY = Block.box(2.4, 0, 4.8, 13.6, 7.9, 11.2);

    public static final VoxelShape BANKERS = Block.box(2.5, 0, 2.0, 13.5, 12.5, 12.5);

    public static final VoxelShape PA_SPEAKER = Block.box(2.0, 0.0, 2.0, 14.0, 10.8, 14.0);
    public static final VoxelShape PA_MEGAPHONE = Block.box(2.0, 0.0, 2.0, 14.0, 13.6, 14.0);

    public static final VoxelShape ROCKER = Block.box(3, 0, 2, 13, 3.4, 14);
    public static final VoxelShape TOGGLE = Block.box(4, 0, 3, 12, 6.6, 13);
    public static final VoxelShape DOUBLE = Block.box(1.5, 0, 2, 14.5, 3.4, 14);
    public static final VoxelShape ISOLATOR = Block.box(1.9, 0, 0.6, 13.5, 6.5, 13.5);
    public static final VoxelShape BUTTON = Block.box(3.4, 0, 3.4, 12.6, 4.6, 12.6);

    public static final VoxelShape DIMMER_DIAL = Block.box(1, 0, 1, 15, 5.2, 15);
    public static final VoxelShape DIMMER_PUCK = Block.box(1, 0, 1, 15, 2.7, 15);

    public static final VoxelShape TIME_SWITCH = Block.box(1, 0, 1, 15, 2.9, 15);

    public static final VoxelShape MAIN_BREAKER = Block.box(1.9, 0, 1.9, 14.1, 8.0, 14.1);

    private static final Map<VoxelShape, List<Map<Direction, VoxelShape>>> CACHE =
            new java.util.IdentityHashMap<>();

    public static VoxelShape rotate(VoxelShape up, Direction facing) {
        return rotate(up, facing, 0);
    }

    public static synchronized VoxelShape rotate(VoxelShape up, Direction facing, int spin) {
        return CACHE.computeIfAbsent(up, LampShapes::buildAll)
                .get(Math.floorMod(spin, 4)).get(facing);
    }

    private static List<Map<Direction, VoxelShape>> buildAll(VoxelShape up) {
        final List<Map<Direction, VoxelShape>> turns = new java.util.ArrayList<>(4);
        VoxelShape turned = up;
        for (int spin = 0; spin < 4; spin++) {
            final Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.values()) {
                map.put(facing, transform(turned, facing));
            }
            turns.add(map);
            turned = quarterTurn(turned);
        }
        return turns;
    }

    private static VoxelShape quarterTurn(VoxelShape up) {
        final VoxelShape[] result = {Shapes.empty()};
        up.forAllBoxes((x0, y0, z0, x1, y1, z1) ->
                result[0] = Shapes.or(result[0], Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0)));
        return result[0].optimize();
    }

    private static VoxelShape transform(VoxelShape up, Direction facing) {
        if (facing == Direction.UP) {
            return up;
        }
        final VoxelShape[] result = {Shapes.empty()};
        up.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            final double[] b = switch (facing) {
                case DOWN -> new double[] {x0, 1 - y1, 1 - z1, x1, 1 - y0, 1 - z0};

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
