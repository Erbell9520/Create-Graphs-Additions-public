package com.createstats.vccmk3.model;

import com.createstats.vccmk3.hardware.DeviceCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record VccDevice(
        UUID id,
        String label,
        DeviceCategory category,
        BlockPos actuatorPos,
        @Nullable BlockPos governorPos,
        boolean enabled,
        int colorArgb,
        int cardRow,
        CompoundTag config,
        DeviceGeometry geometry
) {
    public VccDevice {
        if (id == null) throw new IllegalArgumentException("id");
        if (category == null) throw new IllegalArgumentException("category");
        if (actuatorPos == null) throw new IllegalArgumentException("actuatorPos");
        if (label == null || label.isBlank()) label = category.label();
        config = config == null ? new CompoundTag() : config.copy();
        if (geometry == null) geometry = DeviceGeometry.UNKNOWN;
    }

    public static VccDevice fromRow(UUID id, DeviceCategory category, BlockPos actuator,
                                    @Nullable BlockPos governor, int cardRow, int index) {
        return new VccDevice(id, category.shortLabel() + " " + index, category, actuator, governor,
                true, category.color(), cardRow, new CompoundTag(), DeviceGeometry.UNKNOWN);
    }

    public boolean awaitingGovernor() {
        return category.requiresGovernor() && governorPos == null;
    }

    public VccDevice withLabel(String newLabel) {
        return new VccDevice(id, newLabel, category, actuatorPos, governorPos, enabled, colorArgb, cardRow, config, geometry);
    }

    public VccDevice withEnabled(boolean newEnabled) {
        return new VccDevice(id, label, category, actuatorPos, governorPos, newEnabled, colorArgb, cardRow, config, geometry);
    }

    public VccDevice withCategory(DeviceCategory newCategory) {
        return new VccDevice(id, label, newCategory, actuatorPos, governorPos, enabled, colorArgb, cardRow, config, geometry);
    }

    public VccDevice withPositions(BlockPos newActuator, @Nullable BlockPos newGovernor) {
        return new VccDevice(id, label, category, newActuator, newGovernor, enabled, colorArgb, cardRow, config, geometry);
    }

    public VccDevice withGeometry(DeviceGeometry newGeometry) {
        return new VccDevice(id, label, category, actuatorPos, governorPos, enabled, colorArgb, cardRow, config, newGeometry);
    }

    public VccDevice withCardRow(int newCardRow) {
        return new VccDevice(id, label, category, actuatorPos, governorPos, enabled, colorArgb, newCardRow, config, geometry);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Label", label);
        tag.putString("Category", category.name());
        tag.putLong("Actuator", actuatorPos.asLong());
        if (governorPos != null) tag.putLong("Governor", governorPos.asLong());
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Color", colorArgb);
        tag.putInt("CardRow", cardRow);
        if (!config.isEmpty()) tag.put("Config", config.copy());
        return tag;
    }

    @Nullable
    public static VccDevice fromNbt(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id") || !tag.contains("Actuator")) return null;
        DeviceCategory category = DeviceCategory.safeValueOf(tag.getString("Category"), DeviceCategory.PROPELLER);
        return new VccDevice(
                tag.getUUID("Id"),
                tag.getString("Label"),
                category,
                BlockPos.of(tag.getLong("Actuator")),
                tag.contains("Governor") ? BlockPos.of(tag.getLong("Governor")) : null,
                !tag.contains("Enabled") || tag.getBoolean("Enabled"),
                tag.contains("Color") ? tag.getInt("Color") : category.color(),
                tag.contains("CardRow") ? tag.getInt("CardRow") : -1,
                tag.contains("Config") ? tag.getCompound("Config") : new CompoundTag(),

                DeviceGeometry.UNKNOWN);
    }
}
