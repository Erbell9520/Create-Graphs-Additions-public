package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class FreightManifestSavedData extends SavedData {
    private static final String DATA_NAME = "create_stats_freight_manifests";

    private final Map<UUID, FreightManifestStationState> stations;

    public FreightManifestSavedData() {
        this.stations = new HashMap<>();
    }

    public static FreightManifestSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(FreightManifestSavedData::new, FreightManifestSavedData::load), DATA_NAME);
    }

    private static FreightManifestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FreightManifestSavedData data = new FreightManifestSavedData();
        NBTHelper.iterateCompoundList(tag.getList("Stations", Tag.TAG_COMPOUND), entryTag -> {
            if (!entryTag.hasUUID("StationId")) {
                return;
            }
            UUID stationId = entryTag.getUUID("StationId");
            if (entryTag.contains("Manifest") && !entryTag.contains("State")) {
                FreightManifestStationState state = new FreightManifestStationState();
                state.setManifest(FreightManifest.load(entryTag.getCompound("Manifest"), registries), 0L);
                data.stations.put(stationId, state);
                return;
            }
            CompoundTag stateTag = entryTag.contains("State") ? entryTag.getCompound("State") : new CompoundTag();
            data.stations.put(stationId, FreightManifestStationState.load(stateTag, registries));
        });
        return data;
    }

    @Nullable
    public FreightManifest getManifest(UUID stationId) {
        FreightManifestStationState state = this.stations.get(stationId);
        return state == null ? null : state.getManifest();
    }

    public DeliveryOrderBoard getPersistentOrders(UUID stationId) {
        FreightManifestStationState state = this.stations.get(stationId);
        return state == null ? new DeliveryOrderBoard() : state.getPersistentOrders();
    }

    // read without creating, getOrCreate is a write dressed up as a read
    @Nullable
    public FreightManifestStationState getStationState(UUID stationId) {
        return this.stations.get(stationId);
    }

    @Nullable
    public static FreightManifestSavedData peek(ServerLevel level) {
        return level.getDataStorage().get(
            new SavedData.Factory<>(FreightManifestSavedData::new, FreightManifestSavedData::load), DATA_NAME);
    }

    public java.util.List<UUID> getStationsWithOpenVisit() {
        java.util.List<UUID> open = new java.util.ArrayList<>();
        for (Map.Entry<UUID, FreightManifestStationState> entry : this.stations.entrySet()) {
            if (entry.getValue().getVisitTrainId() != null) {
                open.add(entry.getKey());
            }
        }
        return open;
    }

    public FreightManifestStationState getOrCreateStationState(UUID stationId) {
        return this.stations.computeIfAbsent(stationId, id -> new FreightManifestStationState());
    }

    public void putManifest(UUID stationId, FreightManifest manifest, long currentTick) {
        this.getOrCreateStationState(stationId).setManifest(manifest, currentTick);
        this.setDirty();
    }

    public void reconcileStationId(UUID linkedId, UUID liveId) {
        if (linkedId.equals(liveId)) {
            return;
        }
        if (this.stations.containsKey(liveId)) {
            return;
        }
        FreightManifestStationState linkedState = this.stations.remove(linkedId);
        if (linkedState != null) {
            this.stations.put(liveId, linkedState);
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        for (Map.Entry<UUID, FreightManifestStationState> entry : this.stations.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("StationId", entry.getKey());
            entryTag.put("State", entry.getValue().save(registries));
            entries.add(entryTag);
        }
        tag.put("Stations", entries);
        return tag;
    }
}
