package com.createstats.surveyor;

import com.createstats.CreateStats;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.PathwayCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// saved data, not entities. entities unload with their chunk
public final class PathwayStore extends SavedData {
    static final String FILE = CreateStats.MOD_ID + "_pathways";

    public record Running(@Nullable UUID subLevel, UUID pathway) {
    }

    private final Map<UUID, Pathway> pathways = new LinkedHashMap<>();
    private final Map<UUID, Running> running = new LinkedHashMap<>();

    public static PathwayStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PathwayStore::new, PathwayStore::load), FILE);
    }

    public Collection<Pathway> all() {
        return Collections.unmodifiableCollection(pathways.values());
    }

    @Nullable
    public Pathway get(UUID id) {
        return id == null ? null : pathways.get(id);
    }

    public void put(Pathway p) {
        pathways.put(p.id(), p);
        setDirty();
    }

    @Nullable
    public Pathway remove(UUID id) {
        Pathway p = pathways.remove(id);
        if (p != null) setDirty();
        return p;
    }

    public int countOwnedBy(UUID owner) {
        int n = 0;
        for (Pathway p : pathways.values()) if (p.owner().equals(owner)) n++;
        return n;
    }

    public int nextColour(UUID owner) {
        boolean[] used = new boolean[16];
        for (Pathway p : pathways.values()) if (p.owner().equals(owner)) used[p.colour() & 15] = true;

        int[] order = {3, 1, 5, 14, 11, 4, 10, 2, 6, 9, 13, 0, 8, 12, 7, 15};
        for (int c : order) if (!used[c]) return c;
        return order[countOwnedBy(owner) % order.length];
    }

    public Map<UUID, Running> running() {
        return Collections.unmodifiableMap(running);
    }

    public void setRunning(UUID console, @Nullable Running r) {
        Running before = r == null ? running.remove(console) : running.put(console, r);
        if (before == null ? r != null : !before.equals(r)) setDirty();
    }

    public boolean isRunning(UUID console) {
        return running.containsKey(console);
    }

    private static PathwayStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PathwayStore store = new PathwayStore();
        ListTag list = tag.getList("Pathways", Tag.TAG_BYTE_ARRAY);
        for (Tag t : list) {
            if (!(t instanceof ByteArrayTag bytes)) continue;
            try {
                Pathway p = PathwayCodec.decode(bytes.getAsByteArray());
                store.pathways.put(p.id(), p);
            } catch (Exception e) {
                CreateStats.LOGGER.warn("[Pathways] a saved pathway could not be read and was skipped: {}", e.toString());
            }
        }
        ListTag runs = tag.getList("Running", Tag.TAG_COMPOUND);
        for (Tag t : runs) {
            if (!(t instanceof CompoundTag c) || !c.hasUUID("Console") || !c.hasUUID("Pathway")) continue;
            store.running.put(c.getUUID("Console"), new Running(c.hasUUID("SubLevel") ? c.getUUID("SubLevel") : null, c.getUUID("Pathway")));
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Pathway p : pathways.values()) list.add(new ByteArrayTag(PathwayCodec.encode(p)));
        tag.put("Pathways", list);
        ListTag runs = new ListTag();
        for (Map.Entry<UUID, Running> e : running.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Console", e.getKey());
            c.putUUID("Pathway", e.getValue().pathway());
            if (e.getValue().subLevel() != null) c.putUUID("SubLevel", e.getValue().subLevel());
            runs.add(c);
        }
        tag.put("Running", runs);
        return tag;
    }

    public List<Pathway> sortedFor(UUID viewer) {
        List<Pathway> out = new ArrayList<>(pathways.values());
        out.sort((a, b) -> {
            boolean am = a.owner().equals(viewer);
            boolean bm = b.owner().equals(viewer);
            if (am != bm) return am ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }
}
