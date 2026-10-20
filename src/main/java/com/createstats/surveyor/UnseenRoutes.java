package com.createstats.surveyor;

import com.createstats.CreateStats;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.CargoSnapshot;
import com.createstats.vccmk4.route.GhostHost;
import com.createstats.vccmk4.route.RouteSensors;
import com.createstats.vccmk4.route.TravelNbt;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RigidMove;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.UnseenTravel;
import com.createstats.vccmk4.route.RouteParking;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// real or unseen, never both. like create trains
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class UnseenRoutes extends SavedData {
    static final String FILE = CreateStats.MOD_ID + "_unseen_routes";

    static final int LOOKUP_TICKS = 100;
    static final int LOOKUP_TRIES = 12;

    public enum Mode {
        REAL, UNSEEN, STRANDED
    }

    public record Report(UUID console, UUID craft, UUID pathway, String name, @Nullable UUID owner, List<UUID> bodies,
                         int state, double s, double planned, double speed, double[] refLocal, double[] noseLocal,
                         double refX, double refY, double refZ, double rise, double riseOverLine, double front, double rear,
                         double half, List<double[]> bodyPositions, CompoundTag driver, CargoSnapshot cargo) {
    }

    static final class Vehicle {
        final UUID console;
        UUID craft;
        UUID pathway;
        String name = "Mk IV";
        @Nullable UUID owner;
        List<UUID> bodies = new ArrayList<>();
        Mode mode = Mode.REAL;
        int state = RouteRunner.State.RUNNING.ordinal();
        double s;
        double speed;
        double planned;
        double[] refLocal = {0.0, 0.0, 0.0};
        double[] noseLocal = {0.0, 0.0, -1.0};
        double rise = Double.NaN;
        double riseOverLine;
        double front = 2.0;
        double rear = 2.0;
        double half = 1.5;
        List<double[]> bodyPositions = new ArrayList<>();

        @Nullable ChunkPos holding;
        List<UUID> chain = new ArrayList<>();
        List<double[]> chainBounds = new ArrayList<>();
        Set<UUID> moving = new HashSet<>();
        double parkX;
        double parkY;
        double parkZ;
        double parkHeading;
        String why = "";
        long since;
        CompoundTag driver = defaultDriver();
        CargoSnapshot cargo = CargoSnapshot.unavailable(0);
        CompoundTag ghostRoute = new CompoundTag();
        double atX, atY, atZ, atHeading;
        boolean returnBlocked;
        @Nullable GhostHost ghost;

        long reportedAt = Long.MIN_VALUE;
        long lastLookup = Long.MIN_VALUE;
        int lookups;
        final UnseenTravel.Settle settle = new UnseenTravel.Settle(UnseenTravel.SETTLE_TICKS);
        @Nullable PathSpline spline;
        long splineVersion = Long.MIN_VALUE;
        String lastWhy = "";
        long retryAt;

        Vehicle(UUID console) {
            this.console = console;
        }
    }

    private final Map<UUID, Vehicle> vehicles = new LinkedHashMap<>();

    private final Set<UUID> parked = new HashSet<>();

    public static UnseenRoutes get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(UnseenRoutes::new, UnseenRoutes::load), FILE);
    }

    public static boolean keepsParked(ServerLevel level, Object data) {
        UUID id = RouteParking.idOf(data);
        return id != null && get(level).parked.contains(id);
    }

    public static void report(ServerLevel level, Report r) {
        UnseenRoutes u = get(level);
        Vehicle v = u.vehicles.computeIfAbsent(r.console(), Vehicle::new);
        long now = level.getGameTime();
        if (v.mode == Mode.UNSEEN) {
            CreateStats.LOGGER.error("[Unseen routes] {} is in the world while it travels unseen; taking it as real here", r.name());
            if (v.ghost != null) { v.ghost.route().unloaded(level); v.ghost = null; }
            u.unpark(v);
        }
        if (v.mode != Mode.REAL) {
            v.mode = Mode.REAL;
            v.since = now;
            v.why = "";
            u.setDirty();
        }
        boolean changed = !r.pathway().equals(v.pathway) || !r.craft().equals(v.craft) || !r.bodies().equals(v.bodies);
        v.craft = r.craft();
        v.pathway = r.pathway();
        v.name = r.name();
        v.owner = r.owner();
        v.bodies = new ArrayList<>(r.bodies());
        v.state = r.state();
        v.s = r.s();
        v.planned = r.planned();
        v.speed = r.speed();
        v.refLocal = r.refLocal();
        v.noseLocal = r.noseLocal();
        if (Double.isFinite(r.rise())) v.rise = r.rise();
        v.riseOverLine = r.riseOverLine();
        v.front = r.front();
        v.rear = r.rear();
        v.half = r.half();
        v.bodyPositions = r.bodyPositions();
        v.driver = r.driver().copy(); v.cargo = r.cargo();
        v.atX = r.refX(); v.atY = r.refY(); v.atZ = r.refZ();
        v.reportedAt = now;
        v.lookups = 0;
        if (changed || now % 20 == 0) u.setDirty();
    }

    public static void forget(ServerLevel level, UUID console) {
        UnseenRoutes u = get(level);
        Vehicle v = u.vehicles.remove(console);
        if (v != null) {
            u.unpark(v);
            u.setDirty();
        }
    }

    @Nullable
    public static String unseenLine(ServerLevel level, UUID console) {
        Vehicle v = get(level).vehicles.get(console);
        if (v == null || v.mode == Mode.REAL) return null;
        return v.mode == Mode.UNSEEN ? "Travelling unseen" + (v.why.isEmpty() ? "" : " (" + v.why + ")") : "Parked: " + v.why;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.tickRateManager().runsNormally()) return;
        UnseenRoutes u = get(level);
        if (u.vehicles.isEmpty()) return;
        long now = level.getGameTime();
        for (Vehicle v : new ArrayList<>(u.vehicles.values())) {
            try {
                switch (v.mode) {
                    case REAL -> u.tickReal(level, v, now);
                    case UNSEEN -> u.tickUnseen(level, v, now);
                    case STRANDED -> u.reportParked(level, v);
                }
            } catch (RuntimeException e) {
                CreateStats.LOGGER.error("[Unseen routes] {}: {}", v.name, e.toString(), e);
            }
        }
    }

    private void tickReal(ServerLevel level, Vehicle v, long now) {
        if (v.reportedAt == now || v.craft == null) return;
        if (RouteParking.live(level, v.craft)) return;
        ChunkPos at = RouteParking.holdingChunkOf(level, v.craft);
        if (at == null) {
            if (v.lastLookup != Long.MIN_VALUE && now - v.lastLookup < LOOKUP_TICKS) return;
            v.lastLookup = now;

            for (double[] p : v.bodyPositions) {
                ChunkPos c = new ChunkPos(BlockPos.containing(p[0], p[1], p[2]));
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) RouteParking.loadHolding(level, new ChunkPos(c.x + dx, c.z + dz));
                }
            }
            at = RouteParking.holdingChunkOf(level, v.craft);
            if (at == null) {
                if (++v.lookups >= LOOKUP_TRIES) strand(level, v, now, "it could not be found where it was last seen");
                return;
            }
        }
        goUnseen(level, v, at, now);
    }

    private void goUnseen(ServerLevel level, Vehicle v, ChunkPos holding, long now) {
        v.parkX = v.atX; v.parkY = v.atY; v.parkZ = v.atZ;
        if (!RouteParking.vetoInstalled()) {
            strand(level, v, now, "this Sable version cannot keep it parked while it travels");
            return;
        }
        RouteParking.Record craft = RouteParking.record(level, v.craft);
        Pose3d pose = RouteParking.parkedPose(level, v.craft);
        if (craft == null || pose == null) {
            strand(level, v, now, "its parked body could not be read");
            return;
        }

        List<double[]> bounds = new ArrayList<>();
        Set<UUID> moving = new HashSet<>();
        Map<UUID, double[]> own = new LinkedHashMap<>();
        for (UUID id : craft.chain()) {
            RouteParking.Record r = RouteParking.record(level, id);
            if (r == null) {
                strand(level, v, now, "a body parked with it could not be read");
                return;
            }
            bounds.add(r.bounds());
            if (id.equals(v.craft) || v.bodies.contains(id)) own.put(id, r.bounds());
        }
        for (int i = 0; i < craft.chain().size(); i++) {
            UUID id = craft.chain().get(i);
            if (own.containsKey(id)) {
                moving.add(id);
                continue;
            }
            boolean rides = false;
            for (double[] host : own.values()) rides |= UnseenTravel.riding(bounds.get(i), host);
            if (!rides) {
                strand(level, v, now, "another craft is parked against it");
                return;
            }
            moving.add(id);
        }
        for (UUID id : craft.chain()) {
            RouteParking.Record r = RouteParking.record(level, id);
            for (UUID tie : RouteParking.ropeTies(r.tag())) {
                if (tie == null || !moving.contains(tie)) {
                    strand(level, v, now, "a rope ties it to something that cannot travel with it"); return;
                }
            }
            for (UUID tie : RouteParking.dockTies(r.tag())) {
                if (tie == null || !moving.contains(tie)) {
                    strand(level, v, now, "a docking connector ties it to something that cannot travel with it"); return;
                }
            }
            for (UUID tie : RouteParking.jointTies(r.tag())) {
                if (tie == null || !moving.contains(tie)) {
                    strand(level, v, now, "a spring or bearing ties it to something that cannot travel with it"); return;
                }
            }
        }
        Vector3d ref = pose.transformPosition(new Vector3d(v.refLocal[0], v.refLocal[1], v.refLocal[2]));
        Vector3d nose = pose.transformNormal(new Vector3d(v.noseLocal[0], v.noseLocal[1], v.noseLocal[2]));
        v.parkX = ref.x;
        v.parkY = ref.y;
        v.parkZ = ref.z;
        v.parkHeading = PathSpline.heading(nose.x, nose.z);
        v.holding = holding;
        v.chain = new ArrayList<>(craft.chain());
        v.chainBounds = bounds;
        v.moving = moving;
        CompoundTag console = RouteParking.consoleTag(craft.tag(), v.console);
        if (console == null || !console.contains("Route", Tag.TAG_COMPOUND)) {
            strand(level, v, now, "its parked route could not be read"); return;
        }
        v.ghostRoute = console.getCompound("Route").copy();
        v.atX = v.parkX; v.atY = v.parkY; v.atZ = v.parkZ; v.atHeading = v.parkHeading;
        v.mode = Mode.UNSEEN;
        v.since = now;
        v.why = "";
        v.settle.reset();

        v.speed = Math.max(0.0, v.speed);
        if (!ensureGhost(level, v, now)) return;
        parked.addAll(v.chain);
        setDirty();
    }

    private static CompoundTag defaultDriver() {
        CompoundTag t = new CompoundTag(); t.putDouble("Kappa", 0.15); t.putDouble("Lock", 30);
        t.putDouble("HalfTrack", 1); t.putInt("MaxRpm", 128); t.putDouble("Height", 3); return t;
    }

    private boolean ensureGhost(ServerLevel level, Vehicle v, long now) {
        if (v.ghost != null) return true;
        if (v.holding != null) RouteParking.loadHolding(level, v.holding);
        RouteParking.Record record = RouteParking.record(level, v.craft);
        CompoundTag console = record == null ? null : RouteParking.consoleTag(record.tag(), v.console);
        if (console == null) { strand(level, v, now, "its parked console could not be found"); return false; }
        if (v.ghostRoute.isEmpty()) {
            v.ghostRoute = console.getCompound("Route").copy();

            if (v.ghostRoute.contains("Runner")) v.ghostRoute.getCompound("Runner").putDouble("S", v.s);
        }
        v.ghost = new GhostHost(level, v.console, v.craft, v.name, new ArrayList<>(v.moving),
                new BlockPos(console.getInt("x"), console.getInt("y"), console.getInt("z")), v.driver,
                new double[]{v.front, v.rear, v.half, v.driver.getDouble("Height")}, v.cargo, v.ghostRoute,
                v.s, v.speed, new UnseenTravel.Place(v.atX, v.atY, v.atZ, v.atHeading));
        return true;
    }

    private void tickUnseen(ServerLevel level, Vehicle v, long now) {
        if (!ensureGhost(level, v, now)) return;
        if (v.returnBlocked) v.ghost.obstruction(new RouteSensors.Obstacle(0, false, v.why));
        v.ghost.tick(now);
        v.s = v.ghost.progress(); v.speed = v.ghost.speed();
        if (v.ghost.route().runner() != null) v.state = v.ghost.route().state().ordinal();
        v.pathway = v.ghost.route().pathwayId();
        UnseenTravel.Place at = v.ghost.place();
        v.atX = at.x(); v.atY = at.y(); v.atZ = at.z(); v.atHeading = at.heading();
        if (v.ghost.takeChanged() || now % 100 == 0) { v.ghostRoute = v.ghost.save(); setDirty(); }
        if (v.pathway == null) reportParked(level, v);
        RigidMove move = UnseenTravel.move(v.parkX, v.parkY, v.parkZ, v.parkHeading, at, Double.NaN, v.rise, v.riseOverLine);
        double[] box = groupBox(v, move);
        if (!v.settle.feed(box != null && runningWorld(level, box))) return;
        if (now >= v.retryAt) comeBack(level, v, at, now);
    }

    private void reportParked(ServerLevel level, Vehicle v) {
        UUID path = v.pathway == null ? new UUID(0, 0) : v.pathway;
        boolean stranded = v.mode == Mode.STRANDED;
        PathwayTraffic.report(level, new RouteTrack(v.console, path, cut(v.name, 48), RouteRunner.State.PAUSED.ordinal(),
                cut((stranded ? "Parked: " : "Travelling unseen: unassigned, waiting to return") + v.why, 96),
                stranded ? v.parkX : v.atX, stranded ? v.parkY : v.atY, stranded ? v.parkZ : v.atZ,
                (float) (stranded ? v.parkHeading : v.atHeading), 0, (float) v.s, -1, -1, -1, "", 0, -1,
                (float) v.front, (float) v.rear, 0, 0, v.owner == null ? new UUID(0, 0) : v.owner, 0, (float) v.half));
    }

    private void comeBack(ServerLevel level, Vehicle v, UnseenTravel.Place at, long now) {
        double ground = groundTop(level, at.x(), at.y() + 1.25, at.z());
        RigidMove move = UnseenTravel.move(v.parkX, v.parkY, v.parkZ, v.parkHeading, at, ground, v.rise, v.riseOverLine);
        Clearance blocked = blockedAt(level, v, move);
        v.returnBlocked = blocked.road();
        v.ghost.obstruction(blocked.road() ? new RouteSensors.Obstacle(0, false, blocked.reason()) : RouteSensors.Obstacle.NONE);
        if (!blocked.reason().isEmpty()) {
            v.why = blocked.reason();
            v.retryAt = now + 10;
            if (blocked.road()) v.ghost.holdForReturn();
            setDirty();
            sayOnce(level, v, v.name + " cannot come back yet: " + blocked.reason());
            return;
        }
        if (v.holding == null) {
            strand(level, v, now, "where it was parked is not known");
            return;
        }
        if (RouteParking.record(level, v.craft) == null) RouteParking.loadHolding(level, v.holding);
        RouteParking.Record parkedRecord = RouteParking.record(level, v.craft);
        CompoundTag console = parkedRecord == null ? null : RouteParking.consoleTag(parkedRecord.tag(), v.console);
        if (console == null) { strand(level, v, now, "its parked console is missing at return"); return; }
        v.ghostRoute = v.ghost.save();
        CompoundTag returned = v.ghostRoute.copy(); returned.putBoolean("ReturnResume", true);

        console.put("Route", returned);
        setDirty();
        String result = RouteParking.restore(level, v.holding, v.craft, v.moving, move);
        if (!result.isEmpty()) {
            boolean intact = true;
            for (UUID id : v.moving) intact &= !RouteParking.live(level, id) && RouteParking.record(level, id) != null;
            if (!intact) {
                failedReturn(level, v, at, now, "Sable interrupted its assembly return: " + result);
                return;
            }
            v.why = result;
            v.retryAt = now + 20;
            sayOnce(level, v, v.name + " cannot come back yet: " + result);
            return;
        }
        for (UUID id : v.moving) {
            if (!RouteParking.live(level, id)) {
                failedReturn(level, v, at, now, "Sable restored only part of its assembly"); return;
            }
        }
        v.ghost.route().unloaded(level); v.ghost = null;
        for (UUID id : v.moving) RouteParking.wake(level, id);
        unpark(v);
        v.mode = Mode.REAL;
        v.since = now;
        v.why = "";
        v.lastWhy = "";
        v.returnBlocked = false;
        v.reportedAt = now;
        setDirty();
    }

    private void failedReturn(ServerLevel level, Vehicle v, UnseenTravel.Place at, long now, String why) {
        v.ghost.route().pause(level, why);
        v.ghostRoute = v.ghost.save();
        for (UUID id : v.moving) {
            RouteParking.pauseLoadedConsole(level, id, v.console, why);
            RouteParking.Record record = RouteParking.record(level, id);
            CompoundTag console = record == null ? null : RouteParking.consoleTag(record.tag(), v.console);
            if (console != null) {
                CompoundTag stopped = v.ghostRoute.copy(); stopped.putBoolean("ReturnResume", true);
                console.put("Route", stopped);
            }
        }
        v.parkX = at.x(); v.parkY = at.y(); v.parkZ = at.z(); v.parkHeading = at.heading();
        v.state = RouteRunner.State.PAUSED.ordinal(); v.speed = 0;
        strand(level, v, now, why);
    }

    private void strand(ServerLevel level, Vehicle v, long now, String why) {
        if (v.ghost != null) { v.ghost.route().unloaded(level); v.ghost = null; }
        unpark(v);
        v.mode = Mode.STRANDED;
        v.since = now;
        v.why = why;
        setDirty();
        say(level, v, v.name + " is parked: " + why, false);
    }

    private void unpark(Vehicle v) {
        for (UUID id : v.chain) parked.remove(id);

        for (Vehicle o : vehicles.values()) if (o != v && o.mode == Mode.UNSEEN) parked.addAll(o.chain);
    }

    @Nullable
    private static double[] groupBox(Vehicle v, RigidMove move) {
        double[] out = null;
        for (int i = 0; i < v.chain.size() && i < v.chainBounds.size(); i++) {
            double[] b = v.chainBounds.get(i);
            double[] m = v.moving.contains(v.chain.get(i)) ? move.box(b[0], b[1], b[2], b[3], b[4], b[5]) : b;
            if (out == null) {
                out = m.clone();
            } else {
                for (int a = 0; a < 3; a++) {
                    out[a] = Math.min(out[a], m[a]);
                    out[a + 3] = Math.max(out[a + 3], m[a + 3]);
                }
            }
        }
        return out;
    }

    // block ticking chunks only, sable only keeps bodies there. lookups only, never loads. sable tax #15
    private static boolean runningWorld(ServerLevel level, double[] box) {
        int[] span = UnseenTravel.chunkSpan(box, UnseenTravel.MARGIN);
        int y = (int) Math.floor((box[1] + box[4]) * 0.5);
        for (int cx = span[0]; cx <= span[2]; cx++) {
            for (int cz = span[1]; cz <= span[3]; cz++) {
                if (!level.shouldTickBlocksAt(ChunkPos.asLong(cx, cz))) return false;
                if (!level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, y, (cz << 4) + 8))) return false;
            }
        }
        return true;
    }

    public static double groundTop(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = (int) Math.floor(y);
        for (int by = top; by >= top - 6; by--) {
            pos.set(bx, by, bz);
            if (!level.isLoaded(pos)) return Double.NaN;
            BlockState st = level.getBlockState(pos);
            VoxelShape shape = st.getCollisionShape(level, pos);
            if (!shape.isEmpty()) return by + shape.max(net.minecraft.core.Direction.Axis.Y);
        }
        return Double.NaN;
    }

    private record Clearance(String reason, boolean road) {
        static final Clearance CLEAR = new Clearance("", false);
    }

    private static Clearance blockedAt(ServerLevel level, Vehicle v, RigidMove move) {
        double[] ref = move.point(v.parkX, v.parkY, v.parkZ);
        double heading = move.heading(v.parkHeading);
        Clearance hostileCollision = Clearance.CLEAR;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < v.chain.size() && i < v.chainBounds.size(); i++) {
            if (!v.moving.contains(v.chain.get(i))) continue;
            double[] old = v.chainBounds.get(i);
            double[] b = move.box(old[0], old[1], old[2], old[3], old[4], old[5]);
            b[1] = Math.max(b[1], ref[1] + 0.5);
            if (b[4] <= b[1]) continue;
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(b[0], b[1], b[2], b[3], b[4], b[5]).deflate(0.03);
            for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
                for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                    for (int y = (int) Math.floor(box.minY); y < Math.ceil(box.maxY); y++) {
                        pos.set(x, y, z);
                        if (!level.isLoaded(pos)) return new Clearance("the whole assembly's place is not loaded", false);
                        BlockState st = level.getBlockState(pos);
                        VoxelShape shape = st.getCollisionShape(level, pos);
                        for (net.minecraft.world.phys.AABB local : shape.toAabbs()) {
                            net.minecraft.world.phys.AABB solid = local.move(pos);
                            if (!solid.intersects(box)) continue;
                            double[] hit = {solid.minX, solid.minY, solid.minZ, solid.maxX, solid.maxY, solid.maxZ};
                            return new Clearance(st.getBlock().getName().getString().toLowerCase(Locale.ROOT)
                                    + " where its assembly would stand (" + x + ", " + y + ", " + z + ")",
                                    UnseenTravel.onRoad(hit, ref[0], ref[2], heading, v.front, v.rear));
                        }
                    }
                }
            }
            for (Object body : com.createstats.flight.bridge.SableBridge.listSubLevels(level)) {
                UUID id = com.createstats.flight.bridge.SableBridge.subLevelId(body);
                if (id != null && v.moving.contains(id)) continue;
                double[] other = com.createstats.vccmk4.route.RouteReflect.worldBounds(body);
                if (other != null && UnseenTravel.intersects(b, other)) {
                    return new Clearance("another vehicle where its assembly would stand",
                            UnseenTravel.onRoad(other, ref[0], ref[2], heading, v.front, v.rear));
                }
            }
            var entities = level.getEntities((net.minecraft.world.entity.Entity) null, box,
                    e -> e.isAlive() && !e.isSpectator());
            if (!entities.isEmpty()) {
                var entity = entities.getFirst();

                for (var candidate : entities) {
                    if (RouteSensors.isHostile(candidate)) continue;
                    entity = candidate;
                    var cb = candidate.getBoundingBox();
                    if (UnseenTravel.onRoad(new double[]{cb.minX, cb.minY, cb.minZ, cb.maxX, cb.maxY, cb.maxZ},
                            ref[0], ref[2], heading, v.front, v.rear)) break;
                }
                var eb = entity.getBoundingBox();
                String reason = entity.getName().getString() + " where its assembly would stand";
                if (!RouteSensors.isHostile(entity)) {
                    return new Clearance(reason, UnseenTravel.onRoad(
                            new double[]{eb.minX, eb.minY, eb.minZ, eb.maxX, eb.maxY, eb.maxZ},
                            ref[0], ref[2], heading, v.front, v.rear));
                }

                hostileCollision = new Clearance(reason, false);
            }
        }
        return hostileCollision;
    }

    private static void say(ServerLevel level, Vehicle v, String line, boolean good) {
        CreateStats.LOGGER.info("[Unseen routes] {}", line);
        if (v.pathway != null) RouteEvents.add(level, v.pathway, line);
        ServerPlayer owner = v.owner == null ? null : level.getServer().getPlayerList().getPlayer(v.owner);
        if (owner != null) owner.displayClientMessage(Component.literal(line).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
    }

    private static void sayOnce(ServerLevel level, Vehicle v, String line) {
        if (line.equals(v.lastWhy)) return;
        v.lastWhy = line;
        say(level, v, line, false);
    }

    private static String cut(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }

    private static UnseenRoutes load(CompoundTag tag, HolderLookup.Provider registries) {
        UnseenRoutes u = new UnseenRoutes();
        ListTag list = tag.getList("Vehicles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("Console")) continue;
            try {
                Vehicle v = new Vehicle(c.getUUID("Console"));
                v.craft = c.hasUUID("Craft") ? c.getUUID("Craft") : null;
                v.pathway = c.hasUUID("Pathway") ? c.getUUID("Pathway") : null;
                v.name = c.getString("Name");
                v.owner = c.hasUUID("Owner") ? c.getUUID("Owner") : null;
                v.bodies = uuids(c.getList("Bodies", Tag.TAG_INT_ARRAY));
                v.mode = Mode.valueOf(c.getString("Mode"));
                v.state = c.getInt("State");
                v.s = c.getDouble("S");
                v.speed = c.getDouble("Speed");
                v.planned = c.getDouble("Planned");
                v.refLocal = doubles(c.getList("RefLocal", Tag.TAG_DOUBLE), 3, v.refLocal);
                v.noseLocal = doubles(c.getList("NoseLocal", Tag.TAG_DOUBLE), 3, v.noseLocal);
                v.rise = c.contains("Rise") ? c.getDouble("Rise") : Double.NaN;
                v.riseOverLine = c.getDouble("RiseOverLine");
                v.front = c.getDouble("Front");
                v.rear = c.getDouble("Rear");
                v.half = c.getDouble("Half");
                v.bodyPositions = boxes(c.getList("BodyPositions", Tag.TAG_LIST), 3);
                if (c.contains("HoldingX")) v.holding = new ChunkPos(c.getInt("HoldingX"), c.getInt("HoldingZ"));
                v.chain = uuids(c.getList("Chain", Tag.TAG_INT_ARRAY));
                v.chainBounds = boxes(c.getList("ChainBounds", Tag.TAG_LIST), 6);
                v.moving = new HashSet<>(uuids(c.getList("Moving", Tag.TAG_INT_ARRAY)));
                v.parkX = c.getDouble("ParkX");
                v.parkY = c.getDouble("ParkY");
                v.parkZ = c.getDouble("ParkZ");
                v.parkHeading = c.getDouble("ParkHeading");
                v.why = c.getString("Why");
                v.since = c.getLong("Since");
                if (c.contains("Driver")) v.driver = c.getCompound("Driver").copy();
                if (c.contains("Cargo")) v.cargo = TravelNbt.cargo(c.getCompound("Cargo"));
                v.ghostRoute = c.getCompound("GhostRoute").copy();
                v.atX = c.contains("AtX") ? c.getDouble("AtX") : v.parkX;
                v.atY = c.contains("AtY") ? c.getDouble("AtY") : v.parkY;
                v.atZ = c.contains("AtZ") ? c.getDouble("AtZ") : v.parkZ;
                v.atHeading = c.contains("AtHeading") ? c.getDouble("AtHeading") : v.parkHeading;
                v.returnBlocked = c.getBoolean("ReturnBlocked");
                u.vehicles.put(v.console, v);
                if (v.mode == Mode.UNSEEN) u.parked.addAll(v.chain);
            } catch (RuntimeException e) {
                CreateStats.LOGGER.warn("[Unseen routes] a saved vehicle could not be read and was skipped: {}", e.toString());
            }
        }
        return u;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Vehicle v : vehicles.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Console", v.console);
            if (v.craft != null) c.putUUID("Craft", v.craft);
            if (v.pathway != null) c.putUUID("Pathway", v.pathway);
            c.putString("Name", v.name);
            if (v.owner != null) c.putUUID("Owner", v.owner);
            c.put("Bodies", uuidList(v.bodies));
            c.putString("Mode", v.mode.name());
            c.putInt("State", v.state);
            c.putDouble("S", v.s);
            c.putDouble("Speed", v.speed);
            c.putDouble("Planned", v.planned);
            c.put("RefLocal", doubleList(v.refLocal));
            c.put("NoseLocal", doubleList(v.noseLocal));
            if (Double.isFinite(v.rise)) c.putDouble("Rise", v.rise);
            c.putDouble("RiseOverLine", v.riseOverLine);
            c.putDouble("Front", v.front);
            c.putDouble("Rear", v.rear);
            c.putDouble("Half", v.half);
            ListTag positions = new ListTag();
            for (double[] p : v.bodyPositions) positions.add(doubleList(p));
            c.put("BodyPositions", positions);
            if (v.holding != null) {
                c.putInt("HoldingX", v.holding.x);
                c.putInt("HoldingZ", v.holding.z);
            }
            c.put("Chain", uuidList(v.chain));
            ListTag bounds = new ListTag();
            for (double[] b : v.chainBounds) bounds.add(doubleList(b));
            c.put("ChainBounds", bounds);
            c.put("Moving", uuidList(new ArrayList<>(v.moving)));
            c.putDouble("ParkX", v.parkX);
            c.putDouble("ParkY", v.parkY);
            c.putDouble("ParkZ", v.parkZ);
            c.putDouble("ParkHeading", v.parkHeading);
            c.putString("Why", v.why);
            c.putLong("Since", v.since);
            c.put("Driver", v.driver.copy()); c.put("Cargo", TravelNbt.cargo(v.cargo));
            c.put("GhostRoute", v.ghost == null ? v.ghostRoute.copy() : v.ghost.save());
            c.putDouble("AtX", v.atX); c.putDouble("AtY", v.atY); c.putDouble("AtZ", v.atZ); c.putDouble("AtHeading", v.atHeading);
            c.putBoolean("ReturnBlocked", v.returnBlocked);
            list.add(c);
        }
        tag.put("Vehicles", list);
        return tag;
    }

    private static ListTag uuidList(List<UUID> ids) {
        ListTag out = new ListTag();
        for (UUID id : ids) out.add(NbtUtils.createUUID(id));
        return out;
    }

    private static List<UUID> uuids(ListTag list) {
        List<UUID> out = new ArrayList<>();
        for (Tag t : list) out.add(NbtUtils.loadUUID(t));
        return out;
    }

    private static ListTag doubleList(double[] d) {
        ListTag out = new ListTag();
        for (double x : d) out.add(net.minecraft.nbt.DoubleTag.valueOf(x));
        return out;
    }

    private static double[] doubles(ListTag list, int n, double[] fallback) {
        if (list.size() < n) return fallback;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = list.getDouble(i);
        return out;
    }

    private static List<double[]> boxes(ListTag list, int n) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ListTag l = list.getList(i);
            if (l.size() >= n) out.add(doubles(l, n, new double[n]));
        }
        return out;
    }
}
