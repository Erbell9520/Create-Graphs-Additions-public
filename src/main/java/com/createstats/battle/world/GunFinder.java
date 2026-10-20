package com.createstats.battle.world;

import com.createstats.CreateStats;
import com.createstats.battle.core.GunGraph;
import com.createstats.battle.core.SurveyStore;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
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
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// walk the real shafts. the old "yaw/pitch mapping" toggles were a guessing game
public final class GunFinder {
    public static final int EVERY = 100;

    public static final int MAX_BODIES = 6;

    public static final int VISIT_LIMIT = 8192;

    private static volatile Method neighbours;
    private static volatile Field lastInput;
    private static volatile Method swivelSubLevel;
    private static volatile boolean warned;

    private GunFinder() {
    }

    private record Found(BlockEntity be, @Nullable UUID body) {
    }

    static void scan(ShipRecord r, ServerLevel level, long now) {
        List<Found> all = new ArrayList<>();
        ShipScanner.Area area = ShipScanner.area(r, level);
        if (area == null) return;
        collect(area.chunks(), area.subLevel(), null, r, all);

        Set<UUID> bodies = new HashSet<>();
        if (area.subLevel() != null) {
            UUID own = SableBridge.subLevelId(area.subLevel());
            if (own != null) bodies.add(own);
        }
        r.ownBodies.clear();
        for (int i = 0; i < all.size() && bodies.size() < MAX_BODIES; i++) {
            BlockEntity be = all.get(i).be();
            if (!SableBridge.matchesSwivelBearing(be) || !SableBridge.isSwivelAssembled(be)) continue;
            UUID carried = swivelBody(be);
            if (carried == null || !bodies.add(carried)) continue;
            Object sub = SableBridge.findSubLevelById(level, carried);
            if (sub != null) collect(SablePlots.plotChunks(sub), sub, carried, null, all);
        }

        r.ownBodies.addAll(bodies);

        List<Found> mounts = new ArrayList<>();
        List<RpmGovernorBE> governors = new ArrayList<>();
        for (Found f : all) {
            if (isMount(f.be()) || isFixedMount(f.be())) mounts.add(f);
            else if (f.be() instanceof RpmGovernorBE g) governors.add(g);
        }

        GunGraph graph = new GunGraph();
        Map<BlockPos, Integer> nodes = new HashMap<>();
        int[] visited = {0};
        for (RpmGovernorBE g : governors) {
            Direction facing = facing(g.getBlockState());
            if (facing == null) continue;
            int a = side(level, g, facing, graph, nodes, visited);
            int b = side(level, g, facing.getOpposite(), graph, nodes, visited);
            graph.governor(g.getBlockPos().asLong(), a, b, inputSide(g, facing));
        }
        long[] keys = new long[mounts.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = mounts.get(i).be().getBlockPos().asLong();
        List<GunGraph.Drive> drives = graph.solve(keys);

        Map<Long, GunRecord> byMount = new HashMap<>();
        for (GunRecord g : r.guns) byMount.put(g.mount, g);
        Set<Long> found = new HashSet<>();
        for (int i = 0; i < mounts.size(); i++) {
            Found f = mounts.get(i);
            long key = keys[i];
            found.add(key);
            GunRecord g = byMount.get(key);
            if (g == null) {
                if (r.guns.size() >= ShipRecord.MAX_GUNS) continue;
                g = new GunRecord(r.nextGunId(), key, f.body());
                r.guns.add(g);
                r.changed();
            }
            GunGraph.Drive d = drives.get(i);
            boolean fixed = isFixedMount(f.be());
            g.mountType = fixed ? GunRecord.MOUNT_FIXED
                    : f.be().getBlockState().hasProperty(BlockStateProperties.VERTICAL_DIRECTION)
                    && f.be().getBlockState().getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.UP
                    ? GunRecord.MOUNT_HANGING : GunRecord.MOUNT_NORMAL;
            g.yawGov = fixed ? GunGraph.NONE : d.yaw();
            g.pitchGov = fixed ? GunGraph.NONE : d.pitch();
            g.yawConflict = d.yawConflict();
            g.pitchConflict = d.pitchConflict();
            g.shared = d.shared();
            g.seenAt = now;
        }
        r.guns.removeIf(g -> {
            if (found.contains(g.mount)) return false;

            if (g.body == null) {
                int packed = r.pack(BlockPos.of(g.mount));
                int slot = packed < 0 ? -1 : r.store.slotOf(packed);
                if (slot >= 0 && r.store.isLost(slot)) {
                    byte cause = r.store.causeAt(slot);
                    if (cause != SurveyStore.CAUSE_PLAYER) return false;
                }
            }
            FireControl.letGo(r, level, g);
            r.changed();
            return true;
        });
        r.guns.sort((x, y) -> Integer.compare(x.id, y.id));
        batteries(r);
    }

    private static void batteries(ShipRecord r) {
        Map<Long, Integer> yawLead = new HashMap<>();
        Map<Long, Integer> pitchLead = new HashMap<>();
        for (GunRecord g : r.guns) {
            g.yawLeader = 0;
            g.pitchLeader = 0;
            if (g.yawGov != GunGraph.NONE) {
                Integer lead = yawLead.putIfAbsent(g.yawGov, g.id);
                if (lead != null) g.yawLeader = lead;
            }
            if (g.pitchGov != GunGraph.NONE) {
                Integer lead = pitchLead.putIfAbsent(g.pitchGov, g.id);
                if (lead != null) g.pitchLeader = lead;
            }
        }
    }

    private static void collect(List<LevelChunk> chunks, @Nullable Object subLevel, @Nullable UUID body,
                                @Nullable ShipRecord groundFilter, List<Found> out) {
        for (LevelChunk c : chunks) {
            for (BlockEntity be : List.copyOf(c.getBlockEntities().values())) {
                if (be.isRemoved()) continue;
                if (subLevel == null && groundFilter != null && !groundFilter.inGroundBox(be.getBlockPos())) continue;
                out.add(new Found(be, body));
            }
        }
    }

    private static int side(ServerLevel level, RpmGovernorBE g, Direction dir, GunGraph graph,
                            Map<BlockPos, Integer> nodes, int[] visited) {
        BlockPos p = g.getBlockPos().relative(dir);
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, p);
        if (isMount(be)) {
            int axis = mountAxis(be, g, p);
            return axis < 0 ? -1 : graph.terminal(p.asLong(), axis);
        }
        return walk(level, p, graph, nodes, visited);
    }

