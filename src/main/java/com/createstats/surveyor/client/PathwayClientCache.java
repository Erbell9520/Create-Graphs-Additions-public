package com.createstats.surveyor.client;

import com.createstats.CreateStats;
import com.createstats.surveyor.PathwayChecks;
import com.createstats.surveyor.RouteEvents;
import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.core.PathHealth;
import com.createstats.surveyor.core.PathSpline;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.PathwayCodec;
import com.createstats.surveyor.core.SegmentCheck;
import com.createstats.surveyor.core.SpeedPlan;
import com.createstats.surveyor.network.PathwayHealthPacket;
import com.createstats.surveyor.network.PathwayTerrainPacket;
import com.createstats.surveyor.network.PathwaySyncPacket;
import com.createstats.surveyor.network.RouteTrackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PathwayClientCache {
    public static final int GROUND_TICKS = 40;

    public static final double LOOK_RANGE = 160.0;

    public static final class Entry {
        public final Pathway pathway;
        public final String owner;
        private PathSpline spline;
        private SpeedPlan plan;

        private double[] flow;
        private PathwayChecks.Result checks;
        private long checksTick = Long.MIN_VALUE;
        private double checksRadius = -1.0;
        private double checksHalf = -1.0;

        Entry(Pathway pathway, String owner) {
            this.pathway = pathway;
            this.owner = owner;
        }

        public PathSpline spline() {
            if (spline == null) spline = PathSpline.of(pathway);
            return spline;
        }

        public SpeedPlan plan() {
            if (plan == null) plan = SpeedPlan.of(spline(), pathway);
            return plan;
        }

        public double[] flow() {
            if (flow == null) {
                PathSpline sp = spline();
                SpeedPlan pl = plan();
                double[] f = new double[sp.sampleCount()];
                for (int i = 1; i < f.length; i++) {
                    double v = Math.max(0.3, 0.5 * (pl.sample(i) + pl.sample(i - 1)));
                    f[i] = f[i - 1] + sp.step() / v;
                }
                flow = f;
            }
            return flow;
        }

        @Nullable
        public PathwayChecks.Result checks(Level level, double cx, double cz, long tick) {
            double radius = 0.0;
            double half = 0.0;
            for (Vehicle v : vehiclesOn(pathway.id())) {
                if (v.now == null) continue;
                if (v.now.minRadius() > 0.5f) radius = Math.max(radius, v.now.minRadius());
                if (v.now.halfWidth() > 0.4f) half = Math.max(half, v.now.halfWidth());
            }
            if (radius <= 0.0) radius = SegmentCheck.DEFAULT_RADIUS;
            if (half <= 0.0) half = 1.5;
            if (checks == null || tick - checksTick >= GROUND_TICKS || tick < checksTick || radius != checksRadius || half != checksHalf) {
                try {
                    checks = PathwayChecks.check(level, pathway, spline(), radius, half, cx, cz, LOOK_RANGE);
                } catch (RuntimeException e) {
                    CreateStats.LOGGER.debug("[Pathways] checks failed for {}: {}", pathway.name(), e.toString());
                }
                checksTick = tick;
                checksRadius = radius;
                checksHalf = half;
            }
            return checks;
        }

        @Nullable
        public PathwayChecks.Result lastChecks() {
            return checks;
        }

        public SegmentCheck.Level worst() {
            if (checks == null) return SegmentCheck.Level.FINE;
            return SegmentCheck.worst(checks.findings());
        }
    }

    static final int TRAIL_MAX = 720;

    static final int TRAIL_EVERY = 20;

    public record Trail(long[] time, float[] s, int[] laps) {
    }

    public static final class Vehicle {
        public RouteTrack prev;
        public RouteTrack now;
        public long prevTime;
        public long nowTime;
        @Nullable private UUID trailPathway;
        private final long[] trailTime = new long[TRAIL_MAX];
        private final float[] trailS = new float[TRAIL_MAX];
        private final int[] trailLaps = new int[TRAIL_MAX];
        private int trailStart;
        private int trailCount;

        void sample(long serverTime, RouteTrack t) {
            if (!t.pathway().equals(trailPathway)) {
                trailPathway = t.pathway();
                trailCount = 0;
                trailStart = 0;
            }
            if (trailCount > 0) {
                long last = trailTime[(trailStart + trailCount - 1) % TRAIL_MAX];
                if (serverTime - last < TRAIL_EVERY && serverTime >= last) return;

                if (serverTime < last) trailCount = 0;
            }
            int at;
            if (trailCount < TRAIL_MAX) {
                at = (trailStart + trailCount) % TRAIL_MAX;
                trailCount++;
            } else {
                at = trailStart;
                trailStart = (trailStart + 1) % TRAIL_MAX;
            }
            trailTime[at] = serverTime;
            trailS[at] = t.s();
            trailLaps[at] = t.laps();
        }

        public Trail trail() {
            long[] time = new long[trailCount];
            float[] s = new float[trailCount];
            int[] laps = new int[trailCount];
            for (int i = 0; i < trailCount; i++) {
                int k = (trailStart + i) % TRAIL_MAX;
                time[i] = trailTime[k];
                s[i] = trailS[k];
                laps[i] = trailLaps[k];
            }
            return new Trail(time, s, laps);
        }

        public double[] at(double t) {
            if (prev == null || nowTime <= prevTime) return new double[]{now.x(), now.y(), now.z(), now.heading()};
            double f = Math.max(0.0, Math.min(1.0, (t - nowTime) / (double) (nowTime - prevTime) + 1.0));
            double h = prev.heading() + PathSpline.wrapAngle(now.heading() - prev.heading()) * f;
            return new double[]{prev.x() + (now.x() - prev.x()) * f, prev.y() + (now.y() - prev.y()) * f,
                    prev.z() + (now.z() - prev.z()) * f, h};
        }
    }

    public record Health(long version, List<PathHealth.Item> items) {
    }

    private static final Map<UUID, Entry> PATHWAYS = new LinkedHashMap<>();
    private static final Map<UUID, Health> HEALTH = new java.util.HashMap<>();

    private static final Map<UUID, PathwayTerrainPacket> TERRAIN = new java.util.HashMap<>();
    private static final Map<UUID, Vehicle> VEHICLES = new LinkedHashMap<>();
    private static List<RouteEvents.Line> log = List.of();
    private static long serverTime;
    private static long receivedAt;
    private static int generation;

    private PathwayClientCache() {
    }

    public static void accept(PathwaySyncPacket p) {
        if (p.full()) PATHWAYS.clear();
        for (int i = 0; i < p.pathways().size(); i++) {
            try {
                Pathway path = PathwayCodec.decode(p.pathways().get(i));
                Entry old = PATHWAYS.get(path.id());
                if (old != null && old.pathway.version() > path.version() && !p.full()) continue;
                PATHWAYS.put(path.id(), new Entry(path, i < p.owners().size() ? p.owners().get(i) : ""));
            } catch (Exception e) {
                CreateStats.LOGGER.warn("[Pathways] a pathway from the server could not be read: {}", e.toString());
            }
        }
        for (UUID id : p.removed()) {
            PATHWAYS.remove(id);
            HEALTH.remove(id);
            TERRAIN.remove(id);
            if (id.equals(StaffInput.selected())) StaffInput.select(null);
        }
        if (p.select().getMostSignificantBits() != 0 || p.select().getLeastSignificantBits() != 0) StaffInput.select(p.select());
        generation++;
    }

    public static void accept(PathwayTerrainPacket p) {
        if (p.w() * p.h() != p.colours().length) return;
        TERRAIN.put(p.pathway(), p);
        generation++;
    }

    public static Collection<PathwayTerrainPacket> terrain() {
        return Collections.unmodifiableCollection(TERRAIN.values());
    }

    public static void accept(PathwayHealthPacket p) {
        try {
            HEALTH.put(p.pathway(), new Health(p.version(), PathHealth.decode(p.items())));
        } catch (Exception e) {
            CreateStats.LOGGER.warn("[Pathways] a pathway's health from the server could not be read: {}", e.toString());
        }
        generation++;
    }

    public static List<PathHealth.Item> health(UUID pathway) {
        Health h = HEALTH.get(pathway);
        Entry e = PATHWAYS.get(pathway);
        if (h == null || e == null || h.version() != e.pathway.version()) return List.of();
        return h.items();
    }

    public static boolean healthKnown(UUID pathway) {
        Health h = HEALTH.get(pathway);
        Entry e = PATHWAYS.get(pathway);
        return h != null && e != null && h.version() == e.pathway.version();
    }

    public static void accept(RouteTrackPacket p) {
        long now = clientTime();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (RouteTrack t : p.tracks()) {
            seen.add(t.console());
            Vehicle v = VEHICLES.computeIfAbsent(t.console(), k -> new Vehicle());
            v.prev = v.now;
            v.prevTime = v.nowTime;
            v.now = t;
            v.nowTime = now;
            v.sample(p.time(), t);
        }
        VEHICLES.keySet().retainAll(seen);
        if (p.logChanged()) log = List.copyOf(p.log());
        serverTime = p.time();
        receivedAt = now;
    }

    public static void clear() {
        PATHWAYS.clear();
        HEALTH.clear();
        TERRAIN.clear();
        VEHICLES.clear();
        log = List.of();
        generation++;
    }

    public static Collection<Entry> all() {
        return Collections.unmodifiableCollection(PATHWAYS.values());
    }

    @Nullable
    public static Entry get(@Nullable UUID id) {
        return id == null ? null : PATHWAYS.get(id);
    }

    public static Collection<Vehicle> vehicles() {
        return Collections.unmodifiableCollection(VEHICLES.values());
    }

    @Nullable
    public static Vehicle vehicle(UUID console) {
        return VEHICLES.get(console);
    }

    public static List<Vehicle> vehiclesOn(UUID pathway) {
        List<Vehicle> out = new ArrayList<>();
        for (Vehicle v : VEHICLES.values()) if (v.now != null && v.now.pathway().equals(pathway)) out.add(v);
        return out;
    }

    public static List<RouteEvents.Line> log() {
        return log;
    }

    public static long serverTime() {
        return serverTime + Math.max(0L, clientTime() - receivedAt);
    }

    public static int generation() {
        return generation;
    }

    static long clientTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }
}
