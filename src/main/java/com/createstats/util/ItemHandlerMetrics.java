package com.createstats.util;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public final class ItemHandlerMetrics
{
    private ItemHandlerMetrics() {
    }

    public static Readout read(IItemHandler handler) {
        int totalItems = 0;
        int totalCapacity = 0;
        int filledSlots = 0;
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                totalItems += stack.getCount();
                filledSlots++;
            }
            final int slotLimit = handler.getSlotLimit(slot);
            if (slotLimit > 0) {
                totalCapacity += slotLimit;
            } else if (!stack.isEmpty()) {
                totalCapacity += stack.getMaxStackSize();
            }
        }
        final float fillPercent = totalCapacity > 0 ? totalItems * 100.0f / totalCapacity : 0.0f;
        return new Readout(fillPercent, totalItems, totalCapacity, filledSlots);
    }

    public record Readout(float fillPercent, int totalItems, int totalCapacity, int filledSlots)
    {
    }
}
