package com.createstats.vccmk4;

import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.MachineAxis;
import com.createstats.vehiclelight.VehicleLightBE;
import com.createstats.vehiclelight.VehicleLightBlock;
import com.createstats.vehiclelight.VehicleLightKind;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.chainDrive.ChainGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// the VCU's auto detection, the one part worth keeping
public final class Vcc4Scanner {
    static final int SCAN_CHUNK_RADIUS = 6;

    static final int BODY_CHUNK_RADIUS = 4;

    static final int WALK_LIMIT = 1024;

    static final int MAX_BODIES = 6;

    static final int MAX_MACHINE_NAMES = 3;

    public record Body(int index, @Nullable UUID id, @Nullable Object handle, BlockPos anchor, int parent,
                @Nullable BlockPos joint, LinkKind link, @Nullable TrailerLinks.Type linkType, boolean foreign,
                @Nullable BlockPos console, boolean towed, boolean load) {
        Body with(@Nullable Object handle, boolean foreign, @Nullable BlockPos console, boolean towed, boolean load) {
            return new Body(index, id, handle, anchor, parent, joint, link, linkType, foreign, console, towed, load);
        }

        Body linkedBy(Tie tie) {
            return new Body(index, id, handle, tie.endOn(index), parent, tie.endOn(parent), tie.kind(), tie.type(), foreign,
                    console, towed, load);
        }
    }

    public record Tie(LinkKind kind, TrailerLinks.Type type, int a, BlockPos posA, int b, BlockPos posB) {
        boolean joins(int x, int y) {
            return (a == x && b == y) || (a == y && b == x);
        }

        BlockPos endOn(int body) {
            return a == body ? posA : posB;
        }
    }

    public record Wheel(BlockPos pos, Vector3d contact, Direction.Axis mountAxis, int body) {
        Vector3d push() {
            return mountAxis == Direction.Axis.X ? new Vector3d(0, 0, 1) : new Vector3d(-1, 0, 0);
        }
    }

    public record Prop(BlockPos pos, Vector3d centre, Vector3d axis, double strength, int body, JetKind kind) {
        Prop(BlockPos pos, Vector3d centre, Vector3d axis, double strength, int body) {
            this(pos, centre, axis, strength, body, JetKind.PROPELLER);
        }

        public boolean direct() {
            return kind.direct();
        }
    }

    public record Swivel(BlockPos pos, Vector3d hingeAxis, boolean assembled, boolean locked, boolean cogTurning, int body,
                  int carries) {
    }

    public record Machine(BlockPos governor, List<String> drives, boolean runner, int body, Set<BlockPos> swivels) {
    }

    public record Link(BlockPos pos, @Nullable Couple<RedstoneLinkNetworkHandler.Frequency> key, String first, String second,
                int body, boolean coupler, @Nullable VehicleLightKind lamp, @Nullable Direction facing) {
        Link(BlockPos pos, Couple<RedstoneLinkNetworkHandler.Frequency> key, String first, String second, int body,
             boolean coupler) {
            this(pos, key, first, second, body, coupler, null, null);
        }

        boolean isLamp() {
            return lamp != null;
        }
    }

    public record Scan(boolean onCraft, List<Body> bodies, List<Tie> ties, List<Wheel> wheels, List<Prop> props,
                List<Swivel> swivels, List<Machine> machines, List<Link> links, Map<BlockPos, BlockPos> governorOf,
                List<BlockPos> otherConsoles, List<String> problems) {
        public static final Scan EMPTY = new Scan(false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(), List.of(), List.of());

        @Nullable
        public BlockPos governorFor(BlockPos part) {
            return governorOf.get(part);
        }

        public Set<BlockPos> usedGovernors() {
            return new LinkedHashSet<>(governorOf.values());
        }

        boolean isEmpty() {
            return wheels.isEmpty() && props.isEmpty() && swivels.isEmpty() && machines.isEmpty() && links.isEmpty();
        }
    }

