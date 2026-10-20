package com.createstats.util;

import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.theme.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GaugeParticleHelper {
    private static final float PARTICLE_START_RPM = 128.0f;
    private static final float PARTICLE_BURST_RPM = 256.0f;
    private static final float RANDOM_OFFSET = 0.25f;
    private static final float SINGLE_PARTICLE_CHANCE = 0.25f;
    private static final double FACE_OFFSET = 0.55d;
    private static final double MOTION_SCALE = 0.30000001192092896d;
    private static final Vec3 UNIT = new Vec3(1.0d, 1.0d, 1.0d);

    private GaugeParticleHelper() {
    }

    public static void spawnRpmParticles(Level level, BlockPos pos, RandomSource random, Direction face, int color, float rpm) {
        if (rpm < PARTICLE_START_RPM || color == 0) {
            return;
        }

        int count = rpm >= PARTICLE_BURST_RPM ? 4 : 1;
        if (count == 1 && random.nextFloat() > SINGLE_PARTICLE_CHANCE) {
            return;
        }

        var colorVector = new Color(color).asVectorF();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Direction positiveAxis = Direction.get(Direction.AxisDirection.POSITIVE, face.getAxis());
        Vec3 planeMask = UNIT.subtract(Vec3.atLowerCornerOf(positiveAxis.getNormal()));
        Vec3 center = VecHelper.getCenterOf(pos).add(normal.scale(FACE_OFFSET));

        for (int i = 0; i < count; i++) {
            Vec3 spread = VecHelper.offsetRandomly(Vec3.ZERO, random, RANDOM_OFFSET).multiply(planeMask);
            if (spread.lengthSqr() < 1.0E-4d) {
                spread = fallbackSpread(face);
            }
            Vec3 motion = spread.normalize().scale(MOTION_SCALE);
            Vec3 particlePos = center.add(motion);
            level.addParticle(new DustParticleOptions(colorVector, 1.0f),
                    particlePos.x, particlePos.y, particlePos.z,
                    motion.x, motion.y, motion.z);
        }
    }

    private static Vec3 fallbackSpread(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(0.0d, 0.2d, 0.2d);
            case Y -> new Vec3(0.2d, 0.0d, 0.2d);
            case Z -> new Vec3(0.2d, 0.2d, 0.0d);
        };
    }
}
