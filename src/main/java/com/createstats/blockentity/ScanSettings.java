package com.createstats.blockentity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class ScanSettings
{
    private ScanMode scanMode;
    private int scanRadius;
    private boolean radiusUnlocked;
    private int autoIntervalTicks;
    private long lastScanTick;
    private final Set<String> disabledSources;
    private final LinkedHashMap<String, DiscoveredSource> discoveredSources;

    public ScanSettings() {
        this.scanMode = ScanMode.MANUAL;
        this.scanRadius = 8;
        this.radiusUnlocked = false;
        this.autoIntervalTicks = 100;
        this.lastScanTick = 0L;
        this.disabledSources = new HashSet<String>();
        this.discoveredSources = new LinkedHashMap<String, DiscoveredSource>();
    }

    public ScanMode getScanMode() {
        return this.scanMode;
    }

    public void setScanMode(ScanMode mode) {
        this.scanMode = mode;
    }

    public int getScanRadius() {
        return this.scanRadius;
    }

    public void setScanRadius(int radius) {
        final int max = this.radiusUnlocked ? 512 : 64;
        this.scanRadius = Math.max(4, Math.min(max, radius));
    }

    public boolean isRadiusUnlocked() {
        return this.radiusUnlocked;
    }

    public void setRadiusUnlocked(boolean unlocked) {
        this.radiusUnlocked = unlocked;
        if (!unlocked && this.scanRadius > 64) {
            this.scanRadius = 64;
        }
    }

    public int getAutoIntervalTicks() {
        return this.autoIntervalTicks;
    }

    public void setAutoIntervalTicks(int ticks) {
        this.autoIntervalTicks = Math.max(20, Math.min(1200, ticks));
    }

    public float getAutoIntervalSeconds() {
        return this.autoIntervalTicks / 20.0f;
    }

    public void setAutoIntervalSeconds(float seconds) {
        this.setAutoIntervalTicks(Math.round(seconds * 20.0f));
    }

    public long getLastScanTick() {
        return this.lastScanTick;
    }

    public void setLastScanTick(long tick) {
        this.lastScanTick = tick;
    }

    public Set<String> getDisabledSources() {
        return this.disabledSources;
    }

    public boolean isSourceEnabled(String sourceId) {
        return !this.disabledSources.contains(sourceId);
    }

    public void setSourceEnabled(String sourceId, boolean enabled) {
        if (enabled) {
            this.disabledSources.remove(sourceId);
        }
        else {
            this.disabledSources.add(sourceId);
        }
    }

    public void toggleSource(String sourceId) {
        if (this.disabledSources.contains(sourceId)) {
            this.disabledSources.remove(sourceId);
        }
        else {
            this.disabledSources.add(sourceId);
        }
    }

    public LinkedHashMap<String, DiscoveredSource> getDiscoveredSources() {
        return this.discoveredSources;
    }

    public void clearDiscoveredSources() {
        this.discoveredSources.clear();
    }

    public DiscoveredSource getOrCreateSource(String sourceId, BlockPos pos, String sourceType) {
        final DiscoveredSource existing = this.discoveredSources.get(sourceId);
        if (existing != null) {
            existing.pos = pos;
            existing.sourceType = sourceType;
            return existing;
        }
        final DiscoveredSource created = new DiscoveredSource(sourceId, pos, sourceType);
        this.discoveredSources.put(sourceId, created);
        return created;
    }

    public List<DiscoveredSource> getEnabledSources() {
        final List<DiscoveredSource> list = new ArrayList<DiscoveredSource>();
        for (DiscoveredSource ds : this.discoveredSources.values()) {
            if (this.isSourceEnabled(ds.sourceId)) {
                list.add(ds);
            }
        }
        return list;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Mode", this.scanMode.name());
        tag.putInt("Radius", this.scanRadius);
        tag.putBoolean("Unlocked", this.radiusUnlocked);
        tag.putInt("Interval", this.autoIntervalTicks);
        tag.putLong("LastScan", this.lastScanTick);
        final ListTag disabled = new ListTag();
        for (String id : this.disabledSources) {
            final CompoundTag entry = new CompoundTag();
            entry.putString("id", id);
            disabled.add(entry);
        }
        tag.put("Disabled", (Tag)disabled);
        final ListTag srcList = new ListTag();
        for (DiscoveredSource ds : this.discoveredSources.values()) {
            srcList.add(ds.save());
        }
        tag.put("Sources", (Tag)srcList);
        return tag;
    }

    public static ScanSettings load(CompoundTag tag) {
        final ScanSettings s = new ScanSettings();
        try {
            s.scanMode = ScanMode.valueOf(tag.getString("Mode"));
        }
        catch (Exception ignored) {
            s.scanMode = ScanMode.MANUAL;
        }
        s.scanRadius = tag.getInt("Radius");
        if (s.scanRadius < 4) {
            s.scanRadius = 8;
        }
        s.radiusUnlocked = tag.getBoolean("Unlocked");
        s.autoIntervalTicks = tag.getInt("Interval");
        if (s.autoIntervalTicks < 20) {
            s.autoIntervalTicks = 100;
        }
        s.lastScanTick = tag.getLong("LastScan");
        s.disabledSources.clear();
        if (tag.contains("Disabled", 9)) {
            final ListTag disabled = tag.getList("Disabled", 10);
            for (int i = 0; i < disabled.size(); ++i) {
                s.disabledSources.add(disabled.getCompound(i).getString("id"));
            }
        }
        s.discoveredSources.clear();
        if (tag.contains("Sources", 9)) {
            final ListTag srcList = tag.getList("Sources", 10);
            for (int i = 0; i < srcList.size(); ++i) {
                final DiscoveredSource ds = DiscoveredSource.load(srcList.getCompound(i));
                s.discoveredSources.put(ds.sourceId, ds);
            }
        }
        return s;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum((Enum)this.scanMode);
        buf.writeVarInt(this.scanRadius);
        buf.writeBoolean(this.radiusUnlocked);
        buf.writeVarInt(this.autoIntervalTicks);
    }

    public static ScanSettings decode(FriendlyByteBuf buf) {
        final ScanSettings s = new ScanSettings();
        s.scanMode = (ScanMode)buf.readEnum((Class)ScanMode.class);
        s.scanRadius = buf.readVarInt();
        s.radiusUnlocked = buf.readBoolean();
        s.autoIntervalTicks = buf.readVarInt();
        return s;
    }

    public enum ScanMode
    {
        MANUAL,
        AUTO;
    }

    public static class DiscoveredSource
    {
        public final String sourceId;
        public BlockPos pos;
        public String sourceType;
        public boolean active;
        public long lastSeenTick;
        public final List<ChannelSnapshot> channels;

        public DiscoveredSource(String sourceId, BlockPos pos, String sourceType) {
            this.channels = new ArrayList<ChannelSnapshot>();
            this.sourceId = sourceId;
            this.pos = pos;
            this.sourceType = sourceType;
            this.active = true;
            this.lastSeenTick = 0L;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.sourceId);
            tag.putLong("Pos", this.pos.asLong());
            tag.putString("Type", this.sourceType);
            tag.putBoolean("Active", this.active);
            tag.putLong("Seen", this.lastSeenTick);
            final ListTag chList = new ListTag();
            for (ChannelSnapshot ch : this.channels) {
                chList.add(ch.save());
            }
            tag.put("Ch", (Tag)chList);
            return tag;
        }

        public static DiscoveredSource load(CompoundTag tag) {
            final DiscoveredSource ds = new DiscoveredSource(tag.getString("Id"), BlockPos.of(tag.getLong("Pos")), tag.getString("Type"));
            ds.active = tag.getBoolean("Active");
            ds.lastSeenTick = tag.getLong("Seen");
            if (tag.contains("Ch", 9)) {
                final ListTag chList = tag.getList("Ch", 10);
                for (int i = 0; i < chList.size(); ++i) {
                    ds.channels.add(ChannelSnapshot.load(chList.getCompound(i)));
                }
            }
            return ds;
        }
    }

    public static class ChannelSnapshot
    {
        public final String channelId;
        public String label;
        public float latestValue;

        public ChannelSnapshot(String channelId, String label, float latestValue) {
            this.channelId = channelId;
            this.label = label;
            this.latestValue = latestValue;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Id", this.channelId);
            tag.putString("L", this.label);
            tag.putFloat("V", this.latestValue);
            return tag;
        }

        public static ChannelSnapshot load(CompoundTag tag) {
            return new ChannelSnapshot(tag.getString("Id"), tag.getString("L"), tag.getFloat("V"));
        }
    }
}
