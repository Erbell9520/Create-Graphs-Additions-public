package com.createstats.vacuum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class VacuumFrame {
    private VacuumFrame() {
    }

    public static float yRotation(Direction facing) {
        return switch (facing) {
            case EAST -> -90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            default -> 0.0f;
        };
    }

    public static Vec3 horizontal(BlockPos pos, Direction facing, double px, double py, double pz) {
        final double x = px / 16.0;
        final double y = py / 16.0;
        final double z = pz / 16.0;
        final Vec3 local = switch (facing) {
            case EAST -> new Vec3(1.0 - z, y, x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
        return new Vec3(pos.getX() + local.x, pos.getY() + local.y, pos.getZ() + local.z);
    }

    public static Vec3 toHorizontal(Direction facing, Vec3 local) {
        return switch (facing) {
            case EAST -> new Vec3(local.z, local.y, 1.0 - local.x);
            case SOUTH -> new Vec3(1.0 - local.x, local.y, 1.0 - local.z);
            case WEST -> new Vec3(1.0 - local.z, local.y, local.x);
            default -> local;
        };
    }

    public static Vec3 horizontalDir(Direction facing, double dx, double dy, double dz) {
        return switch (facing) {
            case EAST -> new Vec3(-dz, dy, dx);
            case SOUTH -> new Vec3(-dx, dy, -dz);
            case WEST -> new Vec3(dz, dy, -dx);
            default -> new Vec3(dx, dy, dz);
        };
    }

    public static Vec3 mounted(BlockPos pos, Direction facing, double px, double py, double pz) {
        final Vec3 d = mountedDir(facing, px / 16.0 - 0.5, py / 16.0 - 0.5, pz / 16.0 - 0.5);
        return new Vec3(pos.getX() + 0.5 + d.x, pos.getY() + 0.5 + d.y, pos.getZ() + 0.5 + d.z);
    }

    public static Vec3 mountedDir(Direction facing, double x, double y, double z) {
        return switch (facing) {
            case DOWN -> new Vec3(x, -y, -z);
            case NORTH -> new Vec3(x, z, -y);
            case SOUTH -> new Vec3(-x, z, y);
            case EAST -> new Vec3(y, z, x);
            case WEST -> new Vec3(-y, z, -x);
            default -> new Vec3(x, y, z);
        };
    }

    public static Vec3 toMounted(Direction facing, Vec3 w) {
        return switch (facing) {
            case DOWN -> new Vec3(w.x, -w.y, -w.z);
            case NORTH -> new Vec3(w.x, -w.z, w.y);
            case SOUTH -> new Vec3(-w.x, w.z, w.y);
            case EAST -> new Vec3(w.z, w.x, w.y);
            case WEST -> new Vec3(-w.z, -w.x, w.y);
            default -> w;
        };
    }
}
