package com.createstats.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ConductorsLensData {
    private static final String KEY = "LensReading";

    public record Reading(long time, String trainName, float speedMps, float maxSpeedMps,
                          int carriages, String owner, String destinationName,
                          double distanceToDestination, boolean waitingForSignal,
                          double distanceToSignal, int fuelTicks, int itemCount, String topItem,
                          int fluidAmount, String topFluid) {
        public static Reading empty() {
            return new Reading(0L, "", 0f, 0f, 0, "", "", -1.0, false, -1.0, 0, 0, "", 0, "");
        }

        public boolean hasTrain() {
            return !trainName.isEmpty();
        }

        public boolean hasDestination() {
            return !destinationName.isEmpty();
        }
    }

    private ConductorsLensData() {
    }

    public static void write(ItemStack stack, Reading r) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag tag = new CompoundTag();
            tag.putLong("t", r.time());
            tag.putString("train", r.trainName());
            tag.putFloat("speed", r.speedMps());
            tag.putFloat("maxSpeed", r.maxSpeedMps());
            tag.putInt("carriages", r.carriages());
            tag.putString("owner", r.owner());
            tag.putString("dest", r.destinationName());
            tag.putDouble("destDist", r.distanceToDestination());
            tag.putBoolean("waitSignal", r.waitingForSignal());
            tag.putDouble("signalDist", r.distanceToSignal());
            tag.putInt("fuel", r.fuelTicks());
            tag.putInt("items", r.itemCount());
            tag.putString("topItem", r.topItem());
            tag.putInt("fluid", r.fluidAmount());
            tag.putString("topFluid", r.topFluid());
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
            tag.getString("train"),
            tag.getFloat("speed"),
            tag.getFloat("maxSpeed"),
            tag.getInt("carriages"),
            tag.getString("owner"),
            tag.getString("dest"),
            tag.getDouble("destDist"),
            tag.getBoolean("waitSignal"),
            tag.getDouble("signalDist"),
            tag.getInt("fuel"),
            tag.getInt("items"),
            tag.getString("topItem"),
            tag.getInt("fluid"),
            tag.getString("topFluid"));
    }
}
