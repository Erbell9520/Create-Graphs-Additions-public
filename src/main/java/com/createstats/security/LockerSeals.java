package com.createstats.security;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// not on the item. components go over the wire, so the contents would too
public final class LockerSeals extends SavedData {
    private static final String DATA_NAME = "create_stats_locker_seals";

    private final Map<UUID, CompoundTag> records = new HashMap<>();

    public static LockerSeals get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LockerSeals::new, LockerSeals::load), DATA_NAME);
    }

    @Nullable
    public static LockerSeals of(@Nullable Level level) {
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return null;
        }
        final ServerLevel overworld = level.getServer().overworld();
        return overworld == null ? null : get(overworld);
    }

    private static LockerSeals load(CompoundTag tag, HolderLookup.Provider registries) {
        final LockerSeals data = new LockerSeals();
        final ListTag list = tag.getList("Seals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("Id")) {
                data.records.put(entry.getUUID("Id"), entry.getCompound("Payload"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : this.records.entrySet()) {
            final CompoundTag out = new CompoundTag();
            out.putUUID("Id", entry.getKey());
            out.put("Payload", entry.getValue());
            list.add(out);
        }
        tag.put("Seals", list);
        return tag;
    }

    public UUID seal(CompoundTag payload) {
        final UUID id = UUID.randomUUID();
        this.records.put(id, payload.copy());
        setDirty();
        return id;
    }

    @Nullable
    public CompoundTag redeem(UUID id) {
        final CompoundTag payload = this.records.remove(id);
        if (payload != null) {
            setDirty();
        }
        return payload;
    }

    public int size() {
        return this.records.size();
    }
}
