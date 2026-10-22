package com.createstats.vccmk4.route;

import com.createstats.flight.bridge.SableContainers;
import com.createstats.flight.bridge.SableWake;
import com.createstats.surveyor.core.RigidMove;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RouteParking {
    static final String HOLDING_CHUNK = "dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk";
    static final String POINTER = "dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer";

    public static final String VETO_HANDLER = "createStats$parkedStaysParked";

    private static volatile Boolean vetoInstalled;

    private RouteParking() {
    }

    public record Record(UUID id, double[] bounds, List<UUID> chain, CompoundTag tag) {
    }

    public static boolean vetoInstalled() {
        Boolean v = vetoInstalled;
        if (v != null) return v;
        boolean found = false;
        try {
            for (Method m : Class.forName(HOLDING_CHUNK).getDeclaredMethods()) {
                if (m.getName().contains(VETO_HANDLER)) {
                    found = true;
                    break;
                }
            }
        } catch (ClassNotFoundException | LinkageError e) {
            found = false;
        }
        if (!found) RouteReflect.warnOnce("parkedVeto", "the parked-vehicle veto is not in Sable's holding chunk class");
        vetoInstalled = found;
        return found;
    }

    private static volatile Method dataUuid;

    @Nullable
    public static UUID idOf(@Nullable Object data) {
        if (data == null) return null;
        Method m = dataUuid;
        if (m == null || m.getDeclaringClass() != data.getClass()) {
            m = method(data.getClass(), "uuid", 0);
            if (m == null) {
                RouteReflect.warnOnce("parkedVetoId", "no uuid() on " + data.getClass().getName());
                return null;
            }
            dataUuid = m;
        }
        return call(m, data) instanceof UUID id ? id : null;
    }

    public static boolean live(ServerLevel level, UUID body) {
        return liveBody(level, body) != null;
    }

    @Nullable
    public static Object liveBody(ServerLevel level, UUID body) {
        return SableContainers.subLevel(level, body);
    }

    @Nullable
    public static Record record(ServerLevel level, UUID body) {
        Object holding = holding(level, body);
        Object data = holding == null ? null : call0(holding, "data");
        if (data == null) return null;
        CompoundTag tag = call0(data, "fullTag") instanceof CompoundTag t ? t : null;
        BoundingBox3d b = call0(data, "bounds") instanceof BoundingBox3d bb ? bb : null;
        if (tag == null || b == null) return null;
        List<UUID> chain = new ArrayList<>();
        chain.add(body);
        if (call0(data, "dependencies") instanceof List<?> deps) {
            for (Object o : deps) if (o instanceof UUID u && !chain.contains(u)) chain.add(u);
        }
        return new Record(body, new double[]{b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ}, chain, tag);
    }

    @Nullable
    public static Pose3d parkedPose(ServerLevel level, UUID body) {
        Object holding = holding(level, body);
        Object data = holding == null ? null : call0(holding, "data");
        return data != null && call0(data, "pose") instanceof Pose3d p ? p : null;
    }

    @Nullable
    public static ChunkPos holdingChunkOf(ServerLevel level, UUID body) {
        Object map = holdingMap(level);
        if (map == null) return null;
        try {
            Field f = field(map.getClass(), "loadedHoldingChunks");
            if (f == null || !(f.get(map) instanceof Map<?, ?> chunks)) {
                RouteReflect.warnOnce("holdingChunks", "SubLevelHoldingChunkMap.loadedHoldingChunks not found");
                return null;
            }
            for (Object chunk : new ArrayList<>(chunks.values())) {
                if (!(call0(chunk, "getLoadedHoldingSubLevels") instanceof Iterable<?> held)) continue;
                for (Object h : held) {
                    Object data = call0(h, "data");
                    if (data != null && body.equals(call0(data, "uuid")) && call0(chunk, "getChunkPos") instanceof ChunkPos pos) return pos;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            RouteReflect.warnOnce("holdingChunks", e.toString());
        }
        return null;
    }

    public static boolean loadHolding(ServerLevel level, ChunkPos pos) {
        Object map = holdingMap(level);
        if (map == null || pos == null) return false;
        Method m = method(map.getClass(), "getOrLoadHoldingChunk", 2, ChunkPos.class);
        if (m == null) {
            RouteReflect.warnOnce("loadHolding", "SubLevelHoldingChunkMap.getOrLoadHoldingChunk not found");
            return false;
        }
        return call(m, map, pos, false) != null;
    }

    public static String restore(ServerLevel level, ChunkPos holdingPos, UUID craft, Collection<UUID> moved, RigidMove move) {
        Object map = holdingMap(level);
        if (map == null) return "Sable's parked bodies are not available";
        if (holding(level, craft) == null) loadHolding(level, holdingPos);
        Record rec = record(level, craft);
        if (rec == null) return "its parked body is not where it was left";
        Map<UUID, Object> data = new LinkedHashMap<>();
        for (UUID id : rec.chain()) {
            Object h = holding(level, id);
            Object d = h == null ? null : call0(h, "data");
            if (d == null) return "a body parked with it is missing";
            data.put(id, d);
        }
        List<Runnable> undo = new ArrayList<>();
        try {
            for (Map.Entry<UUID, Object> e : data.entrySet()) {
                if (!moved.contains(e.getKey())) continue;
                Object d = e.getValue();
                if (!(call0(d, "fullTag") instanceof CompoundTag tag) || !(call0(d, "bounds") instanceof BoundingBox3d b)
                        || !(call0(d, "pose") instanceof Pose3d p)) {
                    throw new IllegalStateException("a parked record could not be read");
                }
                CompoundTag tagBefore = tag.copy();
                BoundingBox3d boundsBefore = new BoundingBox3d(b);
                Pose3d poseBefore = new Pose3d(p);
                undo.add(() -> {
                    for (String k : new ArrayList<>(tag.getAllKeys())) tag.remove(k);
                    for (String k : tagBefore.getAllKeys()) tag.put(k, tagBefore.get(k));
                    b.set(boundsBefore);
                    p.set(poseBefore);
                });
                moveTag(tag, move);
                double[] nb = move.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
                b.set(nb[0], nb[1], nb[2], nb[3], nb[4], nb[5]);
                double[] np = move.point(p.position().x, p.position().y, p.position().z);
                p.position().set(np[0], np[1], np[2]);
                double[] q = move.orientation(p.orientation().x, p.orientation().y, p.orientation().z, p.orientation().w);
                p.orientation().set(q[0], q[1], q[2], q[3]).normalize();
            }
        } catch (RuntimeException e) {
            for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
            RouteReflect.warnOnce("restoreEdit", e.toString());
            return "its parked body could not be moved: " + e.getMessage();
        }
        try {
            Class<?> pointerClass = Class.forName(POINTER);
            Constructor<?> make = pointerClass.getConstructor(ChunkPos.class, short.class, short.class);
            Object pointer = make.newInstance(holdingPos, (short) 0, (short) 0);
            Method snatch = method(map.getClass(), "snatchAndLoad", 2, pointerClass);
            if (snatch == null) throw new NoSuchMethodException("SubLevelHoldingChunkMap.snatchAndLoad");
            snatch.invoke(map, pointer, craft);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            boolean intact = true;
            for (UUID id : data.keySet()) intact &= !live(level, id) && record(level, id) != null;
            if (intact) for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
            RouteReflect.warnOnce("restoreLoad", e.toString());
            return "Sable could not load it: " + e;
        }
        if (live(level, craft)) return "";
        boolean intact = true;
        for (UUID id : data.keySet()) intact &= !live(level, id) && record(level, id) != null;
        if (intact) for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
        return "Sable did not load it";
    }

    public static void moveTag(CompoundTag tag, RigidMove m) {
        if (tag.contains("pose", Tag.TAG_COMPOUND)) {
            CompoundTag pose = tag.getCompound("pose");
            CompoundTag pos = pose.getCompound("position");
            double[] p = m.point(pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z"));
            pos.putDouble("x", p[0]);
            pos.putDouble("y", p[1]);
            pos.putDouble("z", p[2]);
            pose.put("position", pos);
            CompoundTag o = pose.getCompound("orientation");
            double[] q = m.orientation(o.getDouble("x"), o.getDouble("y"), o.getDouble("z"), o.getDouble("w"));
            double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
            if (n < 1.0e-9) n = 1.0;
            o.putDouble("x", q[0] / n);
            o.putDouble("y", q[1] / n);
            o.putDouble("z", q[2] / n);
            o.putDouble("w", q[3] / n);
            pose.put("orientation", o);
        }
        if (tag.contains("world_bounds", Tag.TAG_COMPOUND)) {
            CompoundTag b = tag.getCompound("world_bounds");
            double[] nb = m.box(b.getDouble("minX"), b.getDouble("minY"), b.getDouble("minZ"),
                    b.getDouble("maxX"), b.getDouble("maxY"), b.getDouble("maxZ"));
            b.putDouble("minX", nb[0]);
            b.putDouble("minY", nb[1]);
            b.putDouble("minZ", nb[2]);
            b.putDouble("maxX", nb[3]);
            b.putDouble("maxY", nb[4]);
            b.putDouble("maxZ", nb[5]);
        }
        tag.remove("linear_velocity");
        tag.remove("angular_velocity");
        for (CompoundTag strand : strands(tag)) {
            ListTag points = strand.getList("points", Tag.TAG_LIST);
            for (int i = 0; i < points.size(); i++) {
                ListTag pt = points.getList(i);
                if (pt.size() < 3) continue;
                double[] np = m.point(pt.getDouble(0), pt.getDouble(1), pt.getDouble(2));
                pt.set(0, DoubleTag.valueOf(np[0]));
                pt.set(1, DoubleTag.valueOf(np[1]));
                pt.set(2, DoubleTag.valueOf(np[2]));
            }
        }
    }

    public static List<UUID> ropeTies(CompoundTag tag) {
        List<UUID> out = new ArrayList<>();
        for (CompoundTag strand : strands(tag)) {
            ListTag ends = strand.getList("attachments", Tag.TAG_COMPOUND);
            for (int i = 0; i < ends.size(); i++) {
                CompoundTag a = ends.getCompound(i);
                UUID id = null;
                if (a.contains("subLevelID", Tag.TAG_STRING)) {
                    try {
                        id = UUID.fromString(a.getString("subLevelID"));
                    } catch (IllegalArgumentException ignored) {
                        id = null;
                    }
                }
                out.add(id);
            }
        }
        return out;
    }

    @Nullable
    public static CompoundTag consoleTag(CompoundTag body, UUID console) {
        for (CompoundTag be : blockEntities(body)) if (be.hasUUID("ControllerId") && be.getUUID("ControllerId").equals(console)) return be;
        return null;
    }

    public static List<UUID> dockTies(CompoundTag tag) {
        List<UUID> out = new ArrayList<>();
        for (CompoundTag be : blockEntities(tag)) {
            if (be.getString("id").equals("simulated:docking_connector") && be.contains("OtherConnector")) {
                out.add(be.hasUUID("OtherConnectorSubLevelId") ? be.getUUID("OtherConnectorSubLevelId") : null);
            }
        }
        return out;
    }

    public static List<UUID> jointTies(CompoundTag tag) {
        List<UUID> out = new ArrayList<>();
        for (CompoundTag be : blockEntities(tag)) {
            switch (be.getString("id")) {
                case "simulated:spring" -> {
                    if (be.contains("Goal")) out.add(be.hasUUID("GoalSubLevel") ? be.getUUID("GoalSubLevel") : null);
                }
                case "simulated:swivel_bearing" -> {
                    if (be.contains("SwivelPlate") || be.hasUUID("SubLevelID")) {
                        out.add(be.hasUUID("SubLevelID") ? be.getUUID("SubLevelID") : null);
                    }
                }
                case "simulated:swivel_bearing_link_block" -> {
                    if (be.contains("ParentPos") || be.contains("parent")) {
                        out.add(be.hasUUID("ParentSubLevelId") ? be.getUUID("ParentSubLevelId") : null);
                    }
                }
                default -> {}
            }
        }
        return out;
    }

    public static void pauseLoadedConsole(ServerLevel level, UUID body, UUID console, String reason) {
        int[] bounds = RouteReflect.plotBounds(liveBody(level, body));
        if (bounds == null) return;
        for (int cx = bounds[0] >> 4; cx <= bounds[3] >> 4; cx++) {
            for (int cz = bounds[2] >> 4; cz <= bounds[5] >> 4; cz++) {
                var chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (var pos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(pos) instanceof com.createstats.vccmk4.Vcc4BE be && be.consoleId().equals(console)) {
                        be.route().pause(level, reason);
                        return;
                    }
                }
            }
        }
    }

    private static List<CompoundTag> blockEntities(CompoundTag tag) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag chunks = tag.getCompound("plot").getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            ListTag bes = chunks.getCompound(key).getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < bes.size(); i++) out.add(bes.getCompound(i));
        }
        return out;
    }

    static List<CompoundTag> strands(CompoundTag tag) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag chunks = tag.getCompound("plot").getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            ListTag bes = chunks.getCompound(key).getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < bes.size(); i++) {
                CompoundTag be = bes.getCompound(i);
                if (be.contains("Strand", Tag.TAG_COMPOUND)) out.add(be.getCompound("Strand"));
            }
        }
        return out;
    }

    public static void wake(ServerLevel level, UUID body) {
        Object sub = liveBody(level, body);
        if (sub != null) SableWake.wake(level, sub);
    }

    @Nullable
    private static Object holding(ServerLevel level, UUID body) {
        Object map = holdingMap(level);
        if (map == null || body == null) return null;
        Method get = method(map.getClass(), "getHoldingSubLevel", 1, UUID.class);
        if (get == null) {
            RouteReflect.warnOnce("holdingSubLevel", "SubLevelHoldingChunkMap.getHoldingSubLevel not found");
            return null;
        }
        return call(get, map, body);
    }

    @Nullable
    private static Object holdingMap(ServerLevel level) {
        return SableContainers.holdingChunkMap(level);
    }

    @Nullable
    private static Object call0(@Nullable Object target, String name) {
        if (target == null) return null;
        Method m = method(target.getClass(), name, 0);
        if (m == null) {
            RouteReflect.warnOnce("parking." + name, "no " + name + "() on " + target.getClass().getName());
            return null;
        }
        return call(m, target);
    }

    @Nullable
    private static Object call(Method m, Object target, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            RouteReflect.warnOnce("parking." + m.getName(), e.toString());
            return null;
        }
    }

    @Nullable
    private static Method method(Class<?> c, String name, int params, Class<?>... first) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != params) continue;
                if (first.length > 0 && !m.getParameterTypes()[0].isAssignableFrom(first[0])) continue;
                try {
                    m.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                return m;
            }
        }
        return null;
    }

    @Nullable
    private static Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