    private static int walk(ServerLevel level, BlockPos start, GunGraph graph, Map<BlockPos, Integer> nodes, int[] visited) {
        BlockEntity first = SableBridge.getBlockEntityOnContraption(level, start);
        if (!(first instanceof KineticBlockEntity k0) || isMount(first)) return -1;
        Integer known = nodes.get(start);
        if (known != null) return known;
        int startNode = node(graph, nodes, k0);
        ArrayDeque<KineticBlockEntity> queue = new ArrayDeque<>();
        queue.add(k0);
        while (!queue.isEmpty() && visited[0] < VISIT_LIMIT) {
            KineticBlockEntity cur = queue.poll();
            visited[0]++;
            int curNode = nodes.get(cur.getBlockPos());
            if (cur instanceof RpmGovernorBE) continue;
            for (BlockPos np : neighbours(cur)) {
                BlockEntity nb = SableBridge.getBlockEntityOnContraption(level, np);
                if (nb == null) continue;
                if (isMount(nb)) {
                    int axis = mountAxis(nb, cur, np);
                    if (axis >= 0) graph.edge(curNode, graph.terminal(np.asLong(), axis));
                    continue;
                }
                if (!(nb instanceof KineticBlockEntity next)) continue;
                if (!(RotationPropagator.isConnected(cur, next) || RotationPropagator.isConnected(next, cur))) continue;
                boolean fresh = !nodes.containsKey(np);
                int nextNode = node(graph, nodes, next);
                graph.edge(curNode, nextNode);
                if (fresh) queue.add(next);
            }
        }
        return startNode;
    }

    private static int node(GunGraph graph, Map<BlockPos, Integer> nodes, KineticBlockEntity be) {
        Integer n = nodes.get(be.getBlockPos());
        if (n != null) return n;
        int i = graph.node(be.getBlockPos().asLong(), be instanceof GeneratingKineticBlockEntity);
        nodes.put(be.getBlockPos().immutable(), i);
        return i;
    }

