package com.createstats.upgrade;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class DeepItems {
    private static final String KEY = "DeepItems";

    private DeepItems() {
    }

    public static void save(CompoundTag tag, List<ItemStack> items, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            final ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);

            entry.put("Item", stack.copyWithCount(1).save(registries));
            entry.putInt("Count", stack.getCount());
            list.add(entry);
        }
        tag.put(KEY, list);
    }

    public static int highestSlot(CompoundTag tag) {
        int highest = -1;
        if (tag.contains(KEY, Tag.TAG_LIST)) {
            final ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                highest = Math.max(highest, list.getCompound(i).getInt("Slot"));
            }
            return highest;
        }

        if (tag.contains("Items", Tag.TAG_LIST)) {
            final ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                highest = Math.max(highest, list.getCompound(i).getByte("Slot") & 255);
            }
        }
        return highest;
    }

    public static void load(CompoundTag tag, NonNullList<ItemStack> items,
                            HolderLookup.Provider registries) {
        items.clear();
        if (!tag.contains(KEY, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, items, registries);
            return;
        }
        final ListTag list = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag entry = list.getCompound(i);
            final int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= items.size()) {
                continue;
            }
            ItemStack.parse(registries, entry.getCompound("Item")).ifPresent(stack -> {
                stack.setCount(Math.max(1, entry.getInt("Count")));
                items.set(slot, stack);
            });
        }
    }

    public static void dropSplit(net.minecraft.world.level.Level level,
                                 net.minecraft.core.BlockPos pos, List<ItemStack> items) {
        for (final ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            int left = stack.getCount();
            final int per = Math.max(1, stack.getMaxStackSize());
            while (left > 0) {
                final int take = Math.min(per, left);
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        stack.copyWithCount(take));
                left -= take;
            }
        }
    }
}
