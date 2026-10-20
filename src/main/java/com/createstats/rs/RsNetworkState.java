package com.createstats.rs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.compat.RefinedStorageCompatHelper;
import com.createstats.data.WindowedTrend;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class RsNetworkState {
    public static final int MAX_ROWS = RefinedStorageCompatHelper.MAX_TRACKED_ITEMS;

    private static final long EMPTY_ROW_TTL_TICKS = 24000L;

    public static final class Track {
        private final String itemId;
        private final WindowedTrend trend = new WindowedTrend();
        private long amount;
        private long lastNonZeroTick;

        Track(String itemId) {
            this.itemId = itemId;
        }

        public String itemId() {
            return this.itemId;
        }

        public long amount() {
            return this.amount;
        }

        public WindowedTrend trend() {
            return this.trend;
        }
    }

    public record Row(String itemId,
                      long amount,
                      long[] delta,
                      float[] spread,
                      int[] coverage,
                      float[][] series) {
        public static final float[][] NO_SERIES = new float[0][];

        public long deltaFor(WindowedTrend.Window w) {
            return this.delta.length > w.ordinal() ? this.delta[w.ordinal()] : 0L;
        }

        public float spreadFor(WindowedTrend.Window w) {
            return this.spread.length > w.ordinal() ? this.spread[w.ordinal()] : 0.0f;
        }

        public int coverageFor(WindowedTrend.Window w) {
            return this.coverage.length > w.ordinal() ? this.coverage[w.ordinal()] : 0;
        }

        public boolean hasSeries() {
            return this.series.length == WindowedTrend.Window.values().length;
        }

        public float[] seriesFor(WindowedTrend.Window w) {
            return hasSeries() ? this.series[w.ordinal()] : new float[0];
        }
    }

    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final WindowedTrend totalTrend = new WindowedTrend();
    private final WindowedTrend energyTrend = new WindowedTrend();

    private boolean online;
    private long itemsStored;
    private long itemCapacity = -1L;
    private long fluidsStored;
    private int itemTypes;
    private int fluidTypes;
    private long energyStored;
    private long energyCapacity;
    private int patternCount;
    private int storageCount;
    private long lastReadTick;

    private final List<Row> rows = new ArrayList<>();
    private String focusedItemId = "";

    public void ingest(RefinedStorageCompatHelper.NetworkRead read, long gameTick) {
        this.online = read.online();
        this.itemsStored = read.itemsStored();
        this.itemCapacity = read.itemCapacity();
        this.fluidsStored = read.fluidsStored();
        this.itemTypes = read.itemTypes();
        this.fluidTypes = read.fluidTypes();
        this.energyStored = read.energyStored();
        this.energyCapacity = read.energyCapacity();
        this.patternCount = read.patternCount();
        this.storageCount = read.storageCount();
        this.lastReadTick = gameTick;

        this.totalTrend.record(read.itemsStored(), gameTick);
        this.energyTrend.record(read.energyStored(), gameTick);

        final Map<String, Long> present = new LinkedHashMap<>();
        for (final RefinedStorageCompatHelper.Entry entry : read.items()) {
            present.put(entry.id(), entry.amount());
        }
        for (final Map.Entry<String, Long> entry : present.entrySet()) {
            final Track track = this.tracks.computeIfAbsent(entry.getKey(), Track::new);
            track.amount = entry.getValue();
            track.lastNonZeroTick = gameTick;
            track.trend.record(entry.getValue(), gameTick);
        }
        this.tracks.entrySet().removeIf(e -> {
            final Track track = e.getValue();
            if (present.containsKey(e.getKey())) {
                return false;
            }
            track.amount = 0L;
            track.trend.record(0L, gameTick);
            return gameTick - track.lastNonZeroTick > EMPTY_ROW_TTL_TICKS;
        });
        pruneToLimit();
    }

    private void pruneToLimit() {
        if (this.tracks.size() <= MAX_ROWS) {
            return;
        }
        final List<Track> ranked = new ArrayList<>(this.tracks.values());
        ranked.sort(Comparator.comparingLong(Track::amount).reversed());
        for (int i = MAX_ROWS; i < ranked.size(); i++) {
            this.tracks.remove(ranked.get(i).itemId());
        }
    }

    public void markOffline(long gameTick) {
        this.online = false;
        this.lastReadTick = gameTick;
    }

    public boolean online() {
        return this.online;
    }

    public long itemsStored() {
        return this.itemsStored;
    }

    public long itemCapacity() {
        return this.itemCapacity;
    }

    public boolean hasCapacity() {
        return this.itemCapacity > 0L;
    }

    public float fillPercent() {
        return hasCapacity() ? (float) (this.itemsStored * 100.0 / this.itemCapacity) : -1.0f;
    }

    public long fluidsStored() {
        return this.fluidsStored;
    }

    public int itemTypes() {
        return this.itemTypes;
    }

    public int fluidTypes() {
        return this.fluidTypes;
    }

    public long energyStored() {
        return this.energyStored;
    }

    public long energyCapacity() {
        return this.energyCapacity;
    }

    public float energyPercent() {
        return this.energyCapacity > 0L
                ? (float) (this.energyStored * 100.0 / this.energyCapacity) : -1.0f;
    }

    public int patternCount() {
        return this.patternCount;
    }

    public int storageCount() {
        return this.storageCount;
    }

    public long lastReadTick() {
        return this.lastReadTick;
    }

    public WindowedTrend totalTrend() {
        return this.totalTrend;
    }

    public WindowedTrend energyTrend() {
        return this.energyTrend;
    }

    public Map<String, Track> tracks() {
        return this.tracks;
    }

    public List<Row> rows() {
        return this.rows;
    }

    public String focusedItemId() {
        return this.focusedItemId;
    }

    public Row row(String itemId) {
        for (final Row row : this.rows) {
            if (row.itemId().equals(itemId)) {
                return row;
            }
        }
        return null;
    }

    public CompoundTag save() {
        final CompoundTag tag = writeScalars();
        tag.put("Total", this.totalTrend.save());
        tag.put("Energy", this.energyTrend.save());
        final ListTag list = new ListTag();
        for (final Track track : this.tracks.values()) {
            final CompoundTag t = new CompoundTag();
            t.putString("Id", track.itemId);
            t.putLong("Amount", track.amount);
            t.putLong("Seen", track.lastNonZeroTick);
            t.put("Trend", track.trend.save());
            list.add(t);
        }
        tag.put("Tracks", list);
        return tag;
    }

    public static RsNetworkState load(CompoundTag tag) {
        final RsNetworkState state = new RsNetworkState();
        if (tag == null) {
            return state;
        }
        state.readScalars(tag);
        final ListTag list = tag.getList("Tracks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag t = list.getCompound(i);
            final String id = t.getString("Id");
            if (id.isEmpty()) {
                continue;
            }
            final Track track = new Track(id);
            track.amount = t.getLong("Amount");
            track.lastNonZeroTick = t.getLong("Seen");
            copyInto(track.trend, WindowedTrend.load(t.getCompound("Trend")));
            state.tracks.put(id, track);
        }
        copyInto(state.totalTrend, WindowedTrend.load(tag.getCompound("Total")));
        copyInto(state.energyTrend, WindowedTrend.load(tag.getCompound("Energy")));
        return state;
    }

    private static void copyInto(WindowedTrend target, WindowedTrend source) {
        target.clear();
        for (final WindowedTrend.Window w : WindowedTrend.Window.values()) {
            final long[] samples = source.series(w);
            long tick = 0L;
            for (final long value : samples) {
                target.record(value, tick);
                tick += w.intervalTicks();
            }
        }
        if (source.seeded()) {
            target.record(source.latest(), Long.MAX_VALUE / 4L);
        }
    }

    public CompoundTag saveSync(String focusedItemId) {
        final CompoundTag tag = writeScalars();
        tag.putString("Focus", focusedItemId == null ? "" : focusedItemId);
        tag.put("TotalSeries", seriesTag(this.totalTrend));

        final List<Track> ranked = new ArrayList<>(this.tracks.values());
        ranked.sort(Comparator.comparingLong(Track::amount).reversed());

        final ListTag list = new ListTag();
        for (final Track track : ranked) {
            final CompoundTag t = new CompoundTag();
            t.putString("Id", track.itemId);
            t.putLong("N", track.amount);
            final WindowedTrend.Window[] windows = WindowedTrend.Window.values();
            final long[] deltas = new long[windows.length];
            final float[] spreads = new float[windows.length];
            final int[] coverage = new int[windows.length];
            for (int i = 0; i < windows.length; i++) {
                deltas[i] = track.trend.delta(windows[i]);
                spreads[i] = (float) track.trend.stdDev(windows[i]);
                coverage[i] = track.trend.coverageSeconds(windows[i]);
            }
            t.putLongArray("D", deltas);
            t.putIntArray("C", coverage);
            final ListTag spreadList = new ListTag();
            for (final float s : spreads) {
                spreadList.add(net.minecraft.nbt.FloatTag.valueOf(s));
            }
            t.put("S", spreadList);
            if (track.itemId.equals(focusedItemId)) {
                t.put("Series", seriesTag(track.trend));
            }
            list.add(t);
        }
        tag.put("Rows", list);
        return tag;
    }

    public static RsNetworkState loadSync(CompoundTag tag) {
        final RsNetworkState state = new RsNetworkState();
        if (tag == null) {
            return state;
        }
        state.readScalars(tag);
        state.focusedItemId = tag.getString("Focus");
        state.syncedTotalSeries = readSeries(tag.getCompound("TotalSeries"));
        final ListTag list = tag.getList("Rows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag t = list.getCompound(i);
            final String id = t.getString("Id");
            if (id.isEmpty()) {
                continue;
            }
            final ListTag spreadList = t.getList("S", Tag.TAG_FLOAT);
            final float[] spreads = new float[spreadList.size()];
            for (int s = 0; s < spreads.length; s++) {
                spreads[s] = spreadList.getFloat(s);
            }
            state.rows.add(new Row(id, t.getLong("N"), t.getLongArray("D"), spreads,
                    t.getIntArray("C"),
                    t.contains("Series") ? readSeries(t.getCompound("Series")) : Row.NO_SERIES));
        }
        return state;
    }

    private float[][] syncedTotalSeries = Row.NO_SERIES;

    public float[] totalSeries(WindowedTrend.Window window) {
        return this.syncedTotalSeries.length == WindowedTrend.Window.values().length
                ? this.syncedTotalSeries[window.ordinal()] : new float[0];
    }

    private static CompoundTag seriesTag(WindowedTrend trend) {
        final CompoundTag tag = new CompoundTag();
        for (final WindowedTrend.Window w : WindowedTrend.Window.values()) {
            final float[] plot = trend.plot(w);
            final ListTag list = new ListTag();
            for (final float v : plot) {
                list.add(net.minecraft.nbt.FloatTag.valueOf(v));
            }
            tag.put(w.name(), list);
        }
        return tag;
    }

    private static float[][] readSeries(CompoundTag tag) {
        final WindowedTrend.Window[] windows = WindowedTrend.Window.values();
        final float[][] out = new float[windows.length][];
        for (int i = 0; i < windows.length; i++) {
            final ListTag list = tag.getList(windows[i].name(), Tag.TAG_FLOAT);
            final float[] values = new float[list.size()];
            for (int s = 0; s < values.length; s++) {
                values[s] = list.getFloat(s);
            }
            out[i] = values;
        }
        return out;
    }

    private CompoundTag writeScalars() {
        final CompoundTag tag = new CompoundTag();
        tag.putBoolean("On", this.online);
        tag.putLong("Stored", this.itemsStored);
        tag.putLong("Cap", this.itemCapacity);
        tag.putLong("Fluid", this.fluidsStored);
        tag.putInt("Types", this.itemTypes);
        tag.putInt("FluidTypes", this.fluidTypes);
        tag.putLong("FE", this.energyStored);
        tag.putLong("FECap", this.energyCapacity);
        tag.putInt("Patterns", this.patternCount);
        tag.putInt("Disks", this.storageCount);
        tag.putLong("Tick", this.lastReadTick);
        return tag;
    }

    private void readScalars(CompoundTag tag) {
        this.online = tag.getBoolean("On");
        this.itemsStored = tag.getLong("Stored");
        this.itemCapacity = tag.contains("Cap") ? tag.getLong("Cap") : -1L;
        this.fluidsStored = tag.getLong("Fluid");
        this.itemTypes = tag.getInt("Types");
        this.fluidTypes = tag.getInt("FluidTypes");
        this.energyStored = tag.getLong("FE");
        this.energyCapacity = tag.getLong("FECap");
        this.patternCount = tag.getInt("Patterns");
        this.storageCount = tag.getInt("Disks");
        this.lastReadTick = tag.getLong("Tick");
    }
}
