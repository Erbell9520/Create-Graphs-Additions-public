package com.createstats.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class ForeignAnchorReader {
    private static final String CA_X = "x";
    private static final String CA_Y = "y";
    private static final String CA_Z = "z";
    private static final String CA_NODE = "node";

    private static final ResourceLocation NEW_AGE_BOUND_TO =
            ResourceLocation.fromNamespaceAndPath("create_new_age", "bound_to");

    private ForeignAnchorReader() {
    }

    @Nullable
    public static BlockPos peek(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        final BlockPos fromCreateAddition = readCreateAddition(stack);
        return fromCreateAddition != null ? fromCreateAddition : readNewAge(stack);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        clearCreateAddition(stack);
        final DataComponentType<?> boundTo = newAgeBoundTo();
        if (boundTo != null) {
            stack.remove(boundTo);
        }
    }

    @Nullable
    private static BlockPos readCreateAddition(ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        final CompoundTag tag = data.copyTag();
        if (!tag.contains(CA_X) || !tag.contains(CA_Y) || !tag.contains(CA_Z)) {
            return null;
        }
        return new BlockPos(tag.getInt(CA_X), tag.getInt(CA_Y), tag.getInt(CA_Z));
    }

    private static void clearCreateAddition(ItemStack stack) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        final CompoundTag tag = data.copyTag();
        if (!tag.contains(CA_X) && !tag.contains(CA_Y) && !tag.contains(CA_Z) && !tag.contains(CA_NODE)) {
            return;
        }
        tag.remove(CA_X);
        tag.remove(CA_Y);
        tag.remove(CA_Z);
        tag.remove(CA_NODE);

        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Nullable
    private static BlockPos readNewAge(ItemStack stack) {
        final DataComponentType<?> boundTo = newAgeBoundTo();
        if (boundTo == null) {
            return null;
        }
        return stack.get(boundTo) instanceof BlockPos pos ? pos : null;
    }

    @Nullable
    private static DataComponentType<?> newAgeBoundTo() {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(NEW_AGE_BOUND_TO);
    }
}
