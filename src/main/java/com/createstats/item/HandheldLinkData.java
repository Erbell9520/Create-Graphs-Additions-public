package com.createstats.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class HandheldLinkData {

    public enum TargetType {
        NONE(""),
        STATS_DISPLAY("Stats Display"),
        STATS_RECORDER("Stats Recorder"),
        DISPATCH_BOARD("Cargo Dashboard");

        private final String displayName;

        TargetType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    private TargetType targetType = TargetType.NONE;
    @Nullable
    private ResourceLocation dimensionId;
    @Nullable
    private BlockPos targetPos;
    private String targetName = "";

    public boolean isLinked() {
        return this.targetType != TargetType.NONE && this.dimensionId != null && this.targetPos != null;
    }

    public void clear() {
        this.targetType = TargetType.NONE;
        this.dimensionId = null;
        this.targetPos = null;
        this.targetName = "";
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TargetType", this.targetType.name());
        if (this.dimensionId != null) {
            tag.putString("Dimension", this.dimensionId.toString());
        }
        if (this.targetPos != null) {
            tag.putLong("TargetPos", this.targetPos.asLong());
        }
        tag.putString("TargetName", this.targetName);
        return tag;
    }

    public static HandheldLinkData load(CompoundTag tag) {
        HandheldLinkData data = new HandheldLinkData();
        if (tag == null || tag.isEmpty()) {
            return data;
        }
        try {
            data.targetType = TargetType.valueOf(tag.getString("TargetType"));
        } catch (IllegalArgumentException ignored) {
            data.targetType = TargetType.NONE;
        }
        if (tag.contains("Dimension")) {
            data.dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        }
        if (tag.contains("TargetPos")) {
            data.targetPos = BlockPos.of(tag.getLong("TargetPos"));
        }
        data.targetName = tag.getString("TargetName");
        if (data.targetName.isEmpty()) {
            data.targetName = data.targetType.getDisplayName();
        }
        if (!data.isLinked()) {
            data.clear();
        }
        return data;
    }

    public TargetType getTargetType() {
        return this.targetType;
    }

    public void setTargetType(TargetType targetType) {
        this.targetType = targetType != null ? targetType : TargetType.NONE;
    }

    @Nullable
    public ResourceLocation getDimensionId() {
        return this.dimensionId;
    }

    public void setDimensionId(@Nullable ResourceLocation dimensionId) {
        this.dimensionId = dimensionId;
    }

    @Nullable
    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public void setTargetPos(@Nullable BlockPos targetPos) {
        this.targetPos = targetPos;
    }

    public String getTargetName() {
        return this.targetName.isEmpty() ? this.targetType.getDisplayName() : this.targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName != null ? targetName : "";
    }
}