    private Vcc4Scanner() {
    }

    private static final class Found {
        final List<Wheel> wheels = new ArrayList<>();
        final List<Prop> props = new ArrayList<>();
        final List<Swivel> swivels = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        final List<RpmGovernorBE> governors = new ArrayList<>();
        final Map<BlockPos, Integer> governorBody = new HashMap<>();
        final Map<BlockPos, UUID> swivelCarries = new HashMap<>();
        final List<BlockPos> otherConsoles = new ArrayList<>();

        void take(Found other, boolean driveOnly) {
            wheels.addAll(other.wheels);
            props.addAll(other.props);
            governors.addAll(other.governors);
            governorBody.putAll(other.governorBody);
            if (driveOnly) return;
            swivels.addAll(other.swivels);
            links.addAll(other.links);
            swivelCarries.putAll(other.swivelCarries);
        }
    }

    private static final class Graph {
        final List<Body> bodies = new ArrayList<>();
        final Map<UUID, Integer> indexOf = new HashMap<>();
        final List<Tie> ties = new ArrayList<>();
        final Set<Object> seen = new HashSet<>();

        void tie(TrailerLinks.Type type, TrailerLinks.Tie found, int a, @Nullable UUID aId, BlockPos posA) {
            UUID other = found.farBody();
            BlockPos posB = found.farBlock();
            if (other != null && other.equals(aId)) return;
            if (!seen.add(found.key())) return;
            int b = -1;
            if (other != null) {
                Integer known = indexOf.get(other);
                if (known != null) {
                    b = known;
                } else {
                    if (bodies.size() >= MAX_BODIES) return;
                    b = bodies.size();
                    indexOf.put(other, b);
                    bodies.add(new Body(b, other, null, posB, a, posA, found.kind(), type, false, null, false, false));
                }
            }
            ties.add(new Tie(found.kind(), type, a, posA, b, posB));
        }

        LinkKind stiffest(int x, int y, LinkKind fallback) {
            LinkKind best = null;
            for (Tie t : ties) if (t.joins(x, y) && t.kind().stifferThan(best)) best = t.kind();
            return best == null ? fallback : best;
        }
    }

