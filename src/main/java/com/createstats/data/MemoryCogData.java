package com.createstats.data;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class MemoryCogData {

    private static final String TAG_KEY = "MemoryCogData";
    private static final String META_KEY = "MemoryCogMeta";
    private static final int SCHEMA_VERSION = 1;

    public record SnapshotMetadata(int schemaVersion, String sourceLabel, String sourceType, String dimensionId,
                                   @Nullable BlockPos sourcePos, long capturedGameTime, String lastDisplayText) {

        public static SnapshotMetadata empty() {
            return new SnapshotMetadata(SCHEMA_VERSION, "", "", "", null, 0L, "");
        }

        public boolean hasSourceDetails() {
            return !this.sourceLabel.isEmpty() || !this.sourceType.isEmpty();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("SchemaVersion", this.schemaVersion);
            tag.putString("SourceLabel", this.sourceLabel);
            tag.putString("SourceType", this.sourceType);
            tag.putString("DimensionId", this.dimensionId);
            if (this.sourcePos != null) {
                tag.putLong("SourcePos", this.sourcePos.asLong());
            }
            tag.putLong("CapturedGameTime", this.capturedGameTime);
            tag.putString("LastDisplayText", this.lastDisplayText);
            return tag;
        }

        public static SnapshotMetadata load(@Nullable CompoundTag tag) {
            if (tag == null || tag.isEmpty()) {
                return empty();
            }
            BlockPos sourcePos = tag.contains("SourcePos") ? BlockPos.of(tag.getLong("SourcePos")) : null;
            return new SnapshotMetadata(
                tag.contains("SchemaVersion") ? tag.getInt("SchemaVersion") : SCHEMA_VERSION,
                tag.getString("SourceLabel"),
                tag.getString("SourceType"),
                tag.getString("DimensionId"),
                sourcePos,
                tag.getLong("CapturedGameTime"),
                tag.getString("LastDisplayText"));
        }
    }

    public static void writeToStack(ItemStack stack, TimeSeriesStore store) {
        writeToStack(stack, store, SnapshotMetadata.empty());
    }

    public static void writeToStack(ItemStack stack, TimeSeriesStore store, @Nullable SnapshotMetadata metadata) {
        SnapshotMetadata finalMetadata = metadata != null ? metadata : SnapshotMetadata.empty();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put(TAG_KEY, store.save());
            if (finalMetadata.hasSourceDetails() || !finalMetadata.dimensionId().isEmpty() || finalMetadata.sourcePos() != null
                || finalMetadata.capturedGameTime() > 0L || !finalMetadata.lastDisplayText().isEmpty()) {
                tag.put(META_KEY, finalMetadata.save());
            } else {
                tag.remove(META_KEY);
            }
        });
    }

    @Nullable
    public static TimeSeriesStore readFromStack(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_KEY, 10)) {
            return null;
        }
        return TimeSeriesStore.load(tag.getCompound(TAG_KEY));
    }

    public static boolean hasData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains(TAG_KEY, 10);
    }

    public static SnapshotMetadata getMetadata(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return SnapshotMetadata.empty();
        }
        CompoundTag tag = customData.copyTag();
        return tag.contains(META_KEY, 10) ? SnapshotMetadata.load(tag.getCompound(META_KEY)) : SnapshotMetadata.empty();
    }

    public static int getSampleCount(ItemStack stack) {
        TimeSeriesStore store = readFromStack(stack);
        return store != null ? store.getSampleCount() : 0;
    }

    public static MetricType getMetricType(ItemStack stack) {
        TimeSeriesStore store = readFromStack(stack);
        return store != null ? store.getMetricType() : MetricType.GENERIC;
    }
}
