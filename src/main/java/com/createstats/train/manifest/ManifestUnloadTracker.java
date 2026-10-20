package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public class ManifestUnloadTracker {
    private static final Map<UUID, StationVisitState> ACTIVE_VISITS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, UUID> INTERFACE_STATIONS = new ConcurrentHashMap<>();

    public static class StationVisitState {
        private final UUID trainId;
        private final Map<FreightManifestResourceKey, Integer> movedToStation = new HashMap<>();
        private final Map<FreightManifestResourceKey, Integer> movedToTrain = new HashMap<>();

        StationVisitState(UUID trainId) {
            this.trainId = trainId;
        }

        public UUID getTrainId() {
            return this.trainId;
        }

        public synchronized int getMoved(FreightManifestResourceKey key, ManifestOperation operation) {
            Map<FreightManifestResourceKey, Integer> side = this.sideFor(operation);
            return side.getOrDefault(key, 0);
        }

        public synchronized void record(FreightManifestResourceKey key, ManifestOperation operation, int amount) {
            if (amount <= 0) {
                return;
            }
            this.sideFor(operation).merge(key, amount, Integer::sum);
        }

        private Map<FreightManifestResourceKey, Integer> sideFor(ManifestOperation operation) {
            return operation == ManifestOperation.UNLOAD ? this.movedToStation : this.movedToTrain;
        }
    }

    public static StationVisitState getOrCreate(UUID stationId, UUID trainId) {
        return ACTIVE_VISITS.compute(stationId, (key, existing) -> {
            if (existing == null || !existing.trainId.equals(trainId)) {
                return new StationVisitState(trainId);
            }
            return existing;
        });
    }

    public static void registerInterface(BlockPos pos, UUID stationId) {
        INTERFACE_STATIONS.put(pos, stationId);
    }

    public static void onInterfaceDisconnect(BlockPos pos) {
        INTERFACE_STATIONS.remove(pos);
    }

    public static void endVisit(UUID stationId) {
        ACTIVE_VISITS.remove(stationId);
    }

    public static void clearAll() {
        ACTIVE_VISITS.clear();
        INTERFACE_STATIONS.clear();
    }
}
