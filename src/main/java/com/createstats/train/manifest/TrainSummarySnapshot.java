package com.createstats.train.manifest;

import com.createstats.train.power.TrainChargeLedger;
import com.createstats.train.power.TrainChargeProbe;
import com.createstats.train.power.TrainChargeReading;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;

public class TrainSummarySnapshot {
    private final UUID trainId;
    private final String trainName;
    private final String ownerName;
    private final int carriageCount;
    private final float speed;
    private final String nextDestination;
    private final List<String> scheduleStops;
    private final boolean stoppedAtStation;
    private final String currentStationName;
    private final boolean waitingForSignal;
    private final int ticksWaitingForSignal;
    private final boolean inboundToStation;
    private final TrainCargoSnapshot cargoSnapshot;
    private final long captureTick;

    @Nullable
    private final TrainChargeReading charge;

    public TrainSummarySnapshot(UUID trainId, String trainName, String ownerName,
                                int carriageCount, float speed, String nextDestination,
                                List<String> scheduleStops, boolean stoppedAtStation,
                                String currentStationName, boolean waitingForSignal,
                                int ticksWaitingForSignal, boolean inboundToStation,
                                TrainCargoSnapshot cargoSnapshot, long captureTick) {
        this(trainId, trainName, ownerName, carriageCount, speed, nextDestination, scheduleStops,
                stoppedAtStation, currentStationName, waitingForSignal, ticksWaitingForSignal,
                inboundToStation, cargoSnapshot, captureTick, null);
    }

    public TrainSummarySnapshot(UUID trainId, String trainName, String ownerName,
                                int carriageCount, float speed, String nextDestination,
                                List<String> scheduleStops, boolean stoppedAtStation,
                                String currentStationName, boolean waitingForSignal,
                                int ticksWaitingForSignal, boolean inboundToStation,
                                TrainCargoSnapshot cargoSnapshot, long captureTick,
                                @Nullable TrainChargeReading charge) {
        this.trainId = trainId;
        this.trainName = trainName;
        this.ownerName = ownerName;
        this.carriageCount = carriageCount;
        this.speed = speed;
        this.nextDestination = nextDestination;
        this.scheduleStops = new ArrayList<>(scheduleStops);
        this.stoppedAtStation = stoppedAtStation;
        this.currentStationName = currentStationName;
        this.waitingForSignal = waitingForSignal;
        this.ticksWaitingForSignal = ticksWaitingForSignal;
        this.inboundToStation = inboundToStation;
        this.cargoSnapshot = cargoSnapshot == null ? new TrainCargoSnapshot() : cargoSnapshot.copy();
        this.captureTick = captureTick;
        this.charge = charge;
    }

    public static TrainSummarySnapshot capture(Train train, MinecraftServer server,
                                               Map<UUID, String> stationNames, long tick) {
        return capture(train, server, stationNames, null, tick);
    }

    public static TrainSummarySnapshot capture(Train train, MinecraftServer server,
                                               Map<UUID, String> stationNames,
                                               @Nullable GlobalStation monitoredStation, long tick) {
        UUID trainId = train.id;
        String trainName = (train.name != null) ? train.name.getString() : "Train";
        String ownerName = resolveOwnerName(server, train.owner);
        int carriageCount = train.carriages.size();
        float speed = (float) Math.abs(train.speed);

        String nextDestination = "";
        if (train.navigation != null && train.navigation.destination != null) {
            nextDestination = (train.navigation.destination.name != null)
                    ? train.navigation.destination.name : "";
        }

        List<String> scheduleStops = new ArrayList<>();
        if (train.runtime != null && train.runtime.schedule != null) {
            for (ScheduleEntry entry : train.runtime.schedule.entries) {
                if (entry.instruction instanceof DestinationInstruction dest) {
                    String filter = dest.getFilter();
                    if (filter != null && !filter.isEmpty()) {
                        scheduleStops.add(filter);
                    }
                }
            }
        }

        boolean stoppedAtStation = train.currentStation != null;
        String currentStationName = "";
        if (train.currentStation != null) {
            String name = stationNames.get(train.currentStation);
            if (name != null) {
                currentStationName = name;
            }
        }

        boolean waitingForSignal = train.navigation != null
                && train.navigation.waitingForSignal != null;
        int ticksWaitingForSignal = waitingForSignal
                ? train.navigation.ticksWaitingForSignal : 0;
        boolean inboundToStation = monitoredStation != null && train.navigation != null
                && train.navigation.destination != null
                && monitoredStation.getId().equals(train.navigation.destination.getId());
        TrainCargoSnapshot cargoSnapshot = TrainCargoSnapshot.capture(train);

        TrainChargeReading charge = TrainChargeProbe.read(train, tick, currentStationName);
        TrainChargeLedger ledger = TrainChargeLedger.get(server);
        if (ledger != null) {
            if (charge != null) {
                ledger.record(trainId, charge);
            } else {
                charge = ledger.get(trainId);
            }
        }

        return new TrainSummarySnapshot(trainId, trainName, ownerName, carriageCount, speed,
                nextDestination, scheduleStops, stoppedAtStation, currentStationName,
                waitingForSignal, ticksWaitingForSignal, inboundToStation, cargoSnapshot, tick, charge);
    }

