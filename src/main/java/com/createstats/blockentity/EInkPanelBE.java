package com.createstats.blockentity;

import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// two kinds of source: create sends text lines, ours sends numbers
public class EInkPanelBE extends BlockEntity implements IAutoDataReceiver {
    private static final int STALE_AFTER_TICKS = 200;

    private static final int MAX_READINGS = 24;

    public record Reading(String id, String label, float value, MetricType type) {}

    private final List<Reading> readings = new ArrayList<>();
    private String sourceType = "";
    private int selected;
    private long lastDataTick = Long.MIN_VALUE;
    private boolean syncPending;
    @Nullable
    private BlockPos sourcePos;

    public EInkPanelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide()) {
            com.createstats.renderer.EInkPanelTextureManager.disposeFor(worldPosition);
        }
    }

    public void replaceAutoData(BlockPos from, String type, Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final List<Reading> next = new ArrayList<>();
        for (Map.Entry<String, InstrumentSourceHelper.MetricSample> entry : metrics.entrySet()) {
            if (next.size() >= MAX_READINGS) {
                break;
            }
            final InstrumentSourceHelper.MetricSample sample = entry.getValue();
            next.add(new Reading(entry.getKey(), sample.label(), sample.value(), sample.type()));
        }
        if (next.isEmpty()) {
            return;
        }
        this.sourcePos = from;
        this.sourceType = type == null ? "" : type;
        applyReadings(next);
    }

    public void acceptTextLine(int line, String text, float value, MetricType type, String sourceLabel) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final String id = "line" + line;
        final String label = text == null || text.isBlank() ? sourceLabel : trimLabel(text);
        final List<Reading> next = new ArrayList<>(readings);
        int at = -1;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(id)) {
                at = i;
                break;
            }
        }
        final Reading reading = new Reading(id, label, value, type);
        if (at >= 0) {
            next.set(at, reading);
        } else if (next.size() < MAX_READINGS) {
            next.add(reading);
        } else {
            return;
        }
        this.sourceType = sourceLabel == null ? "" : sourceLabel;
        applyReadings(next);
    }

    @Override
    public void onAutoData(BlockPos from, String type, String metricId, String label, float value, MetricType metricType) {
        if (level == null || level.isClientSide()) {
            return;
        }
        this.sourcePos = from;
        this.sourceType = type == null ? "" : type;
        final List<Reading> next = new ArrayList<>(readings);
        int at = -1;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(metricId)) {
                at = i;
                break;
            }
        }
        final Reading reading = new Reading(metricId, label, value, metricType);
        if (at >= 0) {
            next.set(at, reading);
        } else if (next.size() < MAX_READINGS) {
            next.add(reading);
        } else {
            return;
        }
        applyReadings(next);
    }

    @Override
    public void setSourceType(String type) {
        this.sourceType = type == null ? "" : type;
    }

    @Override
    public SourceConfig registerOrUpdateSource(BlockPos pos, String type) {
        this.sourcePos = pos;
        this.sourceType = type == null ? "" : type;
        final SourceConfig config = new SourceConfig("eink_" + pos.asLong(), pos, this.sourceType);
        if (level != null) {
            config.setLastDataTick(level.getGameTime());
        }
        return config;
    }

    private void applyReadings(List<Reading> next) {
        final boolean changed = !next.equals(readings);
        readings.clear();
        readings.addAll(next);
        if (selected >= readings.size()) {
            selected = 0;
        }
        lastDataTick = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (changed) {
            syncToClient();
        } else {
            setChanged();
        }
    }

    public boolean cycleMetric() {
        if (readings.size() < 2) {
            return false;
        }
        selected = (selected + 1) % readings.size();
        syncToClient();
        return true;
    }

    @Nullable
    public Reading selectedReading() {
        return selected >= 0 && selected < readings.size() ? readings.get(selected) : null;
    }

    public int readingCount() {
        return readings.size();
    }

    public int selectedIndex() {
        return selected;
    }

    public String getSourceType() {
        return sourceType;
    }

    public boolean isStale() {
        if (level == null || lastDataTick == Long.MIN_VALUE) {
            return true;
        }
        return level.getGameTime() - lastDataTick > STALE_AFTER_TICKS;
    }

    private static String trimLabel(String text) {
        final String cleaned = text.replaceAll("§[0-9a-fk-or]", "").trim();
        final int colon = cleaned.indexOf(':');
        final String label = colon > 0 ? cleaned.substring(0, colon).trim() : cleaned;
        return label.length() > 24 ? label.substring(0, 24) : label;
    }

    public void tick() {
        if (syncPending && level != null) {
            syncPending = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void syncToClient() {
        setChanged();
        syncPending = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("SourceType", sourceType);
        tag.putInt("Selected", selected);
        if (sourcePos != null) {
            tag.putLong("SourcePos", sourcePos.asLong());
        }
        final ListTag list = new ListTag();
        for (Reading reading : readings) {
            final CompoundTag entry = new CompoundTag();
            entry.putString("Id", reading.id());
            entry.putString("Label", reading.label() == null ? "" : reading.label());
            entry.putFloat("Value", reading.value());
            entry.putString("Type", (reading.type() == null ? MetricType.GENERIC : reading.type()).name());
            list.add(entry);
        }
        tag.put("Readings", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sourceType = tag.getString("SourceType");
        selected = tag.getInt("Selected");
        sourcePos = tag.contains("SourcePos") ? BlockPos.of(tag.getLong("SourcePos")) : null;
        readings.clear();
        final ListTag list = tag.getList("Readings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            readings.add(new Reading(entry.getString("Id"), entry.getString("Label"),
                    entry.getFloat("Value"), parseType(entry.getString("Type"))));
        }
        if (selected >= readings.size()) {
            selected = 0;
        }
    }

    private static MetricType parseType(String name) {
        try {
            return MetricType.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return MetricType.GENERIC;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}
