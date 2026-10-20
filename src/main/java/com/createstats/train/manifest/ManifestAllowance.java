package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.server.level.ServerLevel;

// the old flat map ignored progress
public final class ManifestAllowance {
    public static final int UNRESTRICTED = -1;

    private final boolean restricted;
    private final Map<FreightManifestResourceKey, Integer> remaining;

    private ManifestAllowance(boolean restricted, Map<FreightManifestResourceKey, Integer> remaining) {
        this.restricted = restricted;
        this.remaining = remaining;
    }

    public boolean isRestricted() {
        return this.restricted;
    }

    public int allowanceFor(FreightManifestResourceKey key) {
        if (!this.restricted) {
            return UNRESTRICTED;
        }
        return Math.max(0, this.remaining.getOrDefault(key, 0));
    }

    public void spend(FreightManifestResourceKey key, int amount) {
        if (!this.restricted || amount <= 0) {
            return;
        }
        this.remaining.merge(key, -amount, Integer::sum);
    }

    public static ManifestAllowance capture(ServerLevel stationLevel, UUID stationId, @Nullable Train train,
                                            ManifestOperation operation, ManifestCargoKind cargoKind) {
        FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
        FreightManifestStationState stationState = savedData.getStationState(stationId);
        if (stationState == null) {
            return unrestricted();
        }
        FreightManifest manifest = stationState.peekManifest();
        boolean completedOnceGate = stationState.hasCompletedOnceGate(operation, cargoKind);
        if (manifest.isEmpty() && !completedOnceGate) {
            return unrestricted();
        }

        if (train != null) {
            GlobalStation dockedAt = train.getCurrentStation();
            if (dockedAt == null || !stationId.equals(dockedAt.getId())) {
                train = null;
            }
        }
        if (train == null) {
            return closed();
        }
        FreightManifestLogic.evaluateStation(savedData, stationId, train, stationLevel.getGameTime(), null);
        DeliveryOrderBoard persistentBoard = stationState.getPersistentOrders();
        DeliveryOrderBoard arrivalBoard = stationState.isVisitActive(train.id) ? stationState.getVisitOrders() : null;
        ManifestUnloadTracker.StationVisitState visit = ManifestUnloadTracker.getOrCreate(stationId, train.id);
        ManifestAllowance allowance = capture(manifest, operation, cargoKind, persistentBoard, arrivalBoard, visit);

        return allowance.withCompletedOnceGate(stationState.hasCompletedOnceGate(operation, cargoKind));
    }

    ManifestAllowance withCompletedOnceGate(boolean completed) {
        return this.restricted || !completed ? this : closed();
    }

    static ManifestAllowance capture(FreightManifest manifest, ManifestOperation operation, ManifestCargoKind cargoKind,
                                     DeliveryOrderBoard persistentBoard, @Nullable DeliveryOrderBoard arrivalBoard,
                                     @Nullable ManifestUnloadTracker.StationVisitState visit) {
        List<Demand> demands = new ArrayList<>();
        for (FreightManifestLine line : manifest.getLines()) {
            if (line.getOperation() != operation || line.getCargoKind() != cargoKind) {
                continue;
            }
            FreightManifestResourceKey key = line.getResourceKey();
            if (key == null || line.getAmount() <= 0) {
                continue;
            }
            DeliveryOrderBoard board = line.getResetMode().isPersistent() ? persistentBoard : arrivalBoard;
            DeliveryOrderLineState state = board == null ? null : board.get(line.getLineId());
            int delivered = state == null ? 0 : Math.min(state.getDeliveredAmount(), line.getAmount());
            demands.add(new Demand(key, line.getAmount(), delivered));
        }
        return fromDemands(demands, operation, visit);
    }

    record Demand(FreightManifestResourceKey key, int target, int delivered) {
    }

    static ManifestAllowance fromDemands(List<Demand> demands, ManifestOperation operation,
                                         @Nullable ManifestUnloadTracker.StationVisitState visit) {
        if (demands.isEmpty()) {
            return unrestricted();
        }
        Map<FreightManifestResourceKey, Integer> targets = new HashMap<>();
        Map<FreightManifestResourceKey, Integer> delivered = new HashMap<>();
        for (Demand demand : demands) {
            targets.merge(demand.key(), demand.target(), Integer::sum);
            delivered.merge(demand.key(), demand.delivered(), Integer::sum);
        }

        Map<FreightManifestResourceKey, Integer> remaining = new HashMap<>();
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : targets.entrySet()) {
            FreightManifestResourceKey key = entry.getKey();

            int accountedFor = delivered.getOrDefault(key, 0);
            if (visit != null) {
                accountedFor = Math.max(accountedFor, visit.getMoved(key, operation));
            }
            remaining.put(key, Math.max(0, entry.getValue() - accountedFor));
        }

        return new ManifestAllowance(true, remaining);
    }

    private static ManifestAllowance unrestricted() {
        return new ManifestAllowance(false, new HashMap<>());
    }

    private static ManifestAllowance closed() {
        return new ManifestAllowance(true, new HashMap<>());
    }
}
