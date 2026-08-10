package com.createstats.train.manifest;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FreightManifestStationResolver {

    private FreightManifestStationResolver() {
    }

    public static ServerLevel resolveStationLevel(MinecraftServer server, FreightStationLink link) {
        if (server == null || link.getStationDimension() == null) {
            return null;
        }
        ResourceKey<Level> stationDimension = ResourceKey.create(Registries.DIMENSION, link.getStationDimension());
        return server.getLevel(stationDimension);
    }

    public static FreightStationCargoView captureCargoView(MinecraftServer server, FreightStationLink link) {
        ServerLevel stationLevel = resolveStationLevel(server, link);
        if (stationLevel == null) {
            return FreightStationCargoView.unavailable("The linked station dimension is not available right now.");
        }
        return captureCargoView(stationLevel, link);
    }

    public static FreightStationCargoView captureCargoView(ServerLevel stationLevel, FreightStationLink link) {
        StationBlockEntity stationBlockEntity = resolveStationBlockEntity(stationLevel, link);
        if (stationBlockEntity == null) {
            return FreightStationCargoView.unavailable("Linked block is not a train station.");
        }
        GlobalStation station = stationBlockEntity.getStation();
        if (station == null) {
            return FreightStationCargoView.unavailable("Station data is not available yet.");
        }
        Train train = station.getPresentTrain();
        if (train == null) {
            return FreightStationCargoView.unavailable("No train currently present at this station.");
        }
        return FreightStationCargoView.capture(train, FreightManifestLogic.previewProgress(train));
    }

    public static DeliveryOrderBoardView captureDeliveryBoard(MinecraftServer server, FreightStationLink link) {
        ServerLevel stationLevel = resolveStationLevel(server, link);
        if (stationLevel == null) {
            return DeliveryOrderBoardView.empty(0L, "The linked station dimension is not available right now.");
        }
        return captureDeliveryBoard(stationLevel, link);
    }

    public static DeliveryOrderBoardView captureDeliveryBoard(ServerLevel stationLevel, FreightStationLink link) {
        long currentTick = stationLevel.getGameTime();
        StationBlockEntity stationBlockEntity = resolveStationBlockEntity(stationLevel, link);
        if (stationBlockEntity == null) {
            return DeliveryOrderBoardView.empty(currentTick, "Linked block is not a train station.");
        }
        GlobalStation station = stationBlockEntity.getStation();
        if (station == null || station.getId() == null) {
            return DeliveryOrderBoardView.empty(currentTick, "Station data is not available yet.");
        }

        FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
        FreightManifest manifest = savedData.getManifest(station.getId());
        if (manifest == null) {
            manifest = new FreightManifest();
        }
        FreightManifestStationState stationState = savedData.getOrCreateStationState(station.getId());
        boolean changed = FreightManifestLogic.refreshPersistentCycles(manifest, stationState.getMutablePersistentOrders(), currentTick, stationState);
        if (changed) {
            savedData.setDirty();
        }

        Train presentTrain = station.getPresentTrain();
        if (presentTrain == null) {
            return FreightManifestLogic.buildDeliveryOrderBoardView(manifest, stationState.getMutablePersistentOrders().copy(),
                null, FreightStationCargoView.unavailable("No train currently present at this station."), currentTick, false,
                stationState.getHistoryEntries());
        }

        CompoundTag activeContext = FreightManifestLogic.findActiveManifestContext(presentTrain);
        DeliveryOrderBoard arrivalBoard = activeContext != null ? FreightManifestLogic.loadArrivalBoard(activeContext) : null;
        FreightStationCargoView cargoView = FreightStationCargoView.capture(presentTrain, FreightManifestLogic.previewProgress(presentTrain));
        return FreightManifestLogic.buildDeliveryOrderBoardView(manifest, stationState.getMutablePersistentOrders().copy(),
            arrivalBoard, cargoView, currentTick, activeContext != null, stationState.getHistoryEntries());
    }

    @Nullable
    public static StationBlockEntity resolveStationBlockEntity(ServerLevel stationLevel, FreightStationLink link) {
        if (link.getStationPos() == null) {
            return null;
        }
        if (!stationLevel.isLoaded(link.getStationPos())) {
            return null;
        }
        BlockEntity stationBe = stationLevel.getBlockEntity(link.getStationPos());
        if (!(stationBe instanceof StationBlockEntity stationBlockEntity)) {
            return null;
        }
        return stationBlockEntity;
    }
}
