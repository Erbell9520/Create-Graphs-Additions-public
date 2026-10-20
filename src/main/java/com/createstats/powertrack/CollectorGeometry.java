package com.createstats.powertrack;

import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.RailLayout;
import com.createstats.powertrack.core.RailProfile;
import com.createstats.powertrack.core.ShoeSlide;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

// trail away from the nearest bogie
public final class CollectorGeometry {
    private static final Map<Contraption, List<BlockPos>> BOGIES = Collections.synchronizedMap(new WeakHashMap<>());

    public static final double SHOE_REACH = CollectorPose.shoeOffsetBlocks(CollectorPose.angleOnRail(RailProfile.CAP_TOP));

    private CollectorGeometry() {
    }

    public static Direction.Axis beamAxis(BlockState state) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                ? state.getValue(BlockStateProperties.HORIZONTAL_AXIS) : Direction.Axis.Z;
    }

    public static Direction.Axis gearAxis(@Nullable Contraption contraption, BlockState state) {
        if (contraption instanceof CarriageContraption carriage && carriage.getAssemblyDirection() != null) {
            return carriage.getAssemblyDirection().getAxis();
        }
        return beamAxis(state);
    }

    public static Direction.Axis across(Direction.Axis axis) {
        return other(axis);
    }

    public static double centreOffset(@Nullable Contraption contraption, BlockPos local, Direction.Axis axis) {
        if (contraption == null) {
            return 0.0;
        }
        final List<BlockPos> bogies = BOGIES.computeIfAbsent(contraption, CollectorGeometry::scanBogies);
        if (bogies.isEmpty()) {
            return 0.0;
        }
        final Direction.Axis side = other(axis);
        BlockPos nearest = bogies.get(0);
        int best = Integer.MAX_VALUE;
        for (BlockPos b : bogies) {
            final int d = Math.abs(b.get(axis) - local.get(axis)) + Math.abs(b.get(side) - local.get(side));
            if (d < best) {
                best = d;
                nearest = b;
            }
        }
        return local.get(side) - nearest.get(side);
    }

    public static int trailSign(Contraption contraption, BlockPos local, Direction.Axis axis) {
        if (contraption == null) {
            return 1;
        }
        final List<BlockPos> bogies = BOGIES.computeIfAbsent(contraption, CollectorGeometry::scanBogies);
        if (bogies.isEmpty()) {
            return 1;
        }
        final int mine = local.get(axis);
        int best = Integer.MAX_VALUE;
        int nearest = 0;
        for (BlockPos b : bogies) {
            final int d = Math.abs(b.get(axis) - mine) + Math.abs(b.get(other(axis)) - local.get(other(axis)));
            if (d < best) {
                best = d;
                nearest = b.get(axis);
            }
        }
        return mine >= nearest ? 1 : -1;
    }

    private static Direction.Axis other(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    private static List<BlockPos> scanBogies(Contraption contraption) {
        final List<BlockPos> out = new ArrayList<>();
        for (StructureBlockInfo info : contraption.getBlocks().values()) {
            if (info.state().getBlock() instanceof AbstractBogeyBlock<?>) {
                out.add(info.pos());
            }
        }
        return out;
    }

    public static Vec3 trailLocal(Direction.Axis axis, int sign) {
        return axis == Direction.Axis.X ? new Vec3(sign, 0, 0) : new Vec3(0, 0, sign);
    }

    public static Vec3 shoeLocalOffset(Direction.Axis axis, int sign, double angleDeg) {
        final double along = sign * CollectorPose.shoeOffsetBlocks(angleDeg);
        return axis == Direction.Axis.X ? new Vec3(along, 0, 0) : new Vec3(0, 0, along);
    }

    public static Vec3 trailDirection(MovementContext context) {
        if (context.position == null || context.rotation == null) {
            return null;
        }
        final Direction.Axis axis = gearAxis(context.contraption, context.state);
        final int sign = trailSign(context.contraption, context.localPos, axis);
        return context.rotation.apply(trailLocal(axis, sign));
    }

    public static Vec3 sideDirection(MovementContext context) {
        if (context.position == null || context.rotation == null) {
            return null;
        }
        return context.rotation.apply(trailLocal(other(gearAxis(context.contraption, context.state)), 1));
    }

    public static Placement place(ShoeSlide.Locator rail, Vec3 centre, Vec3 trail, Vec3 side, double centreOffset,
                                  boolean onCar) {
        final double[] c = {centre.x, centre.y, centre.z};
        final double[] t = flat(trail);
        final double[] s = flat(side);
        final ShoeSlide.Result r = onCar ? ShoeSlide.onCar(rail, c, t, s, centreOffset, SHOE_REACH)
                : ShoeSlide.free(rail, c, t, s, SHOE_REACH);
        final Vec3 slid = centre.add(s[0] * r.slide(), 0, s[1] * r.slide());
        final double contact = CollectorPose.CONTACT_HALF_WIDTH_PX / 16.0;
        final CollectorPose.Rest rest = rest(slid, trail, centre.y - 0.5, p -> {
            final RailLayout.Hit hit = rail.nearest(p.x, p.y, p.z, contact, ShoeSlide.MIN_ABOVE, ShoeSlide.MAX_ABOVE);
            return hit != null && hit.contact() ? hit.railTopY() : Double.NaN;
        });
        return new Placement(r.slide(), slid, rest, r.hit());
    }

    public record Placement(double slide, Vec3 slid, CollectorPose.Rest rest, @Nullable RailLayout.Hit target) {
    }

    private static double[] flat(Vec3 v) {
        final double m = Math.hypot(v.x, v.z);
        return m < 1e-9 ? new double[]{0.0, 1.0} : new double[]{v.x / m, v.z / m};
    }

    public static Vec3 shoeAt(Vec3 centre, Vec3 trail, double angleDeg) {
        return centre.add(trail.scale(CollectorPose.shoeOffsetBlocks(angleDeg)));
    }

    public static CollectorPose.Rest rest(Vec3 centre, Vec3 trail, double floorY,
                                          java.util.function.ToDoubleFunction<Vec3> railTopY) {
        return CollectorPose.rest(offset -> {
            final double top = railTopY.applyAsDouble(centre.add(trail.scale(offset)));
            return Double.isNaN(top) ? Double.NaN : (top - floorY) * 16.0;
        });
    }
}
