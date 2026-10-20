package com.createstats.pa;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public class DeviceIdRegistry extends SavedData {
    private static final String DATA_NAME = "create_stats_device_ids";

    private static final int MAX_ENTRIES = 20000;

    private final Map<BlockPos, DeviceId> byPos = new LinkedHashMap<>();
    private final Map<String, Integer> nextByType = new LinkedHashMap<>();

    public static DeviceIdRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DeviceIdRegistry::new, DeviceIdRegistry::load), DATA_NAME);
    }

    private static DeviceIdRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        final DeviceIdRegistry data = new DeviceIdRegistry();
        final ListTag entries = tag.getList("Entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            final CompoundTag e = entries.getCompound(i);
            data.byPos.put(BlockPos.of(e.getLong("Pos")),
                    new DeviceId(e.getString("Type"), e.getInt("N")));
        }
        final CompoundTag counters = tag.getCompound("Next");
        for (String key : counters.getAllKeys()) {
            data.nextByType.put(key, counters.getInt(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag entries = new ListTag();
        for (Map.Entry<BlockPos, DeviceId> e : this.byPos.entrySet()) {
            final CompoundTag one = new CompoundTag();
            one.putLong("Pos", e.getKey().asLong());
            one.putString("Type", e.getValue().typeKey());
            one.putInt("N", e.getValue().number());
            entries.add(one);
        }
        tag.put("Entries", entries);
        final CompoundTag counters = new CompoundTag();
        this.nextByType.forEach(counters::putInt);
        tag.put("Next", counters);
        return tag;
    }

    public DeviceId idFor(BlockPos pos, BlockState state) {
        final String typeKey = typeKeyOf(state);
        if (typeKey.isEmpty()) {
            return DeviceId.NONE;
        }
        final BlockPos key = pos.immutable();
        final DeviceId existing = this.byPos.get(key);
        if (existing != null && existing.typeKey().equals(typeKey)) {
            return existing;
        }
        if (existing == null && this.byPos.size() >= MAX_ENTRIES) {
            return DeviceId.NONE;
        }
        final int number = this.nextByType.merge(typeKey, 1, Integer::sum);
        final DeviceId fresh = new DeviceId(typeKey, number);
        this.byPos.put(key, fresh);
        this.setDirty();
        return fresh;
    }

    public DeviceId peek(BlockPos pos) {
        return this.byPos.getOrDefault(pos, DeviceId.NONE);
    }

    public static DeviceId idFor(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) {
            return DeviceId.NONE;
        }
        return get(server).idFor(pos, state);
    }

    public static String typeKeyOf(BlockState state) {
        if (state == null || state.isAir()) {
            return "";
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id == null ? "" : id.toString();
    }
}
