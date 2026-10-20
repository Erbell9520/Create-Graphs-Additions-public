package com.createstats.vacuum;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

// take only, in through the nozzle only
public class SnifferSlots implements IItemHandler {
    private final TypeSlots<ItemKey> rules = new TypeSlots<>();
    private final Runnable onChange;

    public SnifferSlots(Runnable onChange) {
        this.onChange = onChange;
    }

    public TypeSlots<ItemKey> rules() {
        return rules;
    }

    public boolean accepts(ItemStack stack) {
        return rules.accepts(ItemKey.of(stack), stack.getCount(), stack.getMaxStackSize());
    }

    public boolean reserve(ItemKey key, int count) {
        final boolean ok = rules.reserve(key, count, key.maxStack());
        if (ok) {
            onChange.run();
        }
        return ok;
    }

    public void release(ItemKey key, int count) {
        rules.release(key, count);
        onChange.run();
    }

    public boolean commit(ItemKey key, int count) {
        final boolean ok = rules.commit(key, count);
        if (ok) {
            onChange.run();
        }
        return ok;
    }

    public boolean full() {
        if (rules.kindsHeld() < TypeSlots.SLOTS) {
            return false;
        }
        for (int i = 0; i < TypeSlots.SLOTS; i++) {
            final ItemKey k = rules.kind(i);
            if (k != null && rules.stored(i) + rules.reserved(i) < k.maxStack()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getSlots() {
        return TypeSlots.SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        final ItemKey k = rules.kind(slot);
        return k == null || rules.stored(slot) <= 0 ? ItemStack.EMPTY : k.stack(rules.stored(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        final ItemKey k = rules.kind(slot);
        if (k == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        final int out = Math.min(amount, rules.stored(slot));
        if (out <= 0) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            rules.extract(slot, out);
            onChange.run();
        }
        return k.stack(out);
    }

    @Override
    public int getSlotLimit(int slot) {
        final ItemKey k = rules.kind(slot);
        return k == null ? 64 : k.maxStack();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }

    public ListTag save(HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (int i = 0; i < TypeSlots.SLOTS; i++) {
            final ItemStack s = getStackInSlot(i);
            final CompoundTag t = new CompoundTag();
            if (!s.isEmpty()) {
                t.put("Stack", s.save(registries));
            }
            list.add(t);
        }
        return list;
    }

    public void load(ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < TypeSlots.SLOTS; i++) {
            final CompoundTag t = i < list.size() ? list.getCompound(i) : new CompoundTag();
            final ItemStack s = t.contains("Stack", Tag.TAG_COMPOUND)
                    ? ItemStack.parseOptional(registries, t.getCompound("Stack")) : ItemStack.EMPTY;
            rules.load(i, s.isEmpty() ? null : ItemKey.of(s), s.getCount());
        }
    }

    public java.util.List<ItemStack> drain() {
        final java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < TypeSlots.SLOTS; i++) {
            final ItemStack s = extractItem(i, Integer.MAX_VALUE, false);
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
