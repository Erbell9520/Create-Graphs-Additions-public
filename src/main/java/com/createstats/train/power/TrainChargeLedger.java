package com.createstats.train.power;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// one table, trains are global in create
public final class TrainChargeLedger extends SavedData {
    private static final String DATA_NAME = "create_stats_train_charge";

    private final Map<UUID, TrainChargeReading> readings = new HashMap<>();

    public TrainChargeLedger() {
    }

    @Nullable
    public static TrainChargeLedger get(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        final ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TrainChargeLedger::new, TrainChargeLedger::load), DATA_NAME);
    }

    private static TrainChargeLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        final TrainChargeLedger ledger = new TrainChargeLedger();
        final ListTag list = tag.getList("Trains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Id")) {
                ledger.readings.put(entry.getUUID("Id"), TrainChargeReading.load(entry.getCompound("R")));
            }
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (Map.Entry<UUID, TrainChargeReading> e : readings.entrySet()) {
            final CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            entry.put("R", e.getValue().save());
            list.add(entry);
        }
        tag.put("Trains", list);
        return tag;
    }

    @Nullable
    public TrainChargeReading get(UUID train) {
        return readings.get(train);
    }

    public void record(UUID train, TrainChargeReading reading) {
        final TrainChargeReading held = readings.get(train);
        if (held != null && held.tick() > reading.tick()) {
            return;
        }
        readings.put(train, reading);
        if (held == null || held.stored() != reading.stored() || held.capacity() != reading.capacity()
                || !held.where().equals(reading.where())) {
            setDirty();
        }
    }

    public void retainOnly(java.util.Set<UUID> live) {
        if (readings.keySet().retainAll(live)) {
            setDirty();
        }
    }
}
