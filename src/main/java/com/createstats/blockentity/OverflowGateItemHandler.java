package com.createstats.blockentity;

import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public class OverflowGateItemHandler implements IItemHandler {

    private final OverflowGateBE blockEntity;

    public OverflowGateItemHandler(OverflowGateBE be) {
        this.blockEntity = be;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return blockEntity.getStoredStack();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!blockEntity.canTakeItems()) {
            return stack;
        }

        ItemStack remainder = ItemHelper.limitCountToMaxStackSize(stack, simulate);
        if (!simulate) {
            blockEntity.setStackToDistribute(stack);
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        IItemHandler beltHandler = blockEntity.getBeltCapability();
        if (beltHandler == null) {
            return ItemStack.EMPTY;
        }
        return beltHandler.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return blockEntity.getStoredStack().isEmpty() ? 64 : blockEntity.getStoredStack().getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
