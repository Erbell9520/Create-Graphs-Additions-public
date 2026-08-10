package com.createstats.train.manifest;

import com.createstats.train.TrainMapData;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;

/** [1] */
public class StationTrainRoster {

    @Nullable
    private final TrainSummarySnapshot presentTrain;
    private final List<TrainSummarySnapshot> associatedTrains;
    private final List<TrainVisitRecord> recentVisits;
    private final long captureTick;

    public StationTrainRoster(@Nullable TrainSummarySnapshot presentTrain,
                              List<TrainSummarySnapshot> associatedTrains,
                              List<TrainVisitRecord> recentVisits,
                              long captureTick) {
        this.presentTrain = presentTrain;
        this.associatedTrains = new ArrayList<>(associatedTrains);
        this.recentVisits = new ArrayList<>(recentVisits);
        this.captureTick = captureTick;
    }

    /**
     * Captures a roster from the live Create train graph.
     * Iterates all active trains to find:
     * <ol>
     *   <li>The train currently docked at the station (present train)</li>
     *   <li>Trains whose schedule contains a destination matching the station name</li>
     * </ol>
     * Only creates {@link TrainSummarySnapshot} objects for relevant trains to minimize cost.
     *
     * @param server         the server instance (for owner name resolution)
     * @param station        the GlobalStation being monitored
     * @param stationName    the station display name (for schedule filter matching)
     * @param persistedVisits the controller's persisted visit history
     * @param tick           current game tick
     * @return a populated roster view
     */
    public static StationTrainRoster capture(MinecraftServer server, GlobalStation station,
                                             String stationName, List<TrainVisitRecord> persistedVisits,
                                             long tick) {
        TrainSummarySnapshot presentSnapshot = null;
        List<TrainSummarySnapshot> associated = new ArrayList<>();

        GlobalRailwayManager railways = Create.RAILWAYS;
        if (railways == null) {
            return new StationTrainRoster(null, associated, persistedVisits, tick);
        }

        // Build station name lookup for currentStationName resolution in snapshots
        Map<UUID, String> stationNames = new HashMap<>();
        for (TrackGraph graph : new ArrayList<>(railways.trackNetworks.values())) {
            for (GlobalStation s : graph.getPoints(EdgePointType.STATION)) {
                stationNames.put(s.getId(), s.name != null ? s.name : "Unknown");
            }
        }

        Train presentTrainEntity = station.getPresentTrain();

        for (Train train : new ArrayList<>(railways.trains.values())) {
            if (train.invalid) {
                continue;
            }

            boolean isPresent = (train == presentTrainEntity);
            boolean inSchedule = hasStationInSchedule(train, stationName);

            if (!isPresent && !inSchedule) {
                continue;
            }

            TrainSummarySnapshot snapshot = TrainSummarySnapshot.capture(train, server, stationNames, tick);
            if (isPresent) {
                presentSnapshot = snapshot;
            }
            if (inSchedule && !isPresent) {
                associated.add(snapshot);
            }
        }

        return new StationTrainRoster(presentSnapshot, associated, persistedVisits, tick);
    }

