package com.createstats.flight.workbench;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record FccDevice(
        UUID id,
        String label,
        FccDeviceCategory category,
        BlockPos primaryPos,
        @Nullable BlockPos secondaryPos,
        boolean enabled,
        int colorArgb,
        CompoundTag config
) {
    public FccDevice {
        if (id == null) throw new IllegalArgumentException("id");
        if (label == null || label.isEmpty()) throw new IllegalArgumentException("label");
        if (category == null) throw new IllegalArgumentException("category");
        if (primaryPos == null) throw new IllegalArgumentException("primaryPos");
        config = config == null ? new CompoundTag() : config.copy();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Label", label);
        tag.putString("Category", category.name());
        tag.putLong("Primary", primaryPos.asLong());
        if (secondaryPos != null) tag.putLong("Secondary", secondaryPos.asLong());
        tag.putBoolean("Enabled", enabled);
        tag.putInt("Color", colorArgb);
        if (!config.isEmpty()) tag.put("Config", config.copy());
        return tag;
    }

    @Nullable
    public static FccDevice fromNbt(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id") || !tag.contains("Primary")) return null;
        UUID id = tag.getUUID("Id");
        String label = tag.getString("Label");
        if (label == null || label.isEmpty()) label = "Device";
        FccDeviceCategory category = FccDeviceCategory.safeValueOf(tag.getString("Category"), FccDeviceCategory.REGULAR_PROP);
        BlockPos primary = BlockPos.of(tag.getLong("Primary"));
        BlockPos secondary = tag.contains("Secondary") ? BlockPos.of(tag.getLong("Secondary")) : null;
        boolean enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        int color = tag.contains("Color") ? tag.getInt("Color") : 0xFF39C8FF;
        CompoundTag config = tag.contains("Config") ? tag.getCompound("Config").copy() : new CompoundTag();
        return new FccDevice(id, label, category, primary, secondary, enabled, color, config);
    }

    public FccDevice withCategory(FccDeviceCategory newCategory) {
        return new FccDevice(id, label, newCategory, primaryPos, secondaryPos, enabled, colorArgb, config);
    }

    public FccDevice withConfig(CompoundTag newConfig) {
        return new FccDevice(id, label, category, primaryPos, secondaryPos, enabled, colorArgb, newConfig);
    }
}
