package com.createstats.vacuum;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

// take only, a collector a hopper could fill is a chest with a fan
public class VacuumCatch extends ItemStackHandler {
    private final Runnable onChange;

    public VacuumCatch(int slots, Runnable onChange) {
        super(slots);
        this.onChange = onChange;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChange.run();
    }

    public int room(ItemStack stack) {
        int left = stack.getCount();
        ItemStack probe = stack.copy();
        for (int i = 0; i < getSlots() && !probe.isEmpty(); i++) {
            probe = insertItem(i, probe, true);
        }
        return left - probe.getCount();
    }

    public ItemStack accept(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < getSlots() && !rest.isEmpty(); i++) {
            if (!getStackInSlot(i).isEmpty()) {
                rest = insertItem(i, rest, false);
            }
        }
        for (int i = 0; i < getSlots() && !rest.isEmpty(); i++) {
            rest = insertItem(i, rest, false);
        }
        return rest;
    }

    public boolean fitsAll(java.util.List<ItemStack> stacks) {
        final ItemStackHandler scratch = new ItemStackHandler(getSlots()) {
            @Override
            public int getSlotLimit(int slot) {
                return VacuumCatch.this.getSlotLimit(slot);
            }
        };
        for (int i = 0; i < getSlots(); i++) {
            scratch.setStackInSlot(i, getStackInSlot(i).copy());
        }
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            for (int i = 0; i < scratch.getSlots() && !rest.isEmpty(); i++) {
                if (!scratch.getStackInSlot(i).isEmpty()) {
                    rest = scratch.insertItem(i, rest, false);
                }
            }
            for (int i = 0; i < scratch.getSlots() && !rest.isEmpty(); i++) {
                rest = scratch.insertItem(i, rest, false);
            }
            if (!rest.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasEmptySlot() {
        for (int i = 0; i < getSlots(); i++) {
            if (getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int stacksHeld() {
        int n = 0;
        for (int i = 0; i < getSlots(); i++) {
            if (!getStackInSlot(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public java.util.List<ItemStack> drain() {
        final java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < getSlots(); i++) {
            final ItemStack s = getStackInSlot(i);
            if (!s.isEmpty()) {
                out.add(s.copy());
                setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        return out;
    }

    private final IItemHandler outside = new IItemHandler() {
        @Override
        public int getSlots() {
            return VacuumCatch.this.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return VacuumCatch.this.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return VacuumCatch.this.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return VacuumCatch.this.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    public IItemHandler outside() {
        return outside;
    }
}
