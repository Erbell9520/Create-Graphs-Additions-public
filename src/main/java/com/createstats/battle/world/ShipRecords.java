package com.createstats.battle.world;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// no time limit, only a count. a week in game is 2.3 real hours, parked ships got forgotten
public final class ShipRecords {
    public static final int MAX_PER_LEVEL = 64;
    private static final String INDEX = "create_stats_battle_index";

    private static final Map<ResourceKey<Level>, Map<RecordKey, ShipRecord>> LOADED = new ConcurrentHashMap<>();

    private ShipRecords() {
    }

    public static ShipRecord getOrCreate(ServerLevel level, RecordKey key, BlockPos origin) {
        Map<RecordKey, ShipRecord> loaded = LOADED.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
        ShipRecord r = loaded.get(key);
        if (r != null) return r;
        r = level.getDataStorage().computeIfAbsent(ShipRecord.factory(key, origin), key.fileName());
        loaded.put(key, r);
        Index index = level.getDataStorage().computeIfAbsent(Index.FACTORY, INDEX);
        index.seen(key.fileName(), level.getGameTime());
        String dropped = index.dropOldest(MAX_PER_LEVEL, key.fileName());
        if (dropped != null) forget(level, dropped);
        return r;
    }

    @Nullable
    public static ShipRecord existing(ServerLevel level, RecordKey key) {
        Map<RecordKey, ShipRecord> loaded = LOADED.get(level.dimension());
        if (loaded != null && loaded.containsKey(key)) return loaded.get(key);
        return level.getDataStorage().get(ShipRecord.factory(key, BlockPos.ZERO), key.fileName());
    }

    @Nullable
    public static ShipRecord loadedShip(ServerLevel level, UUID ship) {
        Map<RecordKey, ShipRecord> loaded = LOADED.get(level.dimension());
        if (loaded == null) return null;
        for (ShipRecord r : loaded.values()) {
            if (!r.ground() && ship.equals(r.key().ship())) return r;
        }
        return null;
    }

    public static List<ShipRecord> groundAt(ServerLevel level, BlockPos pos) {
        Map<RecordKey, ShipRecord> loaded = LOADED.get(level.dimension());
        if (loaded == null || loaded.isEmpty()) return List.of();
        List<ShipRecord> out = new ArrayList<>(1);
        for (ShipRecord r : loaded.values()) {
            if (r.ground() && r.inGroundBox(pos)) out.add(r);
        }
        return out;
    }

    public static void clear() {
        LOADED.clear();
    }

    private static void forget(ServerLevel level, String fileName) {
        Map<RecordKey, ShipRecord> loaded = LOADED.get(level.dimension());
        if (loaded != null && loaded.keySet().stream().anyMatch(k -> k.fileName().equals(fileName))) return;
        try {
            Path root = level.getServer().getWorldPath(LevelResource.ROOT);
            Path file = DimensionType.getStorageFolder(level.dimension(), root).resolve("data").resolve(fileName + ".dat");
            Files.deleteIfExists(file);
            CreateStats.LOGGER.info("[Battle] dropped the least recently seen battle record {} (more than {} in {})",
                    fileName, MAX_PER_LEVEL, level.dimension().location());
        } catch (Exception e) {
            CreateStats.LOGGER.warn("[Battle] could not drop battle record {}: {}", fileName, e.toString());
        }
    }

    static final class Index extends SavedData {
        static final SavedData.Factory<Index> FACTORY = new SavedData.Factory<>(Index::new, Index::load, null);

        private final Map<String, Long> seen = new HashMap<>();

        void seen(String name, long now) {
            Long old = seen.put(name, now);
            if (old == null || old != now) setDirty();
        }

        @Nullable
        String dropOldest(int cap, String keep) {
            if (seen.size() <= cap) return null;
            String oldest = null;
            long when = Long.MAX_VALUE;
            for (Map.Entry<String, Long> e : seen.entrySet()) {
                if (e.getKey().equals(keep)) continue;
                if (e.getValue() < when) {
                    when = e.getValue();
                    oldest = e.getKey();
                }
            }
            if (oldest != null) {
                seen.remove(oldest);
                setDirty();
            }
            return oldest;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            CompoundTag entries = new CompoundTag();
            for (Map.Entry<String, Long> e : seen.entrySet()) entries.putLong(e.getKey(), e.getValue());
            tag.put("Seen", entries);
            return tag;
        }

        static Index load(CompoundTag tag, HolderLookup.Provider registries) {
            Index index = new Index();
            CompoundTag entries = tag.getCompound("Seen");
            for (String name : entries.getAllKeys()) index.seen.put(name, entries.getLong(name));
            return index;
        }
    }
}