    private static int mountAxis(BlockEntity mount, KineticBlockEntity from, BlockPos mountPos) {
        BlockPos offset = from.getBlockPos().subtract(mountPos);
        Direction toMount = Direction.getNearest(-offset.getX(), -offset.getY(), -offset.getZ());
        BlockState fs = from.getBlockState();
        if (!(fs.getBlock() instanceof IRotate rot) || !rot.hasShaftTowards(from.getLevel(), from.getBlockPos(), fs, toMount)) {
            return -1;
        }
        try {
            Method m = mount.getClass().getMethod("getInterfacingBlockEntity", BlockPos.class);
            Object face = m.invoke(mount, offset);
            if (face == null) return -1;
            Object yaw = mount.getClass().getMethod("getYawInterface").invoke(mount);
            Object pitch = mount.getClass().getMethod("getPitchInterface").invoke(mount);
            if (face == yaw) return GunGraph.YAW;
            if (face == pitch) return GunGraph.PITCH;
            return -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            BlockState ms = mount.getBlockState();
            if (!ms.hasProperty(BlockStateProperties.VERTICAL_DIRECTION) || !ms.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return -1;
            Direction vertical = ms.getValue(BlockStateProperties.VERTICAL_DIRECTION);
            Direction face = toMount.getOpposite();
            if (face == vertical) return GunGraph.YAW;
            Direction.Axis pitchAxis = ms.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == Direction.Axis.X
                    ? Direction.Axis.Z : Direction.Axis.X;
            return face.getAxis() == pitchAxis ? GunGraph.PITCH : -1;
        }
    }

    private static int inputSide(RpmGovernorBE g, Direction facing) {
        Direction input = null;
        if (g.hasSource() && g.source != null) {
            BlockPos d = g.source.subtract(g.getBlockPos());
            input = Direction.getNearest(d.getX(), d.getY(), d.getZ());
        }
        if (input == null) input = lastInput(g);
        if (input == facing) return 0;
        if (input == facing.getOpposite()) return 1;
        return -1;
    }

    static boolean isMount(@Nullable BlockEntity be) {
        return be != null && be.getClass().getName().endsWith(".CannonMountBlockEntity");
    }

    static boolean isFixedMount(@Nullable BlockEntity be) {
        return be != null && be.getClass().getName().endsWith(".FixedCannonMountBlockEntity");
    }

    static boolean isAnyMount(@Nullable BlockEntity be) {
        return isMount(be) || isFixedMount(be);
    }

    @Nullable
    static Direction facing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) return state.getValue(BlockStateProperties.FACING);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (Property<?> p : state.getProperties()) {
            if (p instanceof DirectionProperty dp) return state.getValue(dp);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<BlockPos> neighbours(KineticBlockEntity be) {
        Method m = neighbours;
        if (m == null) {
            try {
                m = RotationPropagator.class.getDeclaredMethod("getPotentialNeighbourLocations", KineticBlockEntity.class);
                m.setAccessible(true);
                neighbours = m;
            } catch (ReflectiveOperationException | RuntimeException e) {
                warn("getPotentialNeighbourLocations", e);
                return sixFaces(be.getBlockPos());
            }
        }
        try {
            Object v = m.invoke(null, be);
            return v instanceof List<?> list ? (List<BlockPos>) list : sixFaces(be.getBlockPos());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return sixFaces(be.getBlockPos());
        }
    }

    private static List<BlockPos> sixFaces(BlockPos p) {
        return List.of(p.north(), p.south(), p.east(), p.west(), p.above(), p.below());
    }

    @Nullable
    private static Direction lastInput(RpmGovernorBE g) {
        Field f = lastInput;
        if (f == null) {
            try {
                f = RpmGovernorBE.class.getDeclaredField("lastKnownInputFacing");
                f.setAccessible(true);
                lastInput = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        try {
            return f.get(g) instanceof Direction d ? d : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    static UUID swivelBody(BlockEntity swivel) {
        Method m = swivelSubLevel;
        try {
            if (m == null) {
                m = swivel.getClass().getMethod("getSubLevelID");
                swivelSubLevel = m;
            }
            return m.invoke(swivel) instanceof UUID u ? u : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void warn(String what, Exception e) {
        if (warned) return;
        warned = true;
        CreateStats.LOGGER.warn("[Battle Bridge] gun finder reflection failed ({}): {}", what, e.toString());
    }
}