    public static Scan scan(ServerLevel level, BlockEntity controller) {
        Object craft = SableBridge.subLevelAccessOf(controller);
        if (craft == null) {
            return new Scan(false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(),
                    List.of(), List.of("Not on a craft. Build it into a physics craft (Physics Assembler) with the wheel mounts it should drive."));
        }
        UUID craftId = SableBridge.subLevelId(craft);
        Graph graph = new Graph();
        graph.bodies.add(new Body(0, craftId, craft, controller.getBlockPos(), -1, null, LinkKind.JOINT, null, false, null, false, false));
        if (craftId != null) graph.indexOf.put(craftId, 0);
        Found found = new Found();

        for (int b = 0; b < graph.bodies.size(); b++) {
            Body body = graph.bodies.get(b);
            List<BlockEntity> bes = blockEntitiesOn(level, body, b == 0 ? craft : null, b == 0 ? SCAN_CHUNK_RADIUS : BODY_CHUNK_RADIUS, controller);
            Found here = b == 0 ? found : new Found();
            BlockPos console = null;
            for (BlockEntity be : bes) {
                if (be instanceof Vcc4BE) {
                    if (b == 0) found.otherConsoles.add(be.getBlockPos());
                    else if (console == null) console = be.getBlockPos();
                    continue;
                }
                classify(level, be, b, here);
            }
            if (b > 0) {
                boolean foreign = console != null;
                boolean towed = foreign || (body.parent() >= 0 && graph.bodies.get(body.parent()).towed());

                LinkKind link = graph.stiffest(b, body.parent(), body.link());
                boolean load = here.wheels.isEmpty() && !link.holds();
                Object handle = bes.isEmpty() ? null : SableBridge.subLevelAccessOf(bes.get(0));
                body = body.with(handle, foreign, console, towed, load);
                graph.bodies.set(b, body);

                if (load) continue;
                found.take(here, towed);
            }
            for (BlockEntity be : bes) {
                if (!(be instanceof Vcc4BE)) ties(level, be, body, graph, found);
            }
        }
        List<Body> bodies = effectiveLinks(graph);
        Set<Integer> towedBodies = new HashSet<>();
        for (Body body : bodies) if (body.towed()) towedBodies.add(body.index());

        found.wheels.sort((a, c) -> a.body() != c.body() ? Integer.compare(a.body(), c.body()) : a.pos().compareTo(c.pos()));
        found.props.sort((a, c) -> a.body() != c.body() ? Integer.compare(a.body(), c.body()) : a.pos().compareTo(c.pos()));
        found.links.sort((a, c) -> a.body() != c.body() ? Integer.compare(a.body(), c.body()) : a.pos().compareTo(c.pos()));
        found.governors.sort((a, c) -> {
            int ba = found.governorBody.getOrDefault(a.getBlockPos(), 0);
            int bc = found.governorBody.getOrDefault(c.getBlockPos(), 0);
            return ba != bc ? Integer.compare(ba, bc) : a.getBlockPos().compareTo(c.getBlockPos());
        });
        List<Swivel> swivels = new ArrayList<>();
        found.swivels.sort((a, c) -> a.body() != c.body() ? Integer.compare(a.body(), c.body()) : a.pos().compareTo(c.pos()));
        for (Swivel s : found.swivels) {
            UUID carried = found.swivelCarries.get(s.pos());
            int carries = -1;
            for (Body body : bodies) {
                if (carried != null && carried.equals(body.id())) carries = body.index();
            }
            swivels.add(new Swivel(s.pos(), s.hingeAxis(), s.assembled(), s.locked(), s.cogTurning(), s.body(), carries));
        }

        Set<BlockPos> drivable = new HashSet<>();
        found.wheels.forEach(w -> drivable.add(w.pos()));
        found.props.forEach(p -> drivable.add(p.pos()));
        Map<BlockPos, Set<BlockPos>> downstream = new LinkedHashMap<>();
        Map<BlockPos, BlockPos> governorOf = pairGovernors(level, found.governors, drivable, downstream);
        Set<BlockPos> used = new HashSet<>(governorOf.values());
        Set<BlockPos> swivelSet = new HashSet<>();
        found.swivels.forEach(s -> swivelSet.add(s.pos()));
        List<Machine> machines = new ArrayList<>();
        for (RpmGovernorBE g : found.governors) {
            if (used.contains(g.getBlockPos())) continue;

            if (towedBodies.contains(found.governorBody.getOrDefault(g.getBlockPos(), 0))) continue;
            Set<BlockPos> reached = downstream.getOrDefault(g.getBlockPos(), Set.of());
            List<String> names = machineNames(level, reached);
            if (names.isEmpty()) continue;
            Set<BlockPos> turned = new HashSet<>();
            for (BlockPos p : reached) if (swivelSet.contains(p)) turned.add(p);
            machines.add(new Machine(g.getBlockPos(), names, MachineAxis.runnerByDefault(names),
                    found.governorBody.getOrDefault(g.getBlockPos(), 0), Set.copyOf(turned)));
        }

        List<String> problems = new ArrayList<>();
        List<Wheel> wheels = found.wheels;
        List<Prop> props = found.props;
        if (wheels.isEmpty() && props.isEmpty()) {
            problems.add("No wheel mounts, propellers or thrusters found on this craft. This console drives Offroad wheel mounts "
                    + "with tyres, propellers and Create Propulsion thrusters.");
        }
        long undriven = wheels.stream().filter(w -> !governorOf.containsKey(w.pos())).count();
        if (!wheels.isEmpty() && undriven == wheels.size() && props.isEmpty()) {
            problems.add("No wheel has an RPM Governor in its shaft line: the wheels steer but nothing drives them.");
        } else if (undriven > 0 && undriven < wheels.size()) {
            problems.add(undriven + " wheel(s) have no RPM Governor in their shaft line, so they steer but roll free.");
        }

        List<String> loose = new ArrayList<>();

        for (int i = 0; i < props.size(); i++) {
            if (!props.get(i).direct() && !governorOf.containsKey(props.get(i).pos())) loose.add("J" + (i + 1));
        }
        if (!loose.isEmpty()) {
            problems.add(String.join(" ", loose) + (loose.size() == 1 ? " has" : " have")
                    + " no RPM Governor in its shaft line, so the console cannot spin it.");
        }
        if (!found.otherConsoles.isEmpty()) {
            problems.add("Another VCC console is on this craft. Two consoles fight over the same wheels: break one.");
        }
        for (Tie t : graph.ties) {
            if (t.b() >= 0) continue;
            problems.add("A " + t.kind().noun() + " ties " + (t.a() == 0 ? "this vehicle" : "B" + (t.a() + 1))
                    + " to the ground: it holds the vehicle back until it is cut, unhooked or unpowered.");
        }
        return new Scan(true, bodies, List.copyOf(graph.ties), wheels, props, swivels, machines, found.links, governorOf,
                found.otherConsoles, problems);
    }