    /**
     * Checks if a train's schedule contains a stop matching the given station name.
     * Uses the same wildcard matching as Create's station filters.
     */
    private static boolean hasStationInSchedule(Train train, String stationName) {
        if (train.runtime == null || train.runtime.schedule == null) {
            return false;
        }
        for (ScheduleEntry entry : train.runtime.schedule.entries) {
            if (entry.instruction instanceof DestinationInstruction dest) {
                String filter = dest.getFilter();
                if (filter != null && TrainMapData.matchesStationFilter(filter, stationName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static StationTrainRoster empty(long tick) {
        return new StationTrainRoster(null, List.of(), List.of(), tick);
    }

    // getters

    @Nullable
    public TrainSummarySnapshot getPresentTrain() {
        return this.presentTrain;
    }

    public List<TrainSummarySnapshot> getAssociatedTrains() {
        return Collections.unmodifiableList(this.associatedTrains);
    }

    public List<TrainVisitRecord> getRecentVisits() {
        return Collections.unmodifiableList(this.recentVisits);
    }

    public long getCaptureTick() {
        return this.captureTick;
    }

    public int getAssociatedTrainCount() {
        return this.associatedTrains.size();
    }

    public int getRecentVisitCount() {
        return this.recentVisits.size();
    }

    public StationTrainRoster copy() {
        TrainSummarySnapshot pCopy = this.presentTrain != null ? this.presentTrain.copy() : null;
        List<TrainSummarySnapshot> aCopy = new ArrayList<>();
        for (TrainSummarySnapshot s : this.associatedTrains) {
            aCopy.add(s.copy());
        }
        List<TrainVisitRecord> vCopy = new ArrayList<>();
        for (TrainVisitRecord v : this.recentVisits) {
            vCopy.add(v.copy());
        }
        return new StationTrainRoster(pCopy, aCopy, vCopy, this.captureTick);
    }

    // NBT persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasPresent", this.presentTrain != null);
        if (this.presentTrain != null) {
            tag.put("PresentTrain", this.presentTrain.save());
        }
        ListTag assocList = new ListTag();
        for (TrainSummarySnapshot s : this.associatedTrains) {
            assocList.add(s.save());
        }
        tag.put("AssociatedTrains", assocList);
        ListTag visitList = new ListTag();
        for (TrainVisitRecord v : this.recentVisits) {
            visitList.add(v.save());
        }
        tag.put("RecentVisits", visitList);
        tag.putLong("CaptureTick", this.captureTick);
        return tag;
    }

    public static StationTrainRoster load(CompoundTag tag) {
        TrainSummarySnapshot present = tag.getBoolean("HasPresent")
                ? TrainSummarySnapshot.load(tag.getCompound("PresentTrain")) : null;
        List<TrainSummarySnapshot> associated = new ArrayList<>();
        ListTag assocTags = tag.getList("AssociatedTrains", Tag.TAG_COMPOUND);
        for (int i = 0; i < assocTags.size(); i++) {
            associated.add(TrainSummarySnapshot.load(assocTags.getCompound(i)));
        }
        List<TrainVisitRecord> visits = new ArrayList<>();
        ListTag visitTags = tag.getList("RecentVisits", Tag.TAG_COMPOUND);
        for (int i = 0; i < visitTags.size(); i++) {
            visits.add(TrainVisitRecord.load(visitTags.getCompound(i)));
        }
        long tick = tag.getLong("CaptureTick");
        return new StationTrainRoster(present, associated, visits, tick);
    }

    // network

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.presentTrain != null);
        if (this.presentTrain != null) {
            this.presentTrain.encode(buf);
        }
        buf.writeVarInt(this.associatedTrains.size());
        for (TrainSummarySnapshot s : this.associatedTrains) {
            s.encode(buf);
        }
        buf.writeVarInt(this.recentVisits.size());
        for (TrainVisitRecord v : this.recentVisits) {
            v.encode(buf);
        }
        buf.writeLong(this.captureTick);
    }

    public static StationTrainRoster decode(FriendlyByteBuf buf) {
        TrainSummarySnapshot present = buf.readBoolean() ? TrainSummarySnapshot.decode(buf) : null;
        int aCount = buf.readVarInt();
        List<TrainSummarySnapshot> associated = new ArrayList<>();
        for (int i = 0; i < aCount; i++) {
            associated.add(TrainSummarySnapshot.decode(buf));
        }
        int vCount = buf.readVarInt();
        List<TrainVisitRecord> visits = new ArrayList<>();
        for (int i = 0; i < vCount; i++) {
            visits.add(TrainVisitRecord.decode(buf));
        }
        long tick = buf.readLong();
        return new StationTrainRoster(present, associated, visits, tick);
    }
}
