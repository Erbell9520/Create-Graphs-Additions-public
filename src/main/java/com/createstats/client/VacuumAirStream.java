package com.createstats.client;

import com.createstats.blockentity.VacuumCollectorBE;
import com.createstats.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// more while pulling, wider with reach. one glance says running and how far
public final class VacuumAirStream {
    private VacuumAirStream() {
    }

    public static void collector(VacuumCollectorBE be) {
        final Level level = be.getLevel();
        if (level == null) {
            return;
        }
        final boolean pulling = be.state() == VacuumCollectorBE.State.PULL;
        final RandomSource r = level.random;
        final int count = pulling ? 3 : (r.nextInt(3) == 0 ? 1 : 0);
        if (count == 0) {
            return;
        }
        final Direction f = be.facing();
        final Vec3 mouth = be.mouth();
        final double reach = Math.min(4.0, 0.8 + be.radius() * 0.45);
        for (int i = 0; i < count; i++) {
            final double out = 0.6 + r.nextDouble() * reach;
            final double side = (r.nextDouble() - 0.5) * reach * 1.4;
            final double up = (r.nextDouble() - 0.35) * reach * 0.8;
            final Vec3 across = new Vec3(-f.getStepZ(), 0, f.getStepX());
            final Vec3 from = mouth.add(Vec3.atLowerCornerOf(f.getNormal()).scale(out)).add(across.scale(side))
                    .add(0, up, 0);
            level.addParticle(ModParticles.VACUUM_AIR.get(), from.x, from.y, from.z, mouth.x, mouth.y, mouth.z);
        }
    }

    public static void grit(Level level, BlockPos pos, BlockState state, Direction face, Vec3 mouth, int count) {
        if (!(level instanceof ClientLevel client)) {
            return;
        }
        final RandomSource r = level.random;
        final VoxelShape shape = state.getShape(level, pos);
        final AABB box = shape.isEmpty() ? new AABB(0.25, 0, 0.25, 0.75, 0.5, 0.75) : shape.bounds();
        for (int i = 0; i < count; i++) {
            double x = box.minX + r.nextDouble() * (box.maxX - box.minX);
            double y = box.minY + r.nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + r.nextDouble() * (box.maxZ - box.minZ);

            switch (face) {
                case UP -> y = box.maxY + 0.02;
                case DOWN -> y = box.minY - 0.02;
                case NORTH -> z = box.minZ - 0.02;
                case SOUTH -> z = box.maxZ + 0.02;
                case WEST -> x = box.minX - 0.02;
                case EAST -> x = box.maxX + 0.02;
            }
            Minecraft.getInstance().particleEngine.add(new VacuumGritParticle(client, pos.getX() + x, pos.getY() + y,
                    pos.getZ() + z, state, pos, mouth));
        }
    }

    public static void cone(Level level, Vec3 mouth, Vec3 outward, double length, double spread, int count) {
        final RandomSource r = level.random;
        final Vec3 dir = outward.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, -1) : outward.normalize();
        Vec3 u = dir.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1.0e-6) {
            u = dir.cross(new Vec3(1, 0, 0));
        }
        u = u.normalize();
        final Vec3 v = dir.cross(u).normalize();
        for (int i = 0; i < count; i++) {
            final double t = 0.3 + r.nextDouble() * length;
            final double a = r.nextDouble() * Math.PI * 2.0;
            final double rad = spread * (t / length) * r.nextDouble();
            final Vec3 from = mouth.add(dir.scale(t)).add(u.scale(Math.cos(a) * rad)).add(v.scale(Math.sin(a) * rad));
            level.addParticle(ModParticles.VACUUM_AIR.get(), from.x, from.y, from.z, mouth.x, mouth.y, mouth.z);
        }
    }
}
