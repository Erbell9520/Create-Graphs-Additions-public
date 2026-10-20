package com.createstats.vcu;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vcu.core.V3;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// no cards. the FCC rack was eight steps per propeller and a 16-wheeler didn't fit
final class VcuHardware {
    static final int SCAN_CHUNK_RADIUS = 6;

    static final int WALK_LIMIT = 1024;

    record Prop(BlockPos pos, V3 centre, V3 axis, double strength, boolean gyro) {
    }

    record Wheel(BlockPos pos, V3 contact, Direction.Axis mountAxis) {
    }

    record Surface(BlockPos pos, V3 hingePoint, V3 hingeAxis, V3 bodyCentre, boolean bodyMeasured,
                   boolean assembled, boolean locked, boolean cogTurning) {
    }

    record Lift(BlockPos pos, boolean vent) {
    }

    record Scan(boolean onCraft, V3 pivot, boolean pivotMeasured, List<Prop> props, List<Wheel> wheels,
                List<Surface> surfaces, List<Lift> lifts, Map<BlockPos, BlockPos> governorOf,
                List<String> problems) {
        static final Scan EMPTY = new Scan(false, V3.ZERO, false, List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of());

        @Nullable
        BlockPos governorFor(BlockPos part) {
            return governorOf.get(part);
        }

        Set<BlockPos> usedGovernors() {
            return new LinkedHashSet<>(governorOf.values());
        }

        boolean isEmpty() {
            return props.isEmpty() && wheels.isEmpty() && surfaces.isEmpty() && lifts.isEmpty();
        }
    }

    private VcuHardware() {
    }

