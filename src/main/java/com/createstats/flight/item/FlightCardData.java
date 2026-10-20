package com.createstats.flight.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

public final class FlightCardData {
    @Nullable private BlockPos linkedPos;
    @Nullable private ResourceLocation dimensionId;

    public FlightCardData() {}

    public FlightCardData(@Nullable BlockPos linkedPos, @Nullable ResourceLocation dimensionId) {
        this.linkedPos = linkedPos;
        this.dimensionId = dimensionId;
    }

    public boolean isLinked() {
        return linkedPos != null;
    }

    @Nullable public BlockPos getLinkedPos()       { return linkedPos; }
    @Nullable public ResourceLocation getDimension() { return dimensionId; }

    public void setLinkedPos(@Nullable BlockPos pos)         { this.linkedPos = pos; }
    public void setDimension(@Nullable ResourceLocation dim) { this.dimensionId = dim; }

    public void clear() {
        linkedPos = null;
        dimensionId = null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (linkedPos != null)   tag.putLong("Pos", linkedPos.asLong());
        if (dimensionId != null) tag.putString("Dim", dimensionId.toString());
        return tag;
    }

    public static FlightCardData load(@Nullable CompoundTag tag) {
        FlightCardData data = new FlightCardData();
        if (tag == null) return data;
        if (tag.contains("Pos")) data.linkedPos = BlockPos.of(tag.getLong("Pos"));
        if (tag.contains("Dim")) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.getString("Dim"));
            if (rl != null) data.dimensionId = rl;
        }
        return data;
    }

    public static FlightCardData get(ItemStack stack, String tagKey) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return new FlightCardData();
        CompoundTag root = cd.copyTag();
        if (!root.contains(tagKey)) return new FlightCardData();
        return load(root.getCompound(tagKey));
    }

    public static void set(ItemStack stack, String tagKey, FlightCardData data) {
        if (!data.isLinked()) {
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd == null) return;
            CompoundTag root = cd.copyTag();
            root.remove(tagKey);
            if (root.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
            }
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> root.put(tagKey, data.save()));
    }
}
