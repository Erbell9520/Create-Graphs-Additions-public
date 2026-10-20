package com.createstats.train;

import java.util.UUID;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Collections;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TrainMapData
{
    private final List<StationEntry> stations;
    private final List<TrainEntry> trains;
    private long serverTick;

    public TrainMapData() {
        this.stations = new ArrayList<StationEntry>();
        this.trains = new ArrayList<TrainEntry>();
    }

    public List<StationEntry> getStations() {
        return this.stations;
    }

    public List<TrainEntry> getTrains() {
        return this.trains;
    }

    public long getServerTick() {
        return this.serverTick;
    }

    public void setServerTick(long tick) {
        this.serverTick = tick;
    }

    public boolean isEmpty() {
        return this.stations.isEmpty() && this.trains.isEmpty();
    }

    public Set<String> getDimensions() {
        final LinkedHashSet<String> dims = new LinkedHashSet<String>();
        for (StationEntry station : this.stations) {
            if (station.dimension != null && !station.dimension.isEmpty()) {
                dims.add(station.dimension);
            }
        }
        for (TrainEntry train : this.trains) {
            if (train.dimension != null && !train.dimension.isEmpty()) {
                dims.add(train.dimension);
            }
        }
        return Collections.unmodifiableSet((Set<? extends String>)dims);
    }

    public TrainMapData filterByDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return this;
        }
        final TrainMapData filtered = new TrainMapData();
        filtered.setServerTick(this.serverTick);
        for (StationEntry station : this.stations) {
            if (dimension.equals(station.dimension)) {
                filtered.getStations().add(station);
            }
        }
        for (TrainEntry train : this.trains) {
            if (dimension.equals(train.dimension)) {
                filtered.getTrains().add(train);
            }
        }
        return filtered;
    }

    public static boolean matchesStationFilter(String filter, String name) {
        return filter != null && name != null
            && (filter.equals(name)
            || (filter.startsWith("*") && name.endsWith(filter.substring(1)))
            || (filter.endsWith("*") && name.startsWith(filter.substring(0, filter.length() - 1))));
    }

    public CompoundTag write() {
        final CompoundTag root = new CompoundTag();
        root.putLong("tick", this.serverTick);
        final ListTag stationList = new ListTag();
        for (StationEntry s : this.stations) {
            stationList.add(s.write());
        }
        root.put("stations", (Tag)stationList);
        final ListTag trainList = new ListTag();
        for (TrainEntry t : this.trains) {
            trainList.add(t.write());
        }
        root.put("trains", (Tag)trainList);
        return root;
    }

    public static TrainMapData read(CompoundTag root) {
        final TrainMapData data = new TrainMapData();
        data.serverTick = root.getLong("tick");
        final ListTag stationList = root.getList("stations", 10);
        for (int i = 0; i < stationList.size(); ++i) {
            data.stations.add(StationEntry.read(stationList.getCompound(i)));
        }
        final ListTag trainList = root.getList("trains", 10);
        for (int j = 0; j < trainList.size(); ++j) {
            data.trains.add(TrainEntry.read(trainList.getCompound(j)));
        }
        return data;
    }

    public static class StationEntry
    {
        public final UUID id;
        public final String name;
        public final double x;
        public final double z;
        public final String dimension;
        public final boolean assembling;

        public StationEntry(UUID id, String name, double x, double z, String dimension, boolean assembling) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.z = z;
            this.dimension = dimension;
            this.assembling = assembling;
        }

        public CompoundTag write() {
            final CompoundTag t = new CompoundTag();
            t.putUUID("id", this.id);
            t.putString("name", this.name);
            t.putDouble("x", this.x);
            t.putDouble("z", this.z);
            t.putString("dim", this.dimension);
            t.putBoolean("asm", this.assembling);
            return t;
        }

        public static StationEntry read(CompoundTag t) {
            return new StationEntry(t.getUUID("id"), t.getString("name"), t.getDouble("x"), t.getDouble("z"), t.getString("dim"), t.getBoolean("asm"));
        }
    }

    public static class TrainEntry
    {
        public final UUID id;
        public final String name;
        public final String ownerName;
        public final double x;
        public final double z;
        public final String dimension;
        public final float speed;
        public final String nextDestination;
        public final List<String> scheduleStops;
        public final boolean stopped;
        public final String currentStationName;
        public final boolean waitingForSignal;
        public final int ticksWaitingForSignal;
        public final boolean positionValid;
        public final UUID currentStationId;
        public final UUID previousStationId;
        public final UUID destinationStationId;
        public final UUID activeSegmentFromId;
        public final UUID activeSegmentToId;
        public final float segmentProgress;
        public final float signedSpeed;
        public final String routeKey;

        public TrainEntry(UUID id, String name, String ownerName, double x, double z, String dimension, float speed, String nextDestination, List<String> scheduleStops, boolean stopped, String currentStationName, boolean waitingForSignal, int ticksWaitingForSignal) {
            this(id, name, ownerName, x, z, dimension, speed, nextDestination, scheduleStops, stopped, currentStationName, waitingForSignal, ticksWaitingForSignal, true, null, null, null, null, null, 0.0f, speed, "");
        }

        public TrainEntry(UUID id, String name, String ownerName, double x, double z, String dimension, float speed, String nextDestination, List<String> scheduleStops, boolean stopped, String currentStationName, boolean waitingForSignal, int ticksWaitingForSignal, boolean positionValid, UUID currentStationId, UUID previousStationId, UUID destinationStationId, UUID activeSegmentFromId, UUID activeSegmentToId, float segmentProgress, float signedSpeed, String routeKey) {
            this.id = id;
            this.name = name;
            this.ownerName = ownerName;
            this.x = x;
            this.z = z;
            this.dimension = dimension;
            this.speed = speed;
            this.nextDestination = nextDestination;
            this.scheduleStops = (scheduleStops != null) ? scheduleStops : Collections.emptyList();
            this.stopped = stopped;
            this.currentStationName = ((currentStationName != null) ? currentStationName : "");
            this.waitingForSignal = waitingForSignal;
            this.ticksWaitingForSignal = ticksWaitingForSignal;
            this.positionValid = positionValid;
            this.currentStationId = currentStationId;
            this.previousStationId = previousStationId;
            this.destinationStationId = destinationStationId;
            this.activeSegmentFromId = activeSegmentFromId;
            this.activeSegmentToId = activeSegmentToId;
            this.segmentProgress = Math.max(0.0f, Math.min(1.0f, segmentProgress));
            this.signedSpeed = signedSpeed;
            this.routeKey = (routeKey != null) ? routeKey : "";
        }

        public CompoundTag write() {
            final CompoundTag t = new CompoundTag();
            t.putUUID("id", this.id);
            t.putString("name", this.name);
            t.putString("owner", this.ownerName);
            t.putDouble("x", this.x);
            t.putDouble("z", this.z);
            t.putString("dim", this.dimension);
            t.putFloat("spd", this.speed);
            t.putString("dest", this.nextDestination);
            t.putBoolean("stop", this.stopped);
            t.putString("curStn", this.currentStationName);
            t.putBoolean("sigWait", this.waitingForSignal);
            t.putInt("sigTicks", this.ticksWaitingForSignal);
            t.putBoolean("posValid", this.positionValid);
            putUUIDIfPresent(t, "curStnId", this.currentStationId);
            putUUIDIfPresent(t, "prevStnId", this.previousStationId);
            putUUIDIfPresent(t, "destStnId", this.destinationStationId);
            putUUIDIfPresent(t, "segFromId", this.activeSegmentFromId);
            putUUIDIfPresent(t, "segToId", this.activeSegmentToId);
            t.putFloat("segProg", this.segmentProgress);
            t.putFloat("signedSpd", this.signedSpeed);
            t.putString("routeKey", this.routeKey);
            final ListTag stops = new ListTag();
            for (String s : this.scheduleStops) {
                final CompoundTag st = new CompoundTag();
                st.putString("s", s);
                stops.add(st);
            }
            t.put("sched", (Tag)stops);
            return t;
        }

        public static TrainEntry read(CompoundTag t) {
            final List<String> stops = new ArrayList<String>();
            final ListTag sched = t.getList("sched", 10);
            for (int i = 0; i < sched.size(); ++i) {
                stops.add(sched.getCompound(i).getString("s"));
            }
            return new TrainEntry(t.getUUID("id"), t.getString("name"), t.getString("owner"), t.getDouble("x"), t.getDouble("z"), t.getString("dim"), t.getFloat("spd"), t.getString("dest"), stops, t.getBoolean("stop"), t.getString("curStn"), t.getBoolean("sigWait"), t.getInt("sigTicks"), !t.contains("posValid") || t.getBoolean("posValid"), getUUIDIfPresent(t, "curStnId"), getUUIDIfPresent(t, "prevStnId"), getUUIDIfPresent(t, "destStnId"), getUUIDIfPresent(t, "segFromId"), getUUIDIfPresent(t, "segToId"), t.contains("segProg") ? t.getFloat("segProg") : 0.0f, t.contains("signedSpd") ? t.getFloat("signedSpd") : t.getFloat("spd"), t.contains("routeKey") ? t.getString("routeKey") : "");
        }

        private static void putUUIDIfPresent(CompoundTag tag, String key, UUID value) {
            if (value != null) {
                tag.putUUID(key, value);
            }
        }

        private static UUID getUUIDIfPresent(CompoundTag tag, String key) {
            return tag.hasUUID(key) ? tag.getUUID(key) : null;
        }
    }
}
