package com.createstats.block;

import com.createstats.vacuum.VacuumFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

// not LampShapes, the sniffer turns like an end rod (up -> north). two conventions on one block = outline off from the head
public final class VacuumShapes {
    private VacuumShapes() {
    }

    public static Map<Direction, VoxelShape> horizontal(double[]... boxes) {
        final Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                final Vec3 a = VacuumFrame.horizontal(BlockPos.ZERO, facing, b[0], b[1], b[2]);
                final Vec3 c = VacuumFrame.horizontal(BlockPos.ZERO, facing, b[3], b[4], b[5]);
                shape = Shapes.or(shape, box(a, c));
            }
            out.put(facing, shape.optimize());
        }
        return out;
    }

    public static Map<Direction, VoxelShape> mounted(double[]... boxes) {
        final Map<Direction, VoxelShape> out = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : boxes) {
                final Vec3 a = VacuumFrame.mounted(BlockPos.ZERO, facing, b[0], b[1], b[2]);
                final Vec3 c = VacuumFrame.mounted(BlockPos.ZERO, facing, b[3], b[4], b[5]);
                shape = Shapes.or(shape, box(a, c));
            }
            out.put(facing, shape.optimize());
        }
        return out;
    }

    private static VoxelShape box(Vec3 a, Vec3 c) {
        return Shapes.box(Math.min(a.x, c.x), Math.min(a.y, c.y), Math.min(a.z, c.z),
                Math.max(a.x, c.x), Math.max(a.y, c.y), Math.max(a.z, c.z));
    }
}