    private static String resolveOwnerName(MinecraftServer server, @Nullable UUID ownerUUID) {
        if (ownerUUID == null) {
            return "Unknown";
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(ownerUUID);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return ownerUUID.toString().substring(0, 8);
    }

    public UUID getTrainId() {
        return this.trainId;
    }

    public String getTrainName() {
        return this.trainName;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public int getCarriageCount() {
        return this.carriageCount;
    }

    public float getSpeed() {
        return this.speed;
    }

    public String getNextDestination() {
        return this.nextDestination;
    }

    public List<String> getScheduleStops() {
        return Collections.unmodifiableList(this.scheduleStops);
    }

    public boolean isStoppedAtStation() {
        return this.stoppedAtStation;
    }

    public String getCurrentStationName() {
        return this.currentStationName;
    }

    public boolean isWaitingForSignal() {
        return this.waitingForSignal;
    }

    public int getTicksWaitingForSignal() {
        return this.ticksWaitingForSignal;
    }

    public boolean isInboundToStation() {
        return this.inboundToStation;
    }

    public TrainCargoSnapshot getCargoSnapshot() {
        return this.cargoSnapshot.copy();
    }

    public long getCaptureTick() {
        return this.captureTick;
    }

    @Nullable
    public TrainChargeReading getCharge() {
        return this.charge;
    }

    public TrainSummarySnapshot copy() {
        return new TrainSummarySnapshot(this.trainId, this.trainName, this.ownerName,
                this.carriageCount, this.speed, this.nextDestination,
                new ArrayList<>(this.scheduleStops), this.stoppedAtStation,
                this.currentStationName, this.waitingForSignal,
                this.ticksWaitingForSignal, this.inboundToStation,
                this.cargoSnapshot, this.captureTick, this.charge);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("TrainId", this.trainId);
        tag.putString("TrainName", this.trainName);
        tag.putString("OwnerName", this.ownerName);
        tag.putInt("CarriageCount", this.carriageCount);
        tag.putFloat("Speed", this.speed);
        tag.putString("NextDest", this.nextDestination);
        ListTag stops = new ListTag();
        for (String stop : this.scheduleStops) {
            stops.add(StringTag.valueOf(stop));
        }
        tag.put("ScheduleStops", stops);
        tag.putBoolean("Stopped", this.stoppedAtStation);
        tag.putString("CurrentStation", this.currentStationName);
        tag.putBoolean("WaitSignal", this.waitingForSignal);
        tag.putInt("WaitTicks", this.ticksWaitingForSignal);
        tag.putBoolean("InboundToStation", this.inboundToStation);
        tag.put("CargoSnapshot", this.cargoSnapshot.save());
        tag.putLong("CaptureTick", this.captureTick);
        if (this.charge != null) {
            tag.put("Charge", this.charge.save());
        }
        return tag;
    }

    public static TrainSummarySnapshot load(CompoundTag tag) {
        List<String> stops = new ArrayList<>();
        ListTag stopTags = tag.getList("ScheduleStops", Tag.TAG_STRING);
        for (int i = 0; i < stopTags.size(); i++) {
            stops.add(stopTags.getString(i));
        }
        return new TrainSummarySnapshot(
                tag.getUUID("TrainId"),
                tag.getString("TrainName"),
                tag.getString("OwnerName"),
                tag.getInt("CarriageCount"),
                tag.getFloat("Speed"),
                tag.getString("NextDest"),
                stops,
                tag.getBoolean("Stopped"),
                tag.getString("CurrentStation"),
                tag.getBoolean("WaitSignal"),
                tag.getInt("WaitTicks"),
                tag.getBoolean("InboundToStation"),
                tag.contains("CargoSnapshot")
                        ? TrainCargoSnapshot.load(tag.getCompound("CargoSnapshot"))
                        : new TrainCargoSnapshot(),
                tag.getLong("CaptureTick"),
                tag.contains("Charge") ? TrainChargeReading.load(tag.getCompound("Charge")) : null);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.trainId);
        buf.writeUtf(this.trainName);
        buf.writeUtf(this.ownerName);
        buf.writeVarInt(this.carriageCount);
        buf.writeFloat(this.speed);
        buf.writeUtf(this.nextDestination);
        buf.writeVarInt(this.scheduleStops.size());
        for (String stop : this.scheduleStops) {
            buf.writeUtf(stop);
        }
        buf.writeBoolean(this.stoppedAtStation);
        buf.writeUtf(this.currentStationName);
        buf.writeBoolean(this.waitingForSignal);
        buf.writeVarInt(this.ticksWaitingForSignal);
        buf.writeBoolean(this.inboundToStation);
        buf.writeNbt(this.cargoSnapshot.save());
        buf.writeLong(this.captureTick);
        TrainChargeReading.encodeNullable(buf, this.charge);
    }

    public static TrainSummarySnapshot decode(FriendlyByteBuf buf) {
        UUID trainId = buf.readUUID();
        String trainName = buf.readUtf();
        String ownerName = buf.readUtf();
        int carriageCount = buf.readVarInt();
        float speed = buf.readFloat();
        String nextDest = buf.readUtf();
        int stopCount = buf.readVarInt();
        List<String> stops = new ArrayList<>();
        for (int i = 0; i < stopCount; i++) {
            stops.add(buf.readUtf());
        }
        boolean stopped = buf.readBoolean();
        String currentStation = buf.readUtf();
        boolean waitSignal = buf.readBoolean();
        int waitTicks = buf.readVarInt();
        boolean inboundToStation = buf.readBoolean();
        TrainCargoSnapshot cargoSnapshot = TrainCargoSnapshot.load(buf.readNbt());
        long captureTick = buf.readLong();
        TrainChargeReading charge = TrainChargeReading.decodeNullable(buf);
        return new TrainSummarySnapshot(trainId, trainName, ownerName, carriageCount, speed,
                nextDest, stops, stopped, currentStation, waitSignal, waitTicks,
                inboundToStation, cargoSnapshot, captureTick, charge);
    }
}
