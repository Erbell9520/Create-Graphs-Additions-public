package com.createstats.flight.workbench;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record FccGroup(
        UUID id,
        String label,
        int colorArgb,
        List<UUID> deviceIds,
        boolean hidden,
    String note,
    String zoneKey,
    boolean systemOwned
) {

    public FccGroup {
        if (id == null) throw new IllegalArgumentException("id");
        if (label == null || label.isEmpty()) throw new IllegalArgumentException("label");
        deviceIds = List.copyOf(deviceIds == null ? List.of() : deviceIds);
        note = note == null ? "" : note;
        zoneKey = zoneKey == null ? "" : zoneKey;
    }

    public FccGroup(UUID id, String label, int colorArgb, List<UUID> deviceIds, boolean hidden, String note) {
        this(id, label, colorArgb, deviceIds, hidden, note, "", false);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Label", label);
        tag.putInt("Color", colorArgb);
        tag.putBoolean("Hidden", hidden);
        if (!note.isEmpty()) tag.putString("Note", note);
        if (!zoneKey.isEmpty()) tag.putString("ZoneKey", zoneKey);
        tag.putBoolean("SystemOwned", systemOwned);
        if (!deviceIds.isEmpty()) {
            ListTag ids = new ListTag();
            for (UUID deviceId : deviceIds) ids.add(StringTag.valueOf(deviceId.toString()));
            tag.put("DeviceIds", ids);
        }
        return tag;
    }

    @Nullable
    public static FccGroup fromNbt(@Nullable CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) return null;
        UUID id = tag.getUUID("Id");
        String label = tag.getString("Label");
        if (label == null || label.isEmpty()) label = "Group";
        int color = tag.contains("Color") ? tag.getInt("Color") : 0xFFD4AF37;
        boolean hidden = tag.getBoolean("Hidden");
        String note = tag.getString("Note");
        String zoneKey = tag.getString("ZoneKey");
        boolean systemOwned = tag.getBoolean("SystemOwned");
        Set<UUID> orderedIds = new LinkedHashSet<>();
        if (tag.contains("DeviceIds", Tag.TAG_LIST)) {
            ListTag ids = tag.getList("DeviceIds", Tag.TAG_STRING);
            for (int i = 0; i < ids.size(); i++) {
                try {
                    orderedIds.add(UUID.fromString(ids.getString(i)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new FccGroup(id, label, color, new ArrayList<>(orderedIds), hidden, note, zoneKey, systemOwned);
    }

    public FccGroup withDeviceIds(List<UUID> newDeviceIds) {
        return new FccGroup(id, label, colorArgb, newDeviceIds, hidden, note, zoneKey, systemOwned);
    }
}