package com.createstats.client.dynlight;

import com.createstats.dynlight.PointLight;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vehiclelight.HailSequence;
import com.createstats.vehiclelight.HeadlightState;
import com.createstats.vehiclelight.LampShow;
import com.createstats.vehiclelight.VehicleLightBE;
import com.createstats.vehiclelight.VehicleLightBeams;
import com.createstats.vehiclelight.VehicleLightBlock;
import com.createstats.vehiclelight.VehicleLightGlow;
import com.createstats.vehiclelight.VehicleLightKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

// ship block light only lights the plot, millions of blocks off. so we draw it where the ship is seen. sable tax #6
final class VehicleLightLights {
    private static final Set<VehicleLightBE> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    private VehicleLightLights() {
    }

    static void loaded(BlockEntity be) {
        if (be instanceof VehicleLightBE light) {
            ACTIVE.add(light);
        }
    }

    static void unloaded(BlockEntity be) {
        if (be instanceof VehicleLightBE light) {
            ACTIVE.remove(light);
        }
    }

    static void collect(ClientLevel level, LightSink sink) {
        final long gameTime = level.getGameTime();
        final Iterator<VehicleLightBE> it = ACTIVE.iterator();
        while (it.hasNext()) {
            final VehicleLightBE be = it.next();
            if (be.isRemoved() || (be.getLevel() instanceof ClientLevel other && other != level)) {
                it.remove();
                continue;
            }
            final BlockState state = be.getBlockState();
            if (!(state.getBlock() instanceof VehicleLightBlock block)) {
                continue;
            }
            final VehicleLightKind kind = block.kind();
            final VehicleLightGlow glow = state.getValue(VehicleLightBlock.GLOW);
            final Direction facing = state.getValue(VehicleLightBlock.FACING);
            final Object craft = SableBridge.subLevelAccessOf(be);
            final Quaterniond bodyToWorld = craft == null ? null : SableBridge.bodyOrientation(craft);
            final double[] lens = lens(be.getBlockPos(), facing, craft);
            final HeadlightState head = be.headlight();
            final boolean headlight = kind == VehicleLightKind.WHITE && Math.abs(facing.getStepY()) == 0;
            if (craft != null && lens != null) {
                head.travelled(lens[0], lens[1], lens[2]);
                head.heading(heading(facing, bodyToWorld));
                if (headlight && head.probeDue()) {
                    head.measured(groundBelow(level, lens[0], lens[1], lens[2]));
                }
                head.tick();
            }

            final HailSequence.Hail hail = be.hailing();
            final double t = hail == null ? Double.NaN : hail.seconds(gameTime, 0.0f);
            if (!Double.isNaN(t) && head.speed() > HailSequence.MAX_SPEED) {
                head.cut(hail.start());
            }
            final boolean cut = hail != null && head.isCut(hail.start());

            final VehicleLightGlow step = glow == VehicleLightGlow.BRIGHT ? VehicleLightGlow.BRIGHT : VehicleLightGlow.DIM;
            final double resting = VehicleLightBeams.restingTip(step);
            final double levelled = craft != null ? VehicleLightBeams.tipFor(head.height(), step) : resting;
            final HailSequence.Aim show = cut || Double.isNaN(t) ? HailSequence.Aim.LEVELLED : HailSequence.aim(t);
            final double bend = craft != null && headlight ? head.bend() : 0.0;
            final double[] angles = VehicleLightBeams.angles(resting, levelled, show, bend);
            head.aimed(angles[0], angles[1]);

            final LampShow.Lens shown = LampShow.lens(kind, glow, gameTime, 0.0f, hail, cut);
            if (!shown.lit() || lens == null) {
                continue;
            }
            if (kind != VehicleLightKind.WHITE && craft == null) {
                continue;
            }
            final double dx = lens[0] - sink.camera().x;
            final double dy = lens[1] - sink.camera().y;
            final double dz = lens[2] - sink.camera().z;
            if (dx * dx + dy * dy + dz * dz > sink.maxDistance() * sink.maxDistance()) {
                continue;
            }
            if (kind == VehicleLightKind.WHITE) {
                final double[] aim = VehicleLightBeams.aim(facing.getStepX(), facing.getStepY(), facing.getStepZ(),
                        angles[0], angles[1]);
                final Vector3d dir = new Vector3d(aim[0], aim[1], aim[2]);
                if (bodyToWorld != null) {
                    bodyToWorld.transform(dir);
                }
                if (dir.lengthSquared() > 1.0e-6) {
                    dir.normalize();
                }
                final VehicleLightGlow beam = shown.shown() == VehicleLightGlow.BRIGHT
                        ? VehicleLightGlow.BRIGHT : VehicleLightGlow.DIM;

                final boolean drawn = headlight && DrawnBeams.isActive();
                sink.add(VehicleLightBeams.headlight(new double[] {lens[0], lens[1], lens[2], dir.x, dir.y, dir.z},
                        beam, drawn ? VehicleLightBeams.UNDERLAY_DIM : 0));
                if (drawn) {
                    DrawnBeams.publish(be, craft);
                }
            } else {
                final int emission = shown.emission(kind);
                if (emission > 0) {
                    sink.add(new PointLight(lens[0], lens[1], lens[2], emission));
                }
            }
        }
    }

    private static double[] lens(BlockPos pos, Direction facing, Object craft) {
        final double lx = pos.getX() + 0.5 + facing.getStepX() * VehicleLightBeams.LENS_OFFSET;
        final double ly = pos.getY() + 0.5 + facing.getStepY() * VehicleLightBeams.LENS_OFFSET;
        final double lz = pos.getZ() + 0.5 + facing.getStepZ() * VehicleLightBeams.LENS_OFFSET;
        if (craft == null) {
            return new double[] {lx, ly, lz};
        }
        final Vector3d lens = SableBridge.localToWorld(craft, new Vector3d(lx, ly, lz));
        if (lens == null || !Double.isFinite(lens.x) || !Double.isFinite(lens.y) || !Double.isFinite(lens.z)) {
            return null;
        }
        return new double[] {lens.x, lens.y, lens.z};
    }

    private static double heading(Direction facing, Quaterniond bodyToWorld) {
        final Vector3d d = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        if (bodyToWorld != null) {
            bodyToWorld.transform(d);
        }
        if (Math.abs(d.y) > 0.7) {
            return Double.NaN;
        }
        return Math.atan2(d.x, d.z);
    }

    static double groundBelow(BlockGetter level, double x, double y, double z) {
        final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        final int bx = Mth.floor(x);
        final int bz = Mth.floor(z);
        for (int by = Mth.floor(y); by >= Mth.floor(y - HeadlightState.PROBE_DEPTH); by--) {
            cell.set(bx, by, bz);
            final BlockState s = level.getBlockState(cell);
            double top = Double.NEGATIVE_INFINITY;
            final VoxelShape shape = s.getCollisionShape(level, cell);
            if (!shape.isEmpty()) {
                top = by + shape.max(Direction.Axis.Y);
            }
            final FluidState fluid = s.getFluidState();
            if (!fluid.isEmpty()) {
                top = Math.max(top, by + fluid.getHeight(level, cell));
            }
            if (top > Double.NEGATIVE_INFINITY && top <= y + 1.0e-6) {
                return y - top;
            }
        }
        return Double.NaN;
    }
}