    private static void ties(ServerLevel level, BlockEntity be, Body body, Graph g, Found found) {
        TrailerLinks.Type type = TrailerLinks.of(be);
        if (type == null) return;
        BlockPos pos = be.getBlockPos();

        if (SableBridge.matchesSwivelBearing(be) && SableBridge.isSwivelAssembled(be) && !body.towed()) {
            UUID other = Vcc4Reflect.swivelSubLevelId(be);
            if (other != null) found.swivelCarries.put(pos, other);
        }
        TrailerLinks.Tie tie = type.tie(level, be);
        if (tie != null) g.tie(type, tie, body.index(), body.id(), pos);
    }

    private static List<Body> effectiveLinks(Graph g) {
        List<Body> out = new ArrayList<>(g.bodies.size());
        for (Body body : g.bodies) {
            if (body.index() == 0 || body.parent() < 0) {
                out.add(body);
                continue;
            }
            Tie best = null;
            for (Tie t : g.ties) {
                if (t.joins(body.index(), body.parent()) && (best == null || t.kind().stifferThan(best.kind()))) best = t;
            }
            out.add(best == null ? body : body.linkedBy(best));
        }
        return out;
    }

    public static List<BlockEntity> onOwnBody(ServerLevel level, BlockEntity controller) {
        Object craft = SableBridge.subLevelAccessOf(controller);
        if (craft == null) return List.of();
        Body body = new Body(0, SableBridge.subLevelId(craft), craft, controller.getBlockPos(), -1, null, LinkKind.JOINT,
                null, false, null, false, false);
        return blockEntitiesOn(level, body, craft, SCAN_CHUNK_RADIUS, controller);
    }

    public static List<BlockEntity> onBodies(ServerLevel level, Scan scan, BlockEntity controller) {
        List<BlockEntity> out = new ArrayList<>();
        Object craft = SableBridge.subLevelAccessOf(controller);
        for (Body body : scan.bodies()) {
            if (body.foreign()) continue;
            int b = body.index();
            out.addAll(blockEntitiesOn(level, body, b == 0 ? craft : null, b == 0 ? SCAN_CHUNK_RADIUS : BODY_CHUNK_RADIUS, controller));
        }
        return out;
    }