    static Scan scan(ServerLevel level, BlockEntity controller) {
        Object craft = SableBridge.subLevelAccessOf(controller);
        if (craft == null) {
            return new Scan(false, V3.ZERO, false, List.of(), List.of(), List.of(), List.of(), Map.of(),
                    List.of("Not on a craft. Build it into a physics craft (Physics Assembler) with the props, wheels or swivel bearings it should fly."));
        }
        UUID craftId = SableBridge.subLevelId(craft);

        List<Prop> props = new ArrayList<>();
        List<Wheel> wheels = new ArrayList<>();
        List<Surface> surfaces = new ArrayList<>();
        List<Lift> lifts = new ArrayList<>();
        List<RpmGovernorBE> governors = new ArrayList<>();
        List<String> problems = new ArrayList<>();

        BlockPos origin = controller.getBlockPos();
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dz = -SCAN_CHUNK_RADIUS; dz <= SCAN_CHUNK_RADIUS; dz++) {
            for (int dx = -SCAN_CHUNK_RADIUS; dx <= SCAN_CHUNK_RADIUS; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) continue;
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (be == controller || be.isRemoved()) continue;
                    classify(level, be, craft, craftId, props, wheels, surfaces, lifts, governors);
                }
            }
        }

        Set<BlockPos> drivable = new HashSet<>();
        props.forEach(p -> drivable.add(p.pos()));
        wheels.forEach(w -> drivable.add(w.pos()));
        Map<BlockPos, BlockPos> governorOf = pairGovernors(level, governors, drivable);

        Vector3d com = SableBridge.centerOfMassLocal(craft);
        boolean pivotMeasured = com != null && Double.isFinite(com.x) && Double.isFinite(com.y) && Double.isFinite(com.z);
        V3 pivot = pivotMeasured ? new V3(com.x, com.y, com.z) : centroid(props, wheels, surfaces, origin);

        long unthrottledProps = props.stream().filter(p -> !governorOf.containsKey(p.pos())).count();
        long undrivenWheels = wheels.stream().filter(w -> !governorOf.containsKey(w.pos())).count();
        if (props.isEmpty() && wheels.isEmpty() && surfaces.isEmpty() && lifts.isEmpty()) {
            problems.add("Found nothing to fly on this craft: no props, wheel mounts, swivel bearings, burners or vents.");
        }
        if (unthrottledProps > 0) {
            problems.add(unthrottledProps + " prop(s) have no RPM Governor in their shaft line, so they spin at engine speed and cannot be throttled.");
        }
        if (!wheels.isEmpty() && undrivenWheels == wheels.size()) {
            problems.add("No wheel has an RPM Governor in its shaft line: the VCU can steer them but not drive them.");
        } else if (undrivenWheels > 0) {
            problems.add(undrivenWheels + " wheel(s) have no RPM Governor, so they steer but roll free.");
        }
        for (Surface s : surfaces) {
            String at = s.pos().toShortString();
            if (!s.assembled()) problems.add("Swivel bearing at " + at + " is not assembled, so it moves nothing.");
            else if (!s.locked()) problems.add("Swivel bearing at " + at + " is unlocked (redstone or its value panel), so it cannot hold an angle.");
            if (s.cogTurning()) problems.add("Swivel bearing at " + at + " has a turning cog, which fights the VCU. Leave its cog unpowered.");
        }
        if (!pivotMeasured) {
            problems.add("Sable reported no centre of mass; mixing about the middle of the parts instead.");
        }
        return new Scan(true, pivot, pivotMeasured, props, wheels, surfaces, lifts, governorOf, problems);
    }

    private static void classify(ServerLevel level, BlockEntity be, Object craft, @Nullable UUID craftId,
                                 List<Prop> props, List<Wheel> wheels, List<Surface> surfaces, List<Lift> lifts,
                                 List<RpmGovernorBE> governors) {
        boolean governor = be instanceof RpmGovernorBE;
        boolean wheel = !governor && VcuActuators.is(VcuActuators.WHEEL_MOUNT, be);
        boolean burner = !governor && !wheel && VcuActuators.is(VcuActuators.BURNER, be);
        boolean vent = !governor && !wheel && !burner && VcuActuators.is(VcuActuators.VENT, be);
        boolean swivel = !governor && !wheel && !burner && !vent && SableBridge.matchesSwivelBearing(be);
        SableBridge.PropData bearing = governor || wheel || burner || vent || swivel ? null : SableBridge.readProp(be);
        boolean looseProp = bearing == null && !governor && !wheel && !burner && !vent && !swivel
                && SableBridge.matchesAnyPropeller(be);
        if (!(governor || wheel || burner || vent || swivel || bearing != null || looseProp)) return;
        if (!sameCraft(be, craft, craftId)) return;

        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        if (governor) {
            governors.add((RpmGovernorBE) be);
        } else if (wheel) {
            Direction facing = horizontalFacing(state);
            if (facing == null) return;
            wheels.add(new Wheel(pos, centre(pos.relative(facing)), facing.getAxis()));
        } else if (burner || vent) {
            lifts.add(new Lift(pos, vent));
        } else if (swivel) {
            surfaces.add(surface(level, be, craft));
        } else if (bearing != null) {
            Direction facing = facing(state);
            V3 axis = facing != null ? unit(facing) : V3.UNIT_Y;
            V3 published = new V3(bearing.thrustDirLocal().x, bearing.thrustDirLocal().y, bearing.thrustDirLocal().z);
            if (published.length() > 0.5) axis = published.normalized();

            V3 at = centre(pos).add(facing != null ? unit(facing) : V3.ZERO);
            props.add(new Prop(pos, at, axis, Math.max(1.0, bearing.sailPower()), bearing.isGyro()));
        } else {
            Direction facing = SableBridge.propellerBlockDirection(be);
            if (facing == null) facing = facing(state);
            props.add(new Prop(pos, centre(pos), facing != null ? unit(facing) : V3.UNIT_Y, 1.0, false));
        }
    }

    private static Surface surface(ServerLevel level, BlockEntity be, Object craft) {
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        Direction facing = facing(state);
        V3 axis = facing != null ? unit(facing) : V3.UNIT_Y;
        V3 hinge = centre(pos).add(axis.scale(0.5));
        V3 body = hinge.add(axis.scale(0.5));
        boolean measured = false;
        UUID attached = VcuActuators.swivelSubLevelId(be);
        if (attached != null) {
            Object sub = SableBridge.findSubLevelById(level, attached);
            Vector3d world = sub == null ? null : SableBridge.subLevelWorldCenter(sub);
            Vector3d local = world == null ? null : SableBridge.worldToLocal(craft, world);
            if (local != null && Double.isFinite(local.x) && Double.isFinite(local.y) && Double.isFinite(local.z)) {
                body = new V3(local.x, local.y, local.z);
                measured = true;
            }
        }
        boolean locked = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
        Double cog = SableBridge.swivelKineticSpeed(be);
        return new Surface(pos, hinge, axis, body, measured, SableBridge.isSwivelAssembled(be), locked,
                cog != null && Math.abs(cog) > 0.01);
    }

    private static Map<BlockPos, BlockPos> pairGovernors(ServerLevel level, List<RpmGovernorBE> governors,
                                                         Set<BlockPos> drivable) {
        Map<BlockPos, BlockPos> out = new HashMap<>();
        for (RpmGovernorBE g : governors) {
            Direction facing = facing(g.getBlockState());
            if (facing == null) continue;
            Direction input = null;
            if (g.hasSource() && g.source != null) {
                BlockPos d = g.source.subtract(g.getBlockPos());
                input = Direction.getNearest(d.getX(), d.getY(), d.getZ());
            }
            if (input == null) input = VcuActuators.governorLastInput(g);

            Set<BlockPos> sideA = new HashSet<>();
            Set<BlockPos> sideB = new HashSet<>();
            boolean engineA = walk(level, g, facing, sideA);
            boolean engineB = walk(level, g, facing.getOpposite(), sideB);
            Set<BlockPos> downstream;
            if (input == facing) downstream = sideB;
            else if (input == facing.getOpposite()) downstream = sideA;
            else if (engineA && !engineB) downstream = sideB;
            else if (engineB && !engineA) downstream = sideA;
            else downstream = sideA;

            for (BlockPos reached : downstream) {
                if (drivable.contains(reached)) out.putIfAbsent(reached, g.getBlockPos());
            }
        }
        return out;
    }

    private static boolean walk(ServerLevel level, RpmGovernorBE governor, Direction side, Set<BlockPos> reached) {
        BlockPos start = governor.getBlockPos().relative(side);
        if (!(SableBridge.getBlockEntityOnContraption(level, start) instanceof KineticBlockEntity first)) return false;
        boolean engine = false;
        Set<BlockPos> seen = new HashSet<>();
        seen.add(governor.getBlockPos());
        seen.add(start);
        ArrayDeque<KineticBlockEntity> queue = new ArrayDeque<>();
        queue.add(first);
        while (!queue.isEmpty() && seen.size() < WALK_LIMIT) {
            KineticBlockEntity cur = queue.poll();
            reached.add(cur.getBlockPos());
            if (cur instanceof GeneratingKineticBlockEntity) engine = true;

            if (cur instanceof RpmGovernorBE) continue;
            for (BlockPos np : VcuActuators.potentialNeighbours(cur)) {
                if (seen.contains(np)) continue;
                if (!(SableBridge.getBlockEntityOnContraption(level, np) instanceof KineticBlockEntity next)) continue;
                if (!(RotationPropagator.isConnected(cur, next) || RotationPropagator.isConnected(next, cur))) continue;
                seen.add(np);
                queue.add(next);
            }
        }
        return engine;
    }

    private static boolean sameCraft(BlockEntity be, Object craft, @Nullable UUID craftId) {
        Object other = SableBridge.subLevelAccessOf(be);
        if (other == null) return false;
        if (other == craft || other.equals(craft)) return true;
        UUID otherId = SableBridge.subLevelId(other);
        return craftId != null && craftId.equals(otherId);
    }

    @Nullable
    static Direction facing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) return state.getValue(BlockStateProperties.FACING);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return horizontalFacing(state);
    }

    @Nullable
    static Direction horizontalFacing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (Property<?> p : state.getProperties()) {
            if (p instanceof DirectionProperty dp) {
                Direction d = state.getValue(dp);
                if (d.getAxis().isHorizontal()) return d;
            }
        }
        return null;
    }

    static V3 centre(BlockPos pos) {
        return new V3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    static V3 unit(Direction d) {
        return new V3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    private static V3 centroid(List<Prop> props, List<Wheel> wheels, List<Surface> surfaces, BlockPos fallback) {
        V3 sum = V3.ZERO;
        int n = 0;
        for (Prop p : props) { sum = sum.add(p.centre()); n++; }
        for (Wheel w : wheels) { sum = sum.add(w.contact()); n++; }
        for (Surface s : surfaces) { sum = sum.add(s.bodyCentre()); n++; }
        return n == 0 ? centre(fallback) : sum.scale(1.0 / n);
    }
}
