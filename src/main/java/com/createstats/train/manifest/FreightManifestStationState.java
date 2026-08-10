package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class FreightManifestStationState {

    private static final int MAX_HISTORY_ENTRIES = 12;

    private FreightManifest manifest;
    private DeliveryOrderBoard persistentOrders;
    private final java.util.List<DeliveryOrderHistoryEntry> historyEntries;

    public FreightManifestStationState() {
        this.manifest = new FreightManifest();
        this.persistentOrders = new DeliveryOrderBoard();
        this.historyEntries = new ArrayList<>();
    }

    public FreightManifest getManifest() {
        return this.manifest.copy();
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

    public void setManifest(FreightManifest manifest, long currentTick) {
        FreightManifest previousManifest = this.manifest.copy();
        this.manifest = manifest.copy();
        reconcilePersistentOrders(previousManifest, this.manifest, currentTick);
    }

    private void reconcilePersistentOrders(FreightManifest previousManifest, FreightManifest currentManifest, long currentTick) {
        Map<UUID, FreightManifestLine> previousById = indexById(previousManifest);
        Map<UUID, FreightManifestLine> currentById = indexById(currentManifest);
        this.persistentOrders.retainAll(currentById.keySet());
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
        for (DeliveryOrderHistoryEntry entry : this.historyEntries) {
            copy.historyEntries.add(entry.copy());
        }
        return copy;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Manifest", this.manifest.save(registries));
        tag.put("PersistentOrders", this.persistentOrders.save());
        ListTag history = new ListTag();
        for (DeliveryOrderHistoryEntry entry : this.historyEntries) {
            history.add(entry.save());
        }
        tag.put("HistoryEntries", history);
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
        NBTHelper.iterateCompoundList(tag.getList("HistoryEntries", Tag.TAG_COMPOUND), entryTag -> state.historyEntries.add(DeliveryOrderHistoryEntry.load(entryTag)));
        return state;
    }
}
