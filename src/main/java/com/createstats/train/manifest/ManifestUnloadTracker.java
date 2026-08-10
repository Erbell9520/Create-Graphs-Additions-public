package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared server-side tracking for manifest-limited extraction.
 * Keyed by station UUID; resets automatically when a different train arrives,
 * and clears when all PSI/PFI interfaces at the station disconnect.
 */
public class ManifestUnloadTracker {

    private static final Map<UUID, StationVisitState> ACTIVE_VISITS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, UUID> INTERFACE_STATIONS = new ConcurrentHashMap<>();

    public static class StationVisitState {
        private final UUID trainId;
        private final Map<ResourceLocation, Integer> extractedItems = new HashMap<>();
        private final Map<ResourceLocation, Integer> drainedFluids = new HashMap<>();

        StationVisitState(UUID trainId) {
            this.trainId = trainId;
        }

        public UUID getTrainId() {
            return this.trainId;
        }

        public int getExtractedItems(ResourceLocation itemId) {
            return this.extractedItems.getOrDefault(itemId, 0);
        }

        public void recordItemExtraction(ResourceLocation itemId, int count) {
            this.extractedItems.merge(itemId, count, Integer::sum);
        }

        public int getDrainedFluids(ResourceLocation fluidId) {
            return this.drainedFluids.getOrDefault(fluidId, 0);
        }

        public void recordFluidDrain(ResourceLocation fluidId, int amount) {
            this.drainedFluids.merge(fluidId, amount, Integer::sum);
        }
    }

    /**
     * Gets the visit state for a station. If the train ID has changed (new visit),
     * the old state is replaced automatically.
     */
    public static StationVisitState getOrCreate(UUID stationId, UUID trainId) {
        return ACTIVE_VISITS.compute(stationId, (key, existing) -> {
            if (existing == null || !existing.trainId.equals(trainId)) {
                return new StationVisitState(trainId);
            }
            return existing;
        });
    }

    /**
     * Called when a PSI/PFI handler resolves its manifest.
     * Registers which station this interface position belongs to.
     */
    public static void registerInterface(BlockPos pos, UUID stationId) {
        INTERFACE_STATIONS.put(pos, stationId);
    }

    /**
     * Called when a PSI/PFI stops transferring (train departs).
     * Clears extraction counts once no interfaces remain for that station.
     */
    public static void onInterfaceDisconnect(BlockPos pos) {
        UUID stationId = INTERFACE_STATIONS.remove(pos);
        if (stationId != null && !INTERFACE_STATIONS.containsValue(stationId)) {
            ACTIVE_VISITS.remove(stationId);
        }
    }

    public static void clearAll() {
        ACTIVE_VISITS.clear();
        INTERFACE_STATIONS.clear();
    }
}
