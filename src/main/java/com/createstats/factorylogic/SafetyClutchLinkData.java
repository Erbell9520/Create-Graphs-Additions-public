package com.createstats.factorylogic;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class SafetyClutchLinkData {

    public enum TargetType {
        SAFETY_CLUTCH("Safety Clutch"),
        CONTROL_CLUTCH("Control Clutch"),
        KINETIC_FLYWHEEL("Kinetic Flywheel");

        private final String displayName;

        TargetType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public static TargetType fromName(@Nullable String name) {
            if (name == null || name.isBlank()) {
                return SAFETY_CLUTCH;
            }
            try {
                return TargetType.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return SAFETY_CLUTCH;
            }
        }
    }

    @Nullable
    private ResourceLocation dimensionId;
    @Nullable
    private BlockPos clutchPos;
    private TargetType targetType = TargetType.SAFETY_CLUTCH;

    @Nullable
    public ResourceLocation getDimensionId() {
        return this.dimensionId;
    }

    public void setDimensionId(@Nullable ResourceLocation dimensionId) {
        this.dimensionId = dimensionId;
    }

    @Nullable
    public BlockPos getClutchPos() {
        return this.clutchPos;
    }

    public void setClutchPos(@Nullable BlockPos clutchPos) {
        this.clutchPos = clutchPos;
    }

    public TargetType getTargetType() {
        return this.targetType;
    }

    public void setTargetType(@Nullable TargetType targetType) {
        this.targetType = targetType == null ? TargetType.SAFETY_CLUTCH : targetType;
    }

    public String getTargetDisplayName() {
        return this.targetType.getDisplayName();
    }

    public boolean isLinked() {
        return this.dimensionId != null && this.clutchPos != null;
    }

    public void clear() {
        this.dimensionId = null;
        this.clutchPos = null;
        this.targetType = TargetType.SAFETY_CLUTCH;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TargetType", this.targetType.name());
        if (this.dimensionId != null) {
            tag.putString("DimensionId", this.dimensionId.toString());
        }
        if (this.clutchPos != null) {
            tag.putLong("ClutchPos", this.clutchPos.asLong());
        }
        return tag;
    }

    public static SafetyClutchLinkData load(CompoundTag tag) {
        SafetyClutchLinkData data = new SafetyClutchLinkData();
        data.targetType = TargetType.fromName(tag.contains("TargetType") ? tag.getString("TargetType") : null);
        if (tag.contains("DimensionId")) {
            data.dimensionId = ResourceLocation.tryParse(tag.getString("DimensionId"));
        }
        if (tag.contains("ClutchPos")) {
            data.clutchPos = BlockPos.of(tag.getLong("ClutchPos"));
        }
        return data;
    }
}
