package com.createstats.pa;

import net.minecraft.nbt.CompoundTag;

public record DeviceId(String typeKey, int number) {
    public static final DeviceId NONE = new DeviceId("", 0);

    public boolean isPresent() {
        return this.number > 0 && !this.typeKey.isEmpty();
    }

    public String label() {
        return this.isPresent() ? "ID " + this.number : "unassigned";
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Type", this.typeKey);
        tag.putInt("N", this.number);
        return tag;
    }

    public static DeviceId load(CompoundTag tag) {
        if (tag == null || !tag.contains("Type")) {
            return NONE;
        }
        return new DeviceId(tag.getString("Type"), tag.getInt("N"));
    }
}
