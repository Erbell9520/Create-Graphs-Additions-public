package com.createstats.chunkloader;

import com.createstats.chunkloader.core.CrownRegion;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CrownRegistry extends SavedData {
    private static final String FILE = "create_stats_spark_crowns";

    public record Station(String dimension, long pos, @Nullable UUID owner, int regionX, int regionZ, long[] picks,
                          int held) {
        public Station {
            picks = picks == null ? new long[0] : picks.clone();
            held = Math.max(0, Math.min(held, picks.length));
        }

        public boolean holds(long chunk) {
            for (int i = 0; i < held; i++) {
                if (picks[i] == chunk) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<String, Station> stations = new HashMap<>();

    public static CrownRegistry get(ServerLevel level) {
        return get(level.getServer());
    }

    public static CrownRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CrownRegistry::new, CrownRegistry::load), FILE);
    }

    public static String keyOf(String dimension, long pos) {
        return dimension + "|" + pos;
    }

    public static String dimensionOf(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static boolean stationStillAt(ServerLevel level, long pos) {
        final net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(pos);
        return !level.isLoaded(p)
                || level.getBlockEntity(p) instanceof com.createstats.blockentity.SparkCrownBE;
    }

    @Nullable
    public Station at(String dimension, long pos) {
        return stations.get(keyOf(dimension, pos));
    }

    @Nullable
    public Station holderOf(String dimension, int regionX, int regionZ,
                            @Nullable java.util.function.LongPredicate stillThere) {
        final List<String> stale = new ArrayList<>();
        Station found = null;
        for (Map.Entry<String, Station> e : stations.entrySet()) {
            final Station s = e.getValue();
            if (!s.dimension().equals(dimension) || s.regionX() != regionX || s.regionZ() != regionZ) {
                continue;
            }
            if (stillThere != null && !stillThere.test(s.pos())) {
                stale.add(e.getKey());
                continue;
            }
            found = s;
            break;
        }
        if (!stale.isEmpty()) {
            stale.forEach(stations::remove);
            setDirty();
        }
        return found;
    }

    public int ownerTotal(@Nullable UUID owner) {
        if (owner == null) {
            return 0;
        }
        int total = 0;
        for (Station s : stations.values()) {
            if (owner.equals(s.owner())) {
                total += s.picks().length;
            }
        }
        return total;
    }

    public long[] heldIn(String dimension, int x0, int z0, int x1, int z1, long except) {
        final it.unimi.dsi.fastutil.longs.LongArrayList out = new it.unimi.dsi.fastutil.longs.LongArrayList();
        for (Station s : stations.values()) {
            if (!s.dimension().equals(dimension) || s.pos() == except) {
                continue;
            }
            for (int i = 0; i < s.held(); i++) {
                final long c = s.picks()[i];
                final int x = CrownRegion.x(c);
                final int z = CrownRegion.z(c);
                if (x >= x0 && x < x1 && z >= z0 && z < z1) {
                    out.add(c);
                }
            }
        }
        return out.toLongArray();
    }

    public int stationsOf(@Nullable UUID owner) {
        if (owner == null) {
            return 0;
        }
        int n = 0;
        for (Station s : stations.values()) {
            if (owner.equals(s.owner())) {
                n++;
            }
        }
        return n;
    }

    public void put(Station s) {
        final String key = keyOf(s.dimension(), s.pos());
        final Station before = stations.get(key);
        if (before != null && before.held() == s.held() && before.regionX() == s.regionX()
                && before.regionZ() == s.regionZ() && java.util.Objects.equals(before.owner(), s.owner())
                && java.util.Arrays.equals(before.picks(), s.picks())) {
            return;
        }
        stations.put(key, s);
        setDirty();
    }

    public void remove(String dimension, long pos) {
        if (stations.remove(keyOf(dimension, pos)) != null) {
            setDirty();
        }
    }

    public boolean keepsTicket(String dimension, long stationPos, long chunk) {
        final Station s = at(dimension, stationPos);
        return s != null && s.holds(chunk);
    }

    private static CrownRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        final CrownRegistry out = new CrownRegistry();
        final ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag t = list.getCompound(i);
            final long pos = t.getLong("Pos");
            final String dim = t.getString("Dim");
            final long[] picks = t.getLongArray("Picks");
            final int[] region = t.getIntArray("Region");
            out.stations.put(keyOf(dim, pos), new Station(dim, pos, t.hasUUID("Owner") ? t.getUUID("Owner") : null,
                    region.length == 2 ? region[0] : CrownRegion.regionOf(net.minecraft.core.BlockPos.getX(pos) >> 4),
                    region.length == 2 ? region[1] : CrownRegion.regionOf(net.minecraft.core.BlockPos.getZ(pos) >> 4),
                    picks, t.getInt("Held")));
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (Station s : stations.values()) {
            final CompoundTag t = new CompoundTag();
            t.putString("Dim", s.dimension());
            t.putLong("Pos", s.pos());
            if (s.owner() != null) {
                t.putUUID("Owner", s.owner());
            }
            t.putIntArray("Region", new int[] {s.regionX(), s.regionZ()});
            t.putLongArray("Picks", s.picks());
            t.putInt("Held", s.held());
            list.add(t);
        }
        tag.put("Stations", list);
        return tag;
    }
}
