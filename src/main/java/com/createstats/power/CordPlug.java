package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

public final class CordPlug {
    private CordPlug() {
    }

    private static final double BORE_HEIGHT = 8.0 / 16.0;

    public static final double PLATE_FACE = 2.0 / 16.0;

    public static final double PLUG_LENGTH = 2.5 / 16.0;

    public static final double RELIEF_LENGTH = 1.5 / 16.0;

    public static Direction outward(BlockState plate) {
        return plate.hasProperty(HorizontalDirectionalBlock.FACING)
                ? plate.getValue(HorizontalDirectionalBlock.FACING).getOpposite() : Direction.SOUTH;
    }

    public static Vec3 socketMouth(BlockState plate, BlockPos socket, double[] bores,
                                   @Nullable Vec3 towards) {
        return socketMouth(facingOf(plate), socket, bores, towards);
    }

    public static Vec3 socketMouth(Direction facing, BlockPos socket, double[] bores,
                                   @Nullable Vec3 towards) {
        double best = bores[0];
        if (towards != null && bores.length > 1) {
            double bestDist = Double.MAX_VALUE;
            for (double bore : bores) {
                final double d = onPlate(facing, socket, bore, BORE_HEIGHT, PLATE_FACE)
                        .distanceToSqr(towards);
                if (d < bestDist) {
                    bestDist = d;
                    best = bore;
                }
            }
        }
        return onPlate(facing, socket, best, BORE_HEIGHT, PLATE_FACE + PLUG_LENGTH);
    }

    public static Vec3 boreMouth(Direction facing, BlockPos socket, double bore) {
        return onPlate(facing, socket, bore, BORE_HEIGHT, PLATE_FACE);
    }

    public static Vec3 facePoint(BlockState plate, BlockPos pos, double u, double v, double depth) {
        return onPlate(facingOf(plate), pos, u, v, depth);
    }

    private static Direction facingOf(BlockState plate) {
        return plate.hasProperty(HorizontalDirectionalBlock.FACING)
                ? plate.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    private static Vec3 onPlate(Direction facing, BlockPos socket, double u, double v, double depth) {
        final double x = u;
        final double y = v;
        final double z = depth;
        final Vec3 local = switch (facing) {
            case EAST -> new Vec3(1.0 - z, y, x);
            case SOUTH -> new Vec3(1.0 - x, y, 1.0 - z);
            case WEST -> new Vec3(z, y, 1.0 - x);
            default -> new Vec3(x, y, z);
        };
        return new Vec3(socket.getX() + local.x, socket.getY() + local.y, socket.getZ() + local.z);
    }

    // ask the capability for contacts, plenty of machines only take power on one face
    @Nullable
    public static Direction machineFace(Level level, BlockPos machine, Vec3 from) {
        Direction best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, machine, face) == null) {
                continue;
            }
            if (!clear(level, machine.relative(face))) {
                continue;
            }

            if (level.getBlockEntity(machine) instanceof com.createstats.blockentity.UniversalPowerCouplerBE
                    coupler && coupler.foreignFace() == face) {
                continue;
            }
            final double d = terminal(machine, face).distanceToSqr(from);
            if (d < bestDist) {
                bestDist = d;
                best = face;
            }
        }
        return best;
    }

    public static Vec3 terminal(BlockPos machine, Direction face) {
        return Vec3.atCenterOf(machine)
                .add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5 + RELIEF_LENGTH));
    }

    public static Vec3 machineSurface(BlockPos machine, Direction face) {
        return Vec3.atCenterOf(machine).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
    }

    private static boolean clear(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos, CollisionContext.empty())
                .isEmpty();
    }
}
