package com.createstats.vacuum;

import net.minecraft.world.item.ItemStack;

public final class ItemKey {
    private final ItemStack template;
    private final int hash;

    private ItemKey(ItemStack template) {
        this.template = template;
        this.hash = ItemStack.hashItemAndComponents(template);
    }

    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.copyWithCount(1));
    }

    public ItemStack stack(int count) {
        return template.copyWithCount(count);
    }

    public int maxStack() {
        return template.getMaxStackSize();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemKey other && ItemStack.isSameItemSameComponents(template, other.template);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
