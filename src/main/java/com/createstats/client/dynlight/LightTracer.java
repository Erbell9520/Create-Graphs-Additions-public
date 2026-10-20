package com.createstats.client.dynlight;

import com.createstats.dynlight.BeamLight;
import com.createstats.dynlight.BeamShape;
import com.createstats.dynlight.EmissionPlan;
import com.createstats.dynlight.Light;
import com.createstats.dynlight.PointLight;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

final class LightTracer {
    private LightTracer() {
    }

    static void resolve(BlockGetter level, Light light, Long2ByteMap into) {
        switch (light) {
            case PointLight point -> EmissionPlan.mergeMax(into,
                    BlockPos.asLong(Mth.floor(point.x()), Mth.floor(point.y()), Mth.floor(point.z())), point.level());
            case BeamLight beam -> beam(level, beam, into);
        }
    }

    private static void beam(BlockGetter level, BeamLight beam, Long2ByteMap into) {
        final long lens = BlockPos.asLong(Mth.floor(beam.x()), Mth.floor(beam.y()), Mth.floor(beam.z()));
        EmissionPlan.mergeMax(into, lens, beam.spill());
        if (beam.level() <= 0 || beam.reach() <= 0.0) {
            return;
        }
        final Vec3 from = new Vec3(beam.x(), beam.y(), beam.z());
        final Landing centre = land(level, from, beam.dx(), beam.dy(), beam.dz(), beam, lens, 0, into);
        final double distance = centre == null ? beam.reach() : centre.hit().getLocation().distanceTo(from);
        final double radius = beam.poolRadiusAt(distance);
        final int rays = BeamShape.ringRays(radius);
        if (rays > 0) {
            final double half = BeamShape.ringHalfAngle(radius, distance);
            for (double[] ray : BeamShape.ring(beam.dx(), beam.dy(), beam.dz(), half, rays)) {
                land(level, from, ray[0], ray[1], ray[2], beam, lens, 1, into);
            }
        }
        if (centre != null) {
            final double[] n = surfaceNormal(centre);
            for (double[] p : BeamShape.spine(from.x, from.y, from.z, beam.dx(), beam.dy(), beam.dz(), distance,
                    n[0], n[1], n[2], radius, beam.reach(), BeamShape.SPINE_SPACING)) {
                land(level, from, p[0] - from.x, p[1] - from.y, p[2] - from.z, beam, lens, 1, into);
            }
        }
    }

    private static double[] surfaceNormal(Landing landing) {
        final BlockPos hit = landing.hit().getBlockPos();
        final int nx = BlockPos.getX(landing.openCell()) - hit.getX();
        final int ny = BlockPos.getY(landing.openCell()) - hit.getY();
        final int nz = BlockPos.getZ(landing.openCell()) - hit.getZ();
        if (Math.abs(nx) + Math.abs(ny) + Math.abs(nz) == 1) {
            return new double[] {nx, ny, nz};
        }
        final Direction face = landing.hit().getDirection();
        return new double[] {face.getStepX(), face.getStepY(), face.getStepZ()};
    }

    @Nullable
    private static Landing land(BlockGetter level, Vec3 from, double dx, double dy, double dz, BeamLight beam,
                                long lens, int dim, Long2ByteMap into) {
        final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(len > 1.0e-9)) {
            return null;
        }
        final double scale = beam.reach() / len;
        final Vec3 to = from.add(dx * scale, dy * scale, dz * scale);
        final Landing landing = trace(level, from, to, lens);
        if (landing == null) {
            return null;
        }
        final double distance = landing.hit().getLocation().distanceTo(from);
        final int lit = beam.levelAt(distance) - dim;
        if (lit > 0) {
            EmissionPlan.mergeMax(into, landing.openCell(), lit);
        }
        return landing;
    }

    record Landing(BlockHitResult hit, long openCell) {
    }

    @Nullable
    static Landing trace(BlockGetter level, Vec3 from, Vec3 to, long lens) {
        final long[] lastOpen = {lens};
        final BlockHitResult hit = BlockGetter.traverseBlocks(from, to, level, (getter, pos) -> {
            final long key = pos.asLong();
            if (key == lens) {
                return null;
            }
            final BlockState state = getter.getBlockState(pos);
            if (state.isAir() || (!state.canOcclude() && state.getLightBlock(getter, pos) == 0)) {
                lastOpen[0] = key;
                return null;
            }
            final VoxelShape shape = state.getShape(getter, pos);
            final BlockHitResult h = shape.isEmpty() ? null : shape.clip(from, to, pos.immutable());
            if (h == null || h.isInside()) {
                lastOpen[0] = key;
                return null;
            }
            return h;
        }, getter -> null);
        return hit == null ? null : new Landing(hit, lastOpen[0]);
    }
}
