package com.createstats.train.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class RailSnapshot {
    public static final RailSnapshot EMPTY = new RailSnapshot();

    public long serverTick;

    public int topologyChecksum;

    private final List<Station> stations = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private final List<TrainState> trains = new ArrayList<>();
    private final List<Arrival> arrivals = new ArrayList<>();

    public boolean tracksTruncated;

    public List<Station> stations() {
        return this.stations;
    }

    public List<Link> links() {
        return this.links;
    }

    public List<Track> tracks() {
        return this.tracks;
    }

    public List<TrainState> trains() {
        return this.trains;
    }

    public List<Arrival> arrivals() {
        return this.arrivals;
    }

    public boolean isEmpty() {
        return this.stations.isEmpty() && this.trains.isEmpty();
    }

    public Station station(UUID id) {
        if (id != null) {
            for (final Station s : this.stations) {
                if (id.equals(s.id)) {
                    return s;
                }
            }
        }
        return null;
    }

    public TrainState train(UUID id) {
        if (id != null) {
            for (final TrainState t : this.trains) {
                if (id.equals(t.id)) {
                    return t;
                }
            }
        }
        return null;
    }

    public Station resolveFilter(String filter, String preferredDimension) {
        if (filter == null || filter.isEmpty()) {
            return null;
        }
        Station fallback = null;
        for (final Station s : this.stations) {
            if (!matches(filter, s.name)) {
                continue;
            }
            if (s.dimension.equals(preferredDimension)) {
                return s;
            }
            if (fallback == null) {
                fallback = s;
            }
        }
        return fallback;
    }

    public static boolean matches(String filter, String name) {
        if (filter == null || name == null) {
            return false;
        }
        if (filter.equals(name)) {
            return true;
        }
        if (filter.startsWith("*") && name.endsWith(filter.substring(1))) {
            return true;
        }
        return filter.endsWith("*") && name.startsWith(filter.substring(0, filter.length() - 1));
    }

    public Set<String> dimensions() {
        final LinkedHashSet<String> dims = new LinkedHashSet<>();
        for (final Station s : this.stations) {
            if (!s.dimension.isEmpty()) {
                dims.add(s.dimension);
            }
        }
        for (final TrainState t : this.trains) {
            if (!t.dimension.isEmpty()) {
                dims.add(t.dimension);
            }
        }
        return Collections.unmodifiableSet(dims);
    }

    public RailSnapshot inDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return this;
        }
        final RailSnapshot out = new RailSnapshot();
        out.serverTick = this.serverTick;
        out.topologyChecksum = this.topologyChecksum;
        for (final Station s : this.stations) {
            if (dimension.equals(s.dimension)) {
                out.stations.add(s);
            }
        }
        for (final TrainState t : this.trains) {
            if (dimension.equals(t.dimension)) {
                out.trains.add(t);
            }
        }
        for (final Link l : this.links) {
            if (out.station(l.from) != null && out.station(l.to) != null) {
                out.links.add(l);
            }
        }
        for (final Track t : this.tracks) {
            if (dimension.equals(t.dimension)) {
                out.tracks.add(t);
            }
        }
        out.tracksTruncated = this.tracksTruncated;
        for (final Arrival a : this.arrivals) {
            if (out.train(a.trainId) != null) {
                out.arrivals.add(a);
            }
        }
        return out;
    }

    public static final class Station {
        public UUID id;
        public String name = "";
        public String dimension = "";
        public double x;
        public double y;
        public double z;
        public boolean assembling;

        public UUID presentTrain;

        public UUID imminentTrain;

        public int packagePorts;

        CompoundTag write() {
            final CompoundTag t = new CompoundTag();
            t.putUUID("id", this.id);
            t.putString("n", this.name);
            t.putString("d", this.dimension);
            t.putDouble("x", this.x);
            t.putDouble("y", this.y);
            t.putDouble("z", this.z);
            t.putBoolean("a", this.assembling);
            putUUID(t, "p", this.presentTrain);
            putUUID(t, "i", this.imminentTrain);
            t.putInt("pp", this.packagePorts);
            return t;
        }

        static Station read(CompoundTag t) {
            final Station s = new Station();
            s.id = t.getUUID("id");
            s.name = t.getString("n");
            s.dimension = t.getString("d");
            s.x = t.getDouble("x");
            s.y = t.getDouble("y");
            s.z = t.getDouble("z");
            s.assembling = t.getBoolean("a");
            s.presentTrain = getUUID(t, "p");
            s.imminentTrain = getUUID(t, "i");
            s.packagePorts = t.getInt("pp");
            return s;
        }
    }

    public static final class Link {
        public UUID from;
        public UUID to;

        public double length;

        public float[] path = new float[0];

        public boolean hasPath() {
            return this.path.length >= 4;
        }

        CompoundTag write() {
            final CompoundTag t = new CompoundTag();
            t.putUUID("f", this.from);
            t.putUUID("t", this.to);
            t.putDouble("l", this.length);
            if (this.path.length > 0) {
                final int[] packed = new int[this.path.length];
                for (int i = 0; i < this.path.length; i++) {
                    packed[i] = Float.floatToRawIntBits(this.path[i]);
                }
                t.putIntArray("p", packed);
            }
            return t;
        }

        static Link read(CompoundTag t) {
            final Link l = new Link();
            l.from = t.getUUID("f");
            l.to = t.getUUID("t");
            l.length = t.getDouble("l");
            if (t.contains("p")) {
                final int[] packed = t.getIntArray("p");
                l.path = new float[packed.length];
                for (int i = 0; i < packed.length; i++) {
                    l.path[i] = Float.intBitsToFloat(packed[i]);
                }
            }
            return l;
        }
    }

    public static final class Track {
        public String dimension = "";

        public float[] path = new float[0];

        private float[] bounds;

        public boolean valid() {
            return this.path.length >= 4;
        }

        public float[] bounds() {
            if (this.bounds == null) {
                float minX = Float.MAX_VALUE;
                float minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE;
                float maxZ = -Float.MAX_VALUE;
                for (int i = 0; i < this.path.length; i += 2) {
                    minX = Math.min(minX, this.path[i]);
                    maxX = Math.max(maxX, this.path[i]);
                    minZ = Math.min(minZ, this.path[i + 1]);
                    maxZ = Math.max(maxZ, this.path[i + 1]);
                }
                this.bounds = new float[]{minX, minZ, maxX, maxZ};
            }
            return this.bounds;
        }
    }

    private static final class TrackCodec {
        private static final float UNITS = 4.0f;

        static ListTag write(List<Track> tracks) {
            final ListTag out = new ListTag();
            final java.util.LinkedHashMap<String, List<Track>> byDimension =
                    new java.util.LinkedHashMap<>();
            for (final Track t : tracks) {
                if (t.valid()) {
                    byDimension.computeIfAbsent(t.dimension, k -> new ArrayList<>()).add(t);
                }
            }
            for (final java.util.Map.Entry<String, List<Track>> e : byDimension.entrySet()) {
                final CompoundTag tag = new CompoundTag();
                tag.putString("d", e.getKey());
                tag.putByteArray("p", pack(e.getValue()));
                out.add(tag);
            }
            return out;
        }

        static void read(ListTag list, List<Track> into) {
            for (int i = 0; i < list.size(); i++) {
                final CompoundTag tag = list.getCompound(i);
                unpack(tag.getString("d"), tag.getByteArray("p"), into);
            }
        }

        private static byte[] pack(List<Track> tracks) {
            final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1024);
            varint(out, tracks.size());

            int cursorX = 0;
            int cursorZ = 0;
            for (final Track t : tracks) {
                final int points = t.path.length / 2;
                varint(out, points);
                for (int i = 0; i < points; i++) {
                    final int x = Math.round(t.path[i * 2] * UNITS);
                    final int z = Math.round(t.path[i * 2 + 1] * UNITS);
                    varint(out, zigzag(x - cursorX));
                    varint(out, zigzag(z - cursorZ));
                    cursorX = x;
                    cursorZ = z;
                }
            }
            return out.toByteArray();
        }

        private static void unpack(String dimension, byte[] data, List<Track> into) {
            final int[] cursor = {0};
            final int runs = readVarint(data, cursor);
            int x = 0;
            int z = 0;
            for (int r = 0; r < runs && cursor[0] < data.length; r++) {
                final int points = readVarint(data, cursor);
                if (points < 0 || points > 1 << 20) {
                    return;
                }
                final Track t = new Track();
                t.dimension = dimension;
                t.path = new float[points * 2];
                for (int i = 0; i < points; i++) {
                    x += unzigzag(readVarint(data, cursor));
                    z += unzigzag(readVarint(data, cursor));
                    t.path[i * 2] = x / UNITS;
                    t.path[i * 2 + 1] = z / UNITS;
                }
                if (t.valid()) {
                    into.add(t);
                }
            }
        }

        private static int zigzag(int v) {
            return (v << 1) ^ (v >> 31);
        }

        private static int unzigzag(int v) {
            return (v >>> 1) ^ -(v & 1);
        }

        private static void varint(java.io.ByteArrayOutputStream out, int value) {
            int v = value;
            while ((v & ~0x7F) != 0) {
                out.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }

        private static int readVarint(byte[] data, int[] cursor) {
            int result = 0;
            int shift = 0;
            while (cursor[0] < data.length && shift < 35) {
                final int b = data[cursor[0]++] & 0xFF;
                result |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
            return result;
        }
    }

    public enum SchedulePhase {
        NONE, PRE_TRANSIT, IN_TRANSIT, POST_TRANSIT;

        public static SchedulePhase of(int ordinal) {
            final SchedulePhase[] v = values();
            return ordinal >= 0 && ordinal < v.length ? v[ordinal] : NONE;
        }
    }

    public static final class Car {
        public int spacing;

        public boolean conductor;

        public boolean blocked;
        public boolean stalled;
        public boolean twoBogeys;

        public boolean portal;

        public int items;
        public int slots;
        public int usedSlots;
        public int fluid;
        public int fluidCapacity;

        public float fill() {
            final float byItems = this.slots <= 0 ? 0.0f : this.usedSlots / (float) this.slots;
            final float byFluid = this.fluidCapacity <= 0 ? 0.0f
                    : this.fluid / (float) this.fluidCapacity;
            return Math.max(byItems, byFluid);
        }

        public boolean carriesAnything() {
            return this.slots > 0 || this.fluidCapacity > 0;
        }

        private static final int FLAG_CONDUCTOR = 1;
        private static final int FLAG_BLOCKED = 2;
        private static final int FLAG_STALLED = 4;
        private static final int FLAG_TWO_BOGEYS = 8;
        private static final int FLAG_PORTAL = 16;

        static int[] pack(List<Car> cars) {
            final int[] out = new int[cars.size() * 6];
            for (int i = 0; i < cars.size(); i++) {
                final Car c = cars.get(i);
                int flags = 0;
                if (c.conductor) {
                    flags |= FLAG_CONDUCTOR;
                }
                if (c.blocked) {
                    flags |= FLAG_BLOCKED;
                }
                if (c.stalled) {
                    flags |= FLAG_STALLED;
                }
                if (c.twoBogeys) {
                    flags |= FLAG_TWO_BOGEYS;
                }
                if (c.portal) {
                    flags |= FLAG_PORTAL;
                }
                out[i * 6] = (Math.min(255, Math.max(0, c.spacing)) << 8) | flags;
                out[i * 6 + 1] = c.slots;
                out[i * 6 + 2] = c.usedSlots;
                out[i * 6 + 3] = c.items;
                out[i * 6 + 4] = c.fluid;
                out[i * 6 + 5] = c.fluidCapacity;
            }
            return out;
        }

        static void unpack(int[] packed, List<Car> into) {
            if (packed == null) {
                return;
            }
            for (int i = 0; i + 5 < packed.length; i += 6) {
                final Car c = new Car();
                final int head = packed[i];
                c.spacing = (head >> 8) & 0xFF;
                c.conductor = (head & FLAG_CONDUCTOR) != 0;
                c.blocked = (head & FLAG_BLOCKED) != 0;
                c.stalled = (head & FLAG_STALLED) != 0;
                c.twoBogeys = (head & FLAG_TWO_BOGEYS) != 0;
                c.portal = (head & FLAG_PORTAL) != 0;
                c.slots = packed[i + 1];
                c.usedSlots = packed[i + 2];
                c.items = packed[i + 3];
                c.fluid = packed[i + 4];
                c.fluidCapacity = packed[i + 5];
                into.add(c);
            }
        }
    }

    public static final class TrainState {
        public UUID id;
        public String name = "";
        public String owner = "";
        public String dimension = "";

        public double x;
        public double z;

        public boolean positionValid;

        public float speed;
        public float throttle = 1.0f;
        public float maxSpeed;
        public int carriages;

        public boolean derailed;

        public int fuelTicks;

        public boolean navigationOk = true;
        public boolean trackOk = true;
        public boolean conductorOk = true;

        public UUID currentStation;
        public UUID destinationStation;
        public String destinationName = "";

        public double distanceToDestination;

        public double distanceStartedAt;

        public boolean waitingForSignal;
        public int ticksWaitingForSignal;
        public double distanceToSignal;

        public UUID blockedByTrain;

        public String blockedAt = "";

        public boolean hasSchedule;
        public boolean schedulePaused;
        public boolean scheduleCompleted;
        public int currentEntry;
        public SchedulePhase phase = SchedulePhase.NONE;
        public String scheduleTitle = "";

        public String waitingStatus = "";
        public List<String> stops = new ArrayList<>();

        public int itemCount;
        public int fluidAmount;
        public String topItem = "";
        public String topFluid = "";

        public int occupiedBlocks;
        public int reservedBlocks;

        public int feStored;

        public int feCapacity;

        public long feReadTick;

        public int railState;

        public int railFlow;

        public String iconId = "";

        public int mapColor = -1;

        public boolean doubleEnded;

        public boolean backwards;

        public boolean manual;

        public float targetSpeed;

        public float length;

        public final List<Car> cars = new ArrayList<>();

        public int ticksInTransit;

        public int legPlannedTicks = -1;

        public float secondsLate() {
            if (this.legPlannedTicks <= 0 || this.phase != SchedulePhase.IN_TRANSIT) {
                return Float.NaN;
            }
            return (this.ticksInTransit - this.legPlannedTicks) / 20.0f;
        }

        public float metresPerSecond() {
            return Math.abs(this.speed) * 20.0f;
        }

        public boolean moving() {
            return Math.abs(this.speed) > 0.01f;
        }

        public boolean docked() {
            return this.currentStation != null;
        }

        public com.createstats.train.power.TrainChargeReading charge() {
            return this.feCapacity > 0
                    ? new com.createstats.train.power.TrainChargeReading(this.feStored, this.feCapacity, this.feReadTick, "")
                    : null;
        }

        public boolean unhealthy() {
            return this.derailed || !this.navigationOk || !this.trackOk || !this.conductorOk;
        }

        public float legProgress() {
            if (this.distanceStartedAt <= 0.01) {
                return 0.0f;
            }
            final double done = this.distanceStartedAt - this.distanceToDestination;
            final double f = done / this.distanceStartedAt;
            return (float) (f < 0.0 ? 0.0 : Math.min(f, 1.0));
        }

        CompoundTag write() {
            final CompoundTag t = new CompoundTag();
            t.putUUID("id", this.id);
            t.putString("n", this.name);
            t.putString("o", this.owner);
            t.putString("d", this.dimension);
            t.putDouble("x", this.x);
            t.putDouble("z", this.z);
            t.putBoolean("pv", this.positionValid);
            t.putFloat("sp", this.speed);
            t.putFloat("th", this.throttle);
            t.putFloat("ms", this.maxSpeed);
            t.putInt("cc", this.carriages);
            t.putBoolean("dr", this.derailed);
            t.putInt("ft", this.fuelTicks);
            t.putBoolean("sn", this.navigationOk);
            t.putBoolean("st", this.trackOk);
            t.putBoolean("sc", this.conductorOk);
            putUUID(t, "cs", this.currentStation);
            putUUID(t, "ds", this.destinationStation);
            t.putString("dn", this.destinationName);
            t.putDouble("dd", this.distanceToDestination);
            t.putDouble("d0", this.distanceStartedAt);
            t.putBoolean("wf", this.waitingForSignal);
            t.putInt("wt", this.ticksWaitingForSignal);
            t.putDouble("wd", this.distanceToSignal);

            putUUID(t, "bb", this.blockedByTrain);
            if (!this.blockedAt.isEmpty()) {
                t.putString("ba", this.blockedAt);
            }
            t.putBoolean("hs", this.hasSchedule);
            t.putBoolean("sp2", this.schedulePaused);
            t.putBoolean("sc2", this.scheduleCompleted);
            t.putInt("ce", this.currentEntry);
            t.putInt("ph", this.phase.ordinal());
            t.putString("tt", this.scheduleTitle);
            t.putString("ws", this.waitingStatus);
            final ListTag stopList = new ListTag();
            for (final String stop : this.stops) {
                final CompoundTag s = new CompoundTag();
                s.putString("s", stop);
                stopList.add(s);
            }
            t.put("st2", stopList);
            t.putInt("ic", this.itemCount);
            t.putInt("fa", this.fluidAmount);
            t.putString("ti", this.topItem);
            t.putString("tf", this.topFluid);
            t.putInt("ob", this.occupiedBlocks);

            if (this.feCapacity > 0) {
                t.putInt("fs", this.feStored);
                t.putInt("fc", this.feCapacity);
                t.putLong("fr", this.feReadTick);
            }

            if (this.railState != 0) {
                t.putInt("rs", this.railState);
                t.putInt("rf", this.railFlow);
            }
            t.putInt("rb", this.reservedBlocks);
            t.putString("ic2", this.iconId);
            t.putInt("mc", this.mapColor);
            t.putBoolean("de", this.doubleEnded);
            t.putBoolean("bw", this.backwards);
            t.putBoolean("mn", this.manual);
            t.putFloat("ts", this.targetSpeed);
            t.putFloat("ln", this.length);
            t.putInt("tit", this.ticksInTransit);
            t.putInt("lpt", this.legPlannedTicks);
            if (!this.cars.isEmpty()) {
                t.putIntArray("cr", Car.pack(this.cars));
            }
            return t;
        }

        static TrainState read(CompoundTag t) {
            final TrainState s = new TrainState();
            s.id = t.getUUID("id");
            s.name = t.getString("n");
            s.owner = t.getString("o");
            s.dimension = t.getString("d");
            s.x = t.getDouble("x");
            s.z = t.getDouble("z");
            s.positionValid = t.getBoolean("pv");
            s.speed = t.getFloat("sp");
            s.throttle = t.getFloat("th");
            s.maxSpeed = t.getFloat("ms");
            s.carriages = t.getInt("cc");
            s.derailed = t.getBoolean("dr");
            s.fuelTicks = t.getInt("ft");
            s.navigationOk = t.getBoolean("sn");
            s.trackOk = t.getBoolean("st");
            s.conductorOk = t.getBoolean("sc");
            s.currentStation = getUUID(t, "cs");
            s.destinationStation = getUUID(t, "ds");
            s.destinationName = t.getString("dn");
            s.distanceToDestination = t.getDouble("dd");
            s.distanceStartedAt = t.getDouble("d0");
            s.waitingForSignal = t.getBoolean("wf");
            s.ticksWaitingForSignal = t.getInt("wt");
            s.distanceToSignal = t.getDouble("wd");
            s.blockedByTrain = getUUID(t, "bb");
            s.blockedAt = t.getString("ba");
            s.hasSchedule = t.getBoolean("hs");
            s.schedulePaused = t.getBoolean("sp2");
            s.scheduleCompleted = t.getBoolean("sc2");
            s.currentEntry = t.getInt("ce");
            s.phase = SchedulePhase.of(t.getInt("ph"));
            s.scheduleTitle = t.getString("tt");
            s.waitingStatus = t.getString("ws");
            final ListTag stopList = t.getList("st2", Tag.TAG_COMPOUND);
            for (int i = 0; i < stopList.size(); i++) {
                s.stops.add(stopList.getCompound(i).getString("s"));
            }
            s.itemCount = t.getInt("ic");
            s.fluidAmount = t.getInt("fa");
            s.topItem = t.getString("ti");
            s.topFluid = t.getString("tf");
            s.occupiedBlocks = t.getInt("ob");
            s.feStored = t.getInt("fs");
            s.feCapacity = t.getInt("fc");
            s.feReadTick = t.getLong("fr");
            s.railState = t.getInt("rs");
            s.railFlow = t.getInt("rf");
            s.reservedBlocks = t.getInt("rb");
            s.iconId = t.getString("ic2");

            s.mapColor = t.contains("mc") ? t.getInt("mc") : -1;
            s.doubleEnded = t.getBoolean("de");
            s.backwards = t.getBoolean("bw");
            s.manual = t.getBoolean("mn");
            s.targetSpeed = t.getFloat("ts");
            s.length = t.getFloat("ln");
            s.ticksInTransit = t.getInt("tit");
            s.legPlannedTicks = t.contains("lpt") ? t.getInt("lpt") : -1;
            Car.unpack(t.contains("cr") ? t.getIntArray("cr") : null, s.cars);
            return s;
        }
    }

    public static final class Arrival {
        public UUID trainId;
        public String trainName = "";
        public String station = "";
        public String scheduleTitle = "";
        public int ticks;

        public boolean timed() {
            return this.ticks >= 0;
        }

        public boolean unreachable() {
            return this.ticks == -2;
        }

        public float seconds() {
            return this.ticks / 20.0f;
        }

        CompoundTag write() {
            final CompoundTag t = new CompoundTag();
            t.putUUID("id", this.trainId);
            t.putString("n", this.trainName);
            t.putString("s", this.station);
            t.putString("t", this.scheduleTitle);
            t.putInt("k", this.ticks);
            return t;
        }

        static Arrival read(CompoundTag t) {
            final Arrival a = new Arrival();
            a.trainId = t.getUUID("id");
            a.trainName = t.getString("n");
            a.station = t.getString("s");
            a.scheduleTitle = t.getString("t");
            a.ticks = t.getInt("k");
            return a;
        }
    }

    public CompoundTag write() {
        return write(true);
    }

    public CompoundTag write(boolean includeTopology) {
        final CompoundTag root = new CompoundTag();
        root.putLong("tick", this.serverTick);
        root.putInt("sum", this.topologyChecksum);
        if (includeTopology) {
            root.put("st", writeList(this.stations, Station::write));
            root.put("lk", writeList(this.links, Link::write));
            root.put("tk", TrackCodec.write(this.tracks));
            root.putBoolean("tkc", this.tracksTruncated);
        }
        root.put("tr", writeList(this.trains, TrainState::write));
        root.put("ar", writeList(this.arrivals, Arrival::write));
        return root;
    }

    public static RailSnapshot read(CompoundTag root) {
        final RailSnapshot s = new RailSnapshot();
        if (root == null) {
            return s;
        }
        s.serverTick = root.getLong("tick");
        s.topologyChecksum = root.getInt("sum");
        readList(root, "st", Station::read, s.stations);
        readList(root, "lk", Link::read, s.links);
        TrackCodec.read(root.getList("tk", Tag.TAG_COMPOUND), s.tracks);
        s.tracksTruncated = root.getBoolean("tkc");
        readList(root, "tr", TrainState::read, s.trains);
        readList(root, "ar", Arrival::read, s.arrivals);
        return s;
    }

    public void adoptTopology(RailSnapshot other) {
        if (other == null) {
            return;
        }
        this.stations.clear();
        this.stations.addAll(other.stations);
        this.links.clear();
        this.links.addAll(other.links);
        this.tracks.clear();
        this.tracks.addAll(other.tracks);
        this.tracksTruncated = other.tracksTruncated;
        this.topologyChecksum = other.topologyChecksum;
    }

    private static <T> ListTag writeList(List<T> items,
                                         java.util.function.Function<T, CompoundTag> writer) {
        final ListTag list = new ListTag();
        for (final T item : items) {
            list.add(writer.apply(item));
        }
        return list;
    }

    private static <T> void readList(CompoundTag root, String key,
                                     java.util.function.Function<CompoundTag, T> reader,
                                     List<T> into) {
        final ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            into.add(reader.apply(list.getCompound(i)));
        }
    }

    private static void putUUID(CompoundTag tag, String key, UUID value) {
        if (value != null) {
            tag.putUUID(key, value);
        }
    }

    private static UUID getUUID(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }
}
