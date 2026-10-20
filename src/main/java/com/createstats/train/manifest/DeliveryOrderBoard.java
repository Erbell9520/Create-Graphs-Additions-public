package com.createstats.train.manifest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class DeliveryOrderBoard {
    private final Map<UUID, DeliveryOrderLineState> lineStates;

    public DeliveryOrderBoard() {
        this.lineStates = new HashMap<>();
    }

    public Map<UUID, DeliveryOrderLineState> getLineStates() {
        return this.lineStates;
    }

    public DeliveryOrderLineState getOrCreate(UUID lineId, long currentTick) {
        return this.lineStates.computeIfAbsent(lineId, id -> DeliveryOrderLineState.create(id, currentTick));
    }

    public DeliveryOrderLineState get(UUID lineId) {
        return this.lineStates.get(lineId);
    }

    public void remove(UUID lineId) {
        this.lineStates.remove(lineId);
    }

    public void retainAll(Set<UUID> lineIds) {
        this.lineStates.keySet().removeIf(id -> !lineIds.contains(id));
    }

    public DeliveryOrderBoard copy() {
        DeliveryOrderBoard copy = new DeliveryOrderBoard();
        for (Map.Entry<UUID, DeliveryOrderLineState> entry : this.lineStates.entrySet()) {
            copy.lineStates.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (DeliveryOrderLineState state : this.lineStates.values()) {
            entries.add(state.save());
        }
        tag.put("LineStates", entries);
        return tag;
    }

    public static DeliveryOrderBoard load(CompoundTag tag) {
        DeliveryOrderBoard board = new DeliveryOrderBoard();
        NBTHelper.iterateCompoundList(tag.getList("LineStates", Tag.TAG_COMPOUND), entryTag -> {
            DeliveryOrderLineState state = DeliveryOrderLineState.load(entryTag);
            board.lineStates.put(state.getLineId(), state);
        });
        return board;
    }
}
