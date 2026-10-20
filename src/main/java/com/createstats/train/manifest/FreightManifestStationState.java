package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class FreightManifestStationState {
    private static final int MAX_HISTORY_ENTRIES = 64;

    private FreightManifest manifest;
    private DeliveryOrderBoard persistentOrders;

    private final java.util.List<FreightManifestLine> completedOnceGates;
    private final java.util.List<DeliveryOrderHistoryEntry> historyEntries;

    private DeliveryOrderBoard lastVisitOrders;
    @Nullable
    private UUID lastVisitTrainId;
    private String lastVisitTrainName;
    private long lastVisitTick;

    @Nullable
    private UUID visitTrainId;
    private String visitTrainName;
    private long visitStartTick;
    private DeliveryOrderBoard visitOrders;
    private TrainCargoSnapshot visitLastSnapshot;
    private TrainCargoSnapshot visitMovedToStation;
    private TrainCargoSnapshot visitMovedToTrain;
    private boolean visitSnapshotTaken;

    private long visitLastSeenTick;

    private String lastUnaccounted;

    private transient long lastAllocationTick = Long.MIN_VALUE;

    public FreightManifestStationState() {
        this.manifest = new FreightManifest();
        this.persistentOrders = new DeliveryOrderBoard();
        this.completedOnceGates = new ArrayList<>();
        this.historyEntries = new ArrayList<>();
        this.lastVisitOrders = new DeliveryOrderBoard();
        this.lastVisitTrainName = "";
        this.lastVisitTick = -1L;
        this.visitTrainName = "";
        this.visitStartTick = -1L;
        this.visitOrders = new DeliveryOrderBoard();
        this.visitLastSnapshot = new TrainCargoSnapshot();
        this.visitMovedToStation = new TrainCargoSnapshot();
        this.visitMovedToTrain = new TrainCargoSnapshot();
        this.visitLastSeenTick = -1L;
        this.lastUnaccounted = "";
    }

    public boolean isVisitActive(@Nullable UUID trainId) {
        return trainId != null && trainId.equals(this.visitTrainId);
    }

    @Nullable
    public UUID getVisitTrainId() {
        return this.visitTrainId;
    }

    public String getVisitTrainName() {
        return this.visitTrainName;
    }

    public long getVisitStartTick() {
        return this.visitStartTick;
    }

    public DeliveryOrderBoard getMutableVisitOrders() {
        return this.visitOrders;
    }

    public DeliveryOrderBoard getVisitOrders() {
        return this.visitOrders.copy();
    }

    public TrainCargoSnapshot getVisitMovedToStation() {
        return this.visitMovedToStation.copy();
    }

    public TrainCargoSnapshot getVisitMovedToTrain() {
        return this.visitMovedToTrain.copy();
    }

    public void recordVisitMovement(TrainCargoSnapshot toStation, TrainCargoSnapshot toTrain) {
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : toStation.getAmounts().entrySet()) {
            this.visitMovedToStation.addAmount(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<FreightManifestResourceKey, Integer> entry : toTrain.getAmounts().entrySet()) {
            this.visitMovedToTrain.addAmount(entry.getKey(), entry.getValue());
        }
    }

    public String getLastUnaccounted() {
        return this.lastUnaccounted;
    }

    public void setLastUnaccounted(String lastUnaccounted) {
        this.lastUnaccounted = lastUnaccounted == null ? "" : lastUnaccounted;
    }

    public long getVisitLastSeenTick() {
        return this.visitLastSeenTick;
    }

    public TrainCargoSnapshot getVisitLastSnapshot() {
        return this.visitLastSnapshot;
    }

    public void setVisitLastSnapshot(TrainCargoSnapshot snapshot) {
        this.visitLastSnapshot = snapshot.copy();
        this.visitSnapshotTaken = true;
    }

    public boolean isVisitSnapshotTaken() {
        return this.visitSnapshotTaken;
    }

    public boolean beginVisit(UUID trainId, String trainName, long currentTick) {
        if (trainId.equals(this.visitTrainId)) {
            this.visitLastSeenTick = currentTick;
            if (!this.visitTrainName.equals(trainName)) {
                this.visitTrainName = trainName;
                return true;
            }
            return false;
        }
        this.endVisit(currentTick);
        this.visitLastSeenTick = currentTick;
        this.visitTrainId = trainId;
        this.visitTrainName = trainName;
        this.visitStartTick = currentTick;
        this.visitOrders = new DeliveryOrderBoard();
        this.visitLastSnapshot = new TrainCargoSnapshot();
        this.visitMovedToStation = new TrainCargoSnapshot();
        this.visitMovedToTrain = new TrainCargoSnapshot();
        this.visitSnapshotTaken = false;
        this.lastUnaccounted = "";
        return true;
    }

    public boolean endVisit(long currentTick) {
        if (this.visitTrainId == null) {
            return false;
        }
        this.lastVisitOrders = this.visitOrders.copy();
        this.lastVisitTrainId = this.visitTrainId;
        this.lastVisitTrainName = this.visitTrainName;
        this.lastVisitTick = currentTick;
        this.visitTrainId = null;
        this.visitTrainName = "";
        this.visitStartTick = -1L;
        this.visitOrders = new DeliveryOrderBoard();
        this.visitLastSnapshot = new TrainCargoSnapshot();
        this.visitMovedToStation = new TrainCargoSnapshot();
        this.visitMovedToTrain = new TrainCargoSnapshot();
        this.visitSnapshotTaken = false;
        this.visitLastSeenTick = -1L;
        this.lastUnaccounted = "";
        return true;
    }

    public boolean claimAllocationTick(long currentTick) {
        if (this.lastAllocationTick == currentTick) {
            return false;
        }
        this.lastAllocationTick = currentTick;
        return true;
    }

    public DeliveryOrderBoard getLastVisitOrders() {
        return this.lastVisitOrders.copy();
    }

    public String getLastVisitTrainName() {
        return this.lastVisitTrainName;
    }

    public long getLastVisitTick() {
        return this.lastVisitTick;
    }

    public boolean hasLastVisit() {
        return this.lastVisitTick >= 0L && !this.lastVisitOrders.getLineStates().isEmpty();
    }

    public boolean recordVisitOrders(DeliveryOrderBoard arrivalBoard, @Nullable UUID trainId,
                                     String trainName, long currentTick) {
        boolean changed = !java.util.Objects.equals(this.lastVisitTrainId, trainId)
            || !this.lastVisitTrainName.equals(trainName)
            || !sameDeliveredFigures(this.lastVisitOrders, arrivalBoard);
        this.lastVisitOrders = arrivalBoard.copy();
        this.lastVisitTrainId = trainId;
        this.lastVisitTrainName = trainName;
        this.lastVisitTick = currentTick;
        return changed;
    }

    private static boolean sameDeliveredFigures(DeliveryOrderBoard a, DeliveryOrderBoard b) {
        Map<UUID, DeliveryOrderLineState> left = a.getLineStates();
        Map<UUID, DeliveryOrderLineState> right = b.getLineStates();
        if (left.size() != right.size()) {
            return false;
        }
        for (Map.Entry<UUID, DeliveryOrderLineState> entry : left.entrySet()) {
            DeliveryOrderLineState other = right.get(entry.getKey());
            if (other == null
                || other.getDeliveredAmount() != entry.getValue().getDeliveredAmount()
                || other.isDismissed() != entry.getValue().isDismissed()) {
                return false;
            }
        }
        return true;
    }

    public FreightManifest getManifest() {
        return this.manifest.copy();
    }

    public FreightManifest peekManifest() {
        return this.manifest;
    }

    public DeliveryOrderBoard getMutablePersistentOrders() {
        return this.persistentOrders;
    }

    public DeliveryOrderBoard getPersistentOrders() {
        return this.persistentOrders.copy();
    }

    public java.util.List<DeliveryOrderHistoryEntry> getHistoryEntries() {
        java.util.List<DeliveryOrderHistoryEntry> copy = new ArrayList<>();
        for (DeliveryOrderHistoryEntry entry : this.historyEntries) {
            copy.add(entry.copy());
        }
        return copy;
    }

    public void addHistory(DeliveryOrderHistoryType type, long tick, String title, String detail) {
        DeliveryOrderHistoryEntry entry = new DeliveryOrderHistoryEntry(type, tick, title, detail);
        if (!this.historyEntries.isEmpty()) {
            DeliveryOrderHistoryEntry latest = this.historyEntries.get(0);
            if (latest.getType() == type && latest.getTick() == tick
                && latest.getTitle().equals(title) && latest.getDetail().equals(detail)) {
                return;
            }
        }
        this.historyEntries.add(0, entry);
        while (this.historyEntries.size() > MAX_HISTORY_ENTRIES) {
            this.historyEntries.remove(this.historyEntries.size() - 1);
        }
    }

    public boolean hasCompletedOnceGate(ManifestOperation operation, ManifestCargoKind cargoKind) {
        for (FreightManifestLine line : this.completedOnceGates) {
            if (line.getOperation() == operation && line.getCargoKind() == cargoKind) {
                return true;
            }
        }
        return false;
    }

    public boolean retireCompletedOnceOrders() {
        java.util.List<FreightManifestLine> retired = new ArrayList<>();
        for (FreightManifestLine line : this.manifest.getLines()) {
            DeliveryOrderLineState state = this.persistentOrders.get(line.getLineId());
            if (DeliveryOrderLifecyclePolicy.retiresFromActiveManifest(line.getResetMode(),
                    state != null && state.isComplete(line.getAmount()))) {
                retired.add(line);
            }
        }
        if (retired.isEmpty()) {
            return false;
        }
        for (FreightManifestLine line : retired) {
            this.completedOnceGates.add(line.copy());
            this.persistentOrders.remove(line.getLineId());
        }
        this.manifest.getLines().removeAll(retired);
        this.manifest.bumpVersion();
        return true;
    }

    public void setManifest(FreightManifest manifest, long currentTick) {
        FreightManifest previousManifest = this.manifest.copy();
        this.manifest = manifest.copy();
        java.util.Set<DeliveryOrderLifecyclePolicy.Direction> published = new java.util.HashSet<>();
        for (FreightManifestLine line : this.manifest.getLines()) {
            published.add(new DeliveryOrderLifecyclePolicy.Direction(line.getOperation(), line.getCargoKind()));
        }
        this.completedOnceGates.removeIf(gate -> !DeliveryOrderLifecyclePolicy.publishKeepsOnceGate(
            new DeliveryOrderLifecyclePolicy.Direction(gate.getOperation(), gate.getCargoKind()), published));
        reconcilePersistentOrders(previousManifest, this.manifest, currentTick);
    }

    private void reconcilePersistentOrders(FreightManifest previousManifest, FreightManifest currentManifest, long currentTick) {
        Map<UUID, FreightManifestLine> previousById = indexById(previousManifest);
        Map<UUID, FreightManifestLine> currentById = indexById(currentManifest);
        this.persistentOrders.retainAll(currentById.keySet());

        this.lastVisitOrders.retainAll(currentById.keySet());
        this.visitOrders.retainAll(currentById.keySet());
        for (FreightManifestLine line : currentManifest.getLines()) {
            if (!line.getResetMode().isPersistent()) {
                this.persistentOrders.remove(line.getLineId());
                continue;
            }
            DeliveryOrderLineState state = this.persistentOrders.getOrCreate(line.getLineId(), currentTick);
            FreightManifestLine previousLine = previousById.get(line.getLineId());
            if (previousLine == null || !previousLine.isCycleCompatibleWith(line)) {
                state.reset(currentTick);
            }
            state.reconcileTarget(line.getAmount(), currentTick);
        }
    }

    private static Map<UUID, FreightManifestLine> indexById(FreightManifest manifest) {
        Map<UUID, FreightManifestLine> indexed = new HashMap<>();
        for (FreightManifestLine line : manifest.getLines()) {
            indexed.put(line.getLineId(), line);
        }
        return indexed;
    }

    public FreightManifestStationState copy() {
        FreightManifestStationState copy = new FreightManifestStationState();
        copy.manifest = this.manifest.copy();
        copy.persistentOrders = this.persistentOrders.copy();
        for (FreightManifestLine line : this.completedOnceGates) {
            copy.completedOnceGates.add(line.copy());
        }
        copy.lastVisitOrders = this.lastVisitOrders.copy();
        copy.lastVisitTrainId = this.lastVisitTrainId;
        copy.lastVisitTrainName = this.lastVisitTrainName;
        copy.lastVisitTick = this.lastVisitTick;
        copy.visitTrainId = this.visitTrainId;
        copy.visitTrainName = this.visitTrainName;
        copy.visitStartTick = this.visitStartTick;
        copy.visitOrders = this.visitOrders.copy();
        copy.visitLastSnapshot = this.visitLastSnapshot.copy();
        copy.visitMovedToStation = this.visitMovedToStation.copy();
        copy.visitMovedToTrain = this.visitMovedToTrain.copy();
        copy.visitSnapshotTaken = this.visitSnapshotTaken;
        copy.visitLastSeenTick = this.visitLastSeenTick;
        copy.lastUnaccounted = this.lastUnaccounted;
        for (DeliveryOrderHistoryEntry entry : this.historyEntries) {
            copy.historyEntries.add(entry.copy());
        }
        return copy;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Manifest", this.manifest.save(registries));
        tag.put("PersistentOrders", this.persistentOrders.save());
        ListTag completedOnce = new ListTag();
        for (FreightManifestLine line : this.completedOnceGates) {
            completedOnce.add(line.save(registries));
        }
        tag.put("CompletedOnceGates", completedOnce);
        ListTag history = new ListTag();
        for (DeliveryOrderHistoryEntry entry : this.historyEntries) {
            history.add(entry.save());
        }
        tag.put("HistoryEntries", history);
        tag.put("LastVisitOrders", this.lastVisitOrders.save());
        if (this.lastVisitTrainId != null) {
            tag.putUUID("LastVisitTrainId", this.lastVisitTrainId);
        }
        tag.putString("LastVisitTrainName", this.lastVisitTrainName);
        tag.putLong("LastVisitTick", this.lastVisitTick);

        if (this.visitTrainId != null) {
            tag.putUUID("VisitTrainId", this.visitTrainId);
            tag.putString("VisitTrainName", this.visitTrainName);
            tag.putLong("VisitStartTick", this.visitStartTick);
            tag.put("VisitOrders", this.visitOrders.save());
            tag.put("VisitLastSnapshot", this.visitLastSnapshot.save());
            tag.put("VisitMovedToStation", this.visitMovedToStation.save());
            tag.put("VisitMovedToTrain", this.visitMovedToTrain.save());
            tag.putBoolean("VisitSnapshotTaken", this.visitSnapshotTaken);
            tag.putLong("VisitLastSeenTick", this.visitLastSeenTick);
        }
        return tag;
    }

    public static FreightManifestStationState load(CompoundTag tag, HolderLookup.Provider registries) {
        FreightManifestStationState state = new FreightManifestStationState();
        if (tag.contains("Manifest")) {
            state.manifest = FreightManifest.load(tag.getCompound("Manifest"), registries);
        }
        if (tag.contains("PersistentOrders")) {
            state.persistentOrders = DeliveryOrderBoard.load(tag.getCompound("PersistentOrders"));
        }
        NBTHelper.iterateCompoundList(tag.getList("CompletedOnceGates", Tag.TAG_COMPOUND),
            entryTag -> state.completedOnceGates.add(FreightManifestLine.load(entryTag, registries)));
        NBTHelper.iterateCompoundList(tag.getList("HistoryEntries", Tag.TAG_COMPOUND), entryTag -> state.historyEntries.add(DeliveryOrderHistoryEntry.load(entryTag)));
        if (tag.contains("LastVisitOrders")) {
            state.lastVisitOrders = DeliveryOrderBoard.load(tag.getCompound("LastVisitOrders"));
        }
        if (tag.hasUUID("LastVisitTrainId")) {
            state.lastVisitTrainId = tag.getUUID("LastVisitTrainId");
        }
        state.lastVisitTrainName = tag.getString("LastVisitTrainName");
        state.lastVisitTick = tag.contains("LastVisitTick") ? tag.getLong("LastVisitTick") : -1L;
        if (tag.hasUUID("VisitTrainId")) {
            state.visitTrainId = tag.getUUID("VisitTrainId");
            state.visitTrainName = tag.getString("VisitTrainName");
            state.visitStartTick = tag.getLong("VisitStartTick");
            state.visitOrders = DeliveryOrderBoard.load(tag.getCompound("VisitOrders"));
            state.visitLastSnapshot = TrainCargoSnapshot.load(tag.getCompound("VisitLastSnapshot"));
            state.visitMovedToStation = TrainCargoSnapshot.load(tag.getCompound("VisitMovedToStation"));
            state.visitMovedToTrain = TrainCargoSnapshot.load(tag.getCompound("VisitMovedToTrain"));
            state.visitSnapshotTaken = tag.getBoolean("VisitSnapshotTaken");
            state.visitLastSeenTick = tag.contains("VisitLastSeenTick") ? tag.getLong("VisitLastSeenTick") : -1L;
        }
        return state;
    }
}
