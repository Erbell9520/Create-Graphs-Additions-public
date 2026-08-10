package com.createstats.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** [1] */
public final class KineticMultimeterData {

    private static final String KEY = "KMReading";

    public static final byte ROLE_NONE = 0;
    public static final byte ROLE_SOURCE = 1;
    public static final byte ROLE_CONSUMER = 2;

    public static final byte STATE_NONET = 0;
    public static final byte STATE_OK = 1;
    public static final byte STATE_WARN = 2;
    public static final byte STATE_OVER = 3;

    public record Reading(long time, float rpm, float stressUsed, float stressCap, byte role,
                          String blockId, byte state) {

        public static Reading empty() {
            return new Reading(0L, 0f, 0f, 0f, ROLE_NONE, "", STATE_NONET);
        }

        public boolean hasNetwork() {
            return state != STATE_NONET && stressCap > 0f;
        }

        public float loadPercent() {
            return stressCap > 0f ? (stressUsed / stressCap) * 100f : 0f;
        }
    }

    private KineticMultimeterData() {
    }

    public static void write(ItemStack stack, Reading r) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("t", r.time());
            tag.putFloat("rpm", r.rpm());
            tag.putFloat("used", r.stressUsed());
            tag.putFloat("cap", r.stressCap());
            tag.putByte("role", r.role());
            tag.putString("block", r.blockId());
            tag.putByte("state", r.state());
            root.put(KEY, tag);
        });
    }

    public static boolean has(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.copyTag().contains(KEY, 10);
    }

    public static Reading read(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) {
            return Reading.empty();
        }
        CompoundTag root = cd.copyTag();
        if (!root.contains(KEY, 10)) {
            return Reading.empty();
        }
        CompoundTag tag = root.getCompound(KEY);
        return new Reading(
            tag.getLong("t"),
            tag.getFloat("rpm"),
            tag.getFloat("used"),
            tag.getFloat("cap"),
            tag.getByte("role"),
            tag.getString("block"),
            tag.getByte("state"));
    }
}
