package com.createstats.train.manifest;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
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

    public static FreightManifest captureManifest(ServerLevel stationLevel, FreightStationLink link) {
        java.util.UUID stationId = resolveLiveStationId(stationLevel, link);
        if (stationId == null) {
            return new FreightManifest();
        }
        FreightManifest manifest = FreightManifestSavedData.get(stationLevel).getManifest(stationId);
        return manifest == null ? new FreightManifest() : manifest;
    }

    @Nullable
    public static java.util.UUID resolveLiveStationId(ServerLevel stationLevel, FreightStationLink link) {
        GlobalStation station = resolveStation(stationLevel, link);
        if (station == null || station.getId() == null) {
            return null;
        }
        return station.getId();
    }

    @Nullable
    public static GlobalStation resolveStation(ServerLevel stationLevel, FreightStationLink link) {
        for (TrackGraph graph : Create.RAILWAYS.sided(stationLevel).trackNetworks.values()) {
            GlobalStation station = graph.getPoint(EdgePointType.STATION, link.getStationId());
            if (station != null && stationLevel.dimension().equals(station.getBlockEntityDimension())) {
                return station;
            }
        }
        StationBlockEntity stationBlockEntity = resolveStationBlockEntity(stationLevel, link);
        GlobalStation station = stationBlockEntity == null ? null : stationBlockEntity.getStation();
        if (station != null && stationLevel.dimension().equals(station.getBlockEntityDimension())) {
            reconcileStationId(stationLevel, link, station.getId());
            return station;
        }
        return null;
    }

    private static void reconcileStationId(ServerLevel stationLevel, FreightStationLink link, java.util.UUID liveId) {
        if (link.getStationId() != null) {
            FreightManifestSavedData.get(stationLevel).reconcileStationId(link.getStationId(), liveId);
        }
    }

    public static FreightStationCargoView captureCargoView(MinecraftServer server, FreightStationLink link) {
        ServerLevel stationLevel = resolveStationLevel(server, link);
        if (stationLevel == null) {
            return FreightStationCargoView.unavailable("The linked station dimension is not available right now.");
        }
        return captureCargoView(stationLevel, link);
    }

    public static FreightStationCargoView captureCargoView(ServerLevel stationLevel, FreightStationLink link) {
        GlobalStation station = resolveStation(stationLevel, link);
        if (station == null) {
            return FreightStationCargoView.unavailable("Station data is not available yet.");
        }
        reconcileStationId(stationLevel, link, station.getId());
        Train train = station.getPresentTrain();
        if (train == null) {
            return FreightStationCargoView.unavailable("No train currently present at this station.");
        }

        long currentTick = stationLevel.getGameTime();
        FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
        FreightManifestLogic.evaluateStation(savedData, station.getId(), train, currentTick, null);
        FreightManifestStationState stationState = savedData.getStationState(station.getId());
        return FreightStationCargoView.capture(train,
            FreightManifestLogic.captureVisitProgress(stationState, train, currentTick));
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
        GlobalStation station = resolveStation(stationLevel, link);
        if (station == null || station.getId() == null) {
            return DeliveryOrderBoardView.empty(currentTick, "Station data is not available yet.");
        }

        reconcileStationId(stationLevel, link, station.getId());

        return buildStationView(stationLevel, station.getId(), station.getPresentTrain(), currentTick).deliveryBoard();
    }

    private static StationView buildStationView(ServerLevel stationLevel, java.util.UUID stationId,
                                                @Nullable Train presentTrain, long currentTick) {
        FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
        FreightManifestLogic.evaluateStation(savedData, stationId, presentTrain, currentTick, null);

        FreightManifestStationState stationState = savedData.getStationState(stationId);
        FreightManifest manifest = stationState == null ? new FreightManifest() : stationState.getManifest();
        ArrivalOrders arrival = stationState == null ? new ArrivalOrders(null, false, false)
            : resolveArrivalOrders(presentTrain, stationState);
        FreightStationCargoView cargoView = presentTrain == null
            ? FreightStationCargoView.unavailable("No train currently present at this station.")
            : FreightStationCargoView.capture(presentTrain, FreightManifestLogic.captureVisitProgress(stationState, presentTrain, currentTick));
        DeliveryOrderBoardView boardView = FreightManifestLogic.buildDeliveryOrderBoardView(manifest,
            stationState == null ? new DeliveryOrderBoard() : stationState.getPersistentOrders(), arrival.board(), cargoView, currentTick,
            arrival.live(), arrival.historic(), stationState == null ? java.util.List.of() : stationState.getHistoryEntries());
        return new StationView(manifest, cargoView, boardView);
    }

    public record StationView(FreightManifest manifest, FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
    }

    public static StationView captureStationView(MinecraftServer server, FreightStationLink link) {
        ServerLevel stationLevel = resolveStationLevel(server, link);
        if (stationLevel == null) {
            String message = "The linked station dimension is not available right now.";
            return new StationView(new FreightManifest(), FreightStationCargoView.unavailable(message), DeliveryOrderBoardView.empty(0L, message));
        }
        return captureStationView(stationLevel, link);
    }

    public static StationView captureStationView(ServerLevel stationLevel, FreightStationLink link) {
        long currentTick = stationLevel.getGameTime();
        GlobalStation station = resolveStation(stationLevel, link);
        if (station == null || station.getId() == null) {
            String message = "Linked station is unavailable. Load its chunk or relink the clipboard to a station.";

            FreightManifest saved = FreightManifestSavedData.get(stationLevel).getManifest(link.getStationId());
            return new StationView(saved == null ? new FreightManifest() : saved,
                FreightStationCargoView.unavailable(message), DeliveryOrderBoardView.empty(currentTick, message));
        }
        reconcileStationId(stationLevel, link, station.getId());
        return buildStationView(stationLevel, station.getId(), station.getPresentTrain(), currentTick);
    }

    private record ArrivalOrders(@Nullable DeliveryOrderBoard board, boolean live, boolean historic) {
    }

    private static ArrivalOrders resolveArrivalOrders(@Nullable Train presentTrain, FreightManifestStationState stationState) {
        if (presentTrain != null && stationState.isVisitActive(presentTrain.id)) {
            boolean held = FreightManifestLogic.findActiveManifestContext(presentTrain) != null;
            return new ArrivalOrders(stationState.getVisitOrders(), held, false);
        }
        if (stationState.hasLastVisit()) {
            return new ArrivalOrders(stationState.getLastVisitOrders(), false, true);
        }
        return new ArrivalOrders(null, false, false);
    }

    public static FreightManifestPageSummary capturePageSummary(ServerLevel stationLevel, FreightStationLink link) {
        GlobalStation station = resolveStation(stationLevel, link);
        if (station == null || station.getId() == null) {
            return FreightManifestPageSummary.unreachable();
        }
        long currentTick = stationLevel.getGameTime();

        return FreightManifestPageSummary.from(
            buildStationView(stationLevel, station.getId(), station.getPresentTrain(), currentTick).deliveryBoard());
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
