package com.createstats.upgrade;

import com.createstats.item.StorageUpgradeItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

// one per slot, degrees multiply
public final class UpgradeRack {
    public static final int SIZE = 3;

    private final NonNullList<ItemStack> stacks;

    public UpgradeRack() {
        this(SIZE);
    }

    public UpgradeRack(int size) {
        this.stacks = NonNullList.withSize(Math.max(1, size), ItemStack.EMPTY);
    }

    public int size() {
        return this.stacks.size();
    }

    public ItemStack get(int slot) {
        return slot >= 0 && slot < this.stacks.size() ? this.stacks.get(slot) : ItemStack.EMPTY;
    }

    public void set(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.stacks.size()) {
            this.stacks.set(slot, stack == null ? ItemStack.EMPTY : stack);
        }
    }

    public NonNullList<ItemStack> contents() {
        return this.stacks;
    }

    public boolean isEmpty() {
        for (final ItemStack stack : this.stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public double multiplier(UpgradeKind kind) {
        double out = 1.0;
        for (final ItemStack stack : this.stacks) {
            final StorageUpgradeItem upgrade = StorageUpgradeItem.of(stack);
            if (upgrade != null && upgrade.kind() == kind) {
                final double m = upgrade.degree().multiplier();
                if (m > 0.0) {
                    out *= m;
                }
            }
        }
        return out;
    }

    public int count(UpgradeKind kind) {
        int found = 0;
        for (final ItemStack stack : this.stacks) {
            final StorageUpgradeItem upgrade = StorageUpgradeItem.of(stack);
            if (upgrade != null && upgrade.kind() == kind) {
                found++;
            }
        }
        return found;
    }

    public boolean any() {
        return !isEmpty();
    }

    public boolean blocksRemoval(int slot, int baseRows, int columns, int maxRows,
                                 int highestOccupied, int deepestStack, int deepestBase,
                                 int maxStack) {
        final StorageUpgradeItem going = StorageUpgradeItem.of(get(slot));
        if (going == null) {
            return false;
        }

        double slots = 1.0;
        double stack = 1.0;
        for (int i = 0; i < this.stacks.size(); i++) {
            if (i == slot) {
                continue;
            }
            final StorageUpgradeItem upgrade = StorageUpgradeItem.of(this.stacks.get(i));
            if (upgrade == null) {
                continue;
            }
            final double m = upgrade.degree().multiplier();
            if (m <= 0.0) {
                continue;
            }
            if (upgrade.kind() == UpgradeKind.SLOTS) {
                slots *= m;
            } else {
                stack *= m;
            }
        }

        return going.kind() == UpgradeKind.SLOTS
                ? UpgradeScale.stranded(highestOccupied, columns, baseRows, slots, maxRows)
                : UpgradeScale.tooDeep(deepestStack, deepestBase, stack, maxStack);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, this.stacks, true, registries);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.stacks.clear();
        if (tag != null) {
            ContainerHelper.loadAllItems(tag, this.stacks, registries);
        }
    }

    public UpgradeRack copy() {
        final UpgradeRack out = new UpgradeRack(this.stacks.size());
        for (int i = 0; i < this.stacks.size(); i++) {
            out.stacks.set(i, this.stacks.get(i).copy());
        }
        return out;
    }
}