    private static List<BlockEntity> blockEntitiesOn(ServerLevel level, Body body, @Nullable Object craft, int radius,
                                                     BlockEntity controller) {
        List<BlockEntity> out = new ArrayList<>();
        int cx = body.anchor().getX() >> 4;
        int cz = body.anchor().getZ() >> 4;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) continue;
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (be == controller || be.isRemoved()) continue;
                    if (sameBody(be, craft, body.id())) out.add(be);
                }
            }
        }
        return out;
    }

    private static void classify(ServerLevel level, BlockEntity be, int body, Found found) {
        boolean governor = be instanceof RpmGovernorBE;
        boolean wheel = !governor && Vcc4Reflect.is(Vcc4Reflect.WHEEL_MOUNT, be);
        boolean swivel = !governor && !wheel && SableBridge.matchesSwivelBearing(be);
        SableBridge.PropData bearing = governor || wheel || swivel ? null : SableBridge.readProp(be);
        boolean looseProp = bearing == null && !governor && !wheel && !swivel && SableBridge.matchesAnyPropeller(be);

        Vcc4Jets.Type direct = governor || wheel || swivel || bearing != null || looseProp ? null : Vcc4Jets.of(be);

        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        if (governor) {
            found.governors.add((RpmGovernorBE) be);
            found.governorBody.put(pos, body);
        } else if (wheel) {
            Direction facing = horizontalFacing(state);
            if (facing == null) return;
            found.wheels.add(new Wheel(pos, centre(pos.relative(facing)), facing.getAxis(), body));
        } else if (swivel) {
            Direction facing = facing(state);
            Vector3d axis = facing != null ? unit(facing) : new Vector3d(0, 1, 0);
            Double cog = SableBridge.swivelKineticSpeed(be);
            found.swivels.add(new Swivel(pos, axis, SableBridge.isSwivelAssembled(be), locked(state),
                    cog != null && Math.abs(cog) > 0.01, body, -1));
        } else if (bearing != null) {
            Direction facing = facing(state);
            Vector3d axis = facing != null ? unit(facing) : new Vector3d(0, 1, 0);
            Vector3d published = new Vector3d(bearing.thrustDirLocal());
            if (published.length() > 0.5) axis = published.normalize();
            // sable applies prop force at the block centre, measure the arm from there

            found.props.add(new Prop(pos, centre(pos), axis, Math.max(1.0, bearing.sailPower()), body));
        } else if (looseProp) {
            Direction facing = SableBridge.propellerBlockDirection(be);
            if (facing == null) facing = facing(state);
            found.props.add(new Prop(pos, centre(pos), facing != null ? unit(facing) : new Vector3d(0, 1, 0), 1.0, body));
        } else if (direct != null) {
            Vcc4Jets.Spec spec = direct.spec(be);

            if (spec != null) found.props.add(new Prop(pos, spec.centre(), spec.axis(), spec.configForce(), body, spec.kind()));
        } else if (be instanceof VehicleLightBE && state.getBlock() instanceof VehicleLightBlock lamp) {
            found.links.add(new Link(pos, null, state.getBlock().getDescriptionId(), "", body, false, lamp.kind(),
                    state.getValue(VehicleLightBlock.FACING)));
        } else if (be instanceof SmartBlockEntity smart) {
            LinkBehaviour link = BlockEntityBehaviour.get(smart, LinkBehaviour.TYPE);
            if (link != null && link.isListening()) {
                Couple<RedstoneLinkNetworkHandler.Frequency> key = link.getNetworkKey();
                found.links.add(new Link(pos, key, frequencyName(key.getFirst()), frequencyName(key.getSecond()), body,
                        againstDockingConnector(level, pos)));
            }
        }
    }

    private static boolean againstDockingConnector(ServerLevel level, BlockPos receiver) {
        for (Direction d : Direction.values()) {
            if (Vcc4Reflect.is(Vcc4Reflect.DOCKING_CONNECTOR, SableBridge.getBlockEntityOnContraption(level, receiver.relative(d)))) return true;
        }
        return false;
    }

    private static String frequencyName(@Nullable RedstoneLinkNetworkHandler.Frequency f) {
        if (f == null) return "";
        ItemStack stack = f.getStack();
        if (stack == null || stack.isEmpty()) return "";
        if (stack.has(DataComponents.CUSTOM_NAME)) return stack.getHoverName().getString();
        return stack.getItem().getDescriptionId();
    }

    private static List<String> machineNames(ServerLevel level, Set<BlockPos> reached) {
        List<String> out = new ArrayList<>();
        for (BlockPos p : reached) {
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, p);
            if (!(be instanceof KineticBlockEntity k) || !isMachine(k)) continue;
            String id = be.getBlockState().getBlock().getDescriptionId();
            if (!out.contains(id)) out.add(id);
            if (out.size() >= MAX_MACHINE_NAMES) break;
        }
        return out;
    }

    private static boolean isMachine(KineticBlockEntity be) {
        if (be instanceof RpmGovernorBE || be instanceof GeneratingKineticBlockEntity) return false;
        if (be instanceof SimpleKineticBlockEntity || be instanceof GearboxBlockEntity || be instanceof SplitShaftBlockEntity
                || be instanceof ChainGearshiftBlockEntity || be instanceof BeltBlockEntity || be instanceof GaugeBlockEntity) {
            return false;
        }

        if (be.getClass() == KineticBlockEntity.class) return false;
        return !Vcc4Reflect.is(Vcc4Reflect.SWIVEL_PLATE, be) && !Vcc4Reflect.is(Vcc4Reflect.WHEEL_MOUNT, be);
    }

    static boolean locked(BlockState state) {
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    private static Map<BlockPos, BlockPos> pairGovernors(ServerLevel level, List<RpmGovernorBE> governors,
                                                         Set<BlockPos> drivable, Map<BlockPos, Set<BlockPos>> downstream) {
        Map<BlockPos, BlockPos> out = new HashMap<>();
        for (RpmGovernorBE g : governors) {
            Direction facing = facing(g.getBlockState());
            if (facing == null) continue;
            Direction input = null;
            if (g.hasSource() && g.source != null) {
                BlockPos d = g.source.subtract(g.getBlockPos());
                input = Direction.getNearest(d.getX(), d.getY(), d.getZ());
            }
            if (input == null) input = Vcc4Reflect.governorLastInput(g);

            Set<BlockPos> sideA = new LinkedHashSet<>();
            Set<BlockPos> sideB = new LinkedHashSet<>();
            boolean engineA = walk(level, g, facing, sideA);
            boolean engineB = walk(level, g, facing.getOpposite(), sideB);
            Set<BlockPos> down;
            if (input == facing) down = sideB;
            else if (input == facing.getOpposite()) down = sideA;
            else if (engineA && !engineB) down = sideB;
            else if (engineB && !engineA) down = sideA;
            else down = sideA;

            downstream.put(g.getBlockPos(), down);
            for (BlockPos reached : down) {
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
            for (BlockPos np : Vcc4Reflect.potentialNeighbours(cur)) {
                if (seen.contains(np)) continue;
                if (!(SableBridge.getBlockEntityOnContraption(level, np) instanceof KineticBlockEntity next)) continue;
                if (!(RotationPropagator.isConnected(cur, next) || RotationPropagator.isConnected(next, cur))) continue;
                seen.add(np);
                queue.add(next);
            }
        }
        return engine;
    }

    private static boolean sameBody(BlockEntity be, @Nullable Object craft, @Nullable UUID id) {
        Object other = SableBridge.subLevelAccessOf(be);
        if (other == null) return false;
        if (craft != null && (other == craft || other.equals(craft))) return true;
        UUID otherId = SableBridge.subLevelId(other);
        return id != null && id.equals(otherId);
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

    public static Vector3d centre(BlockPos pos) {
        return new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    static Vector3d unit(Direction d) {
        return new Vector3d(d.getStepX(), d.getStepY(), d.getStepZ());
    }
}
