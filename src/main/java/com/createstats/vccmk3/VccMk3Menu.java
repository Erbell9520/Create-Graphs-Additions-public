package com.createstats.vccmk3;

import com.createstats.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class VccMk3Menu extends AbstractContainerMenu {
    public static final int ROW_COUNT = VccMk3BlockEntity.ROW_COUNT;
    public static final int SLOT_COUNT = VccMk3BlockEntity.SLOT_COUNT;

    public static final int RACK_COLUMNS = 4;
    public static final int RACK_GRID_X = 96;
    public static final int RACK_GRID_Y = 96;
    public static final int RACK_BAY_PITCH = 56;
    public static final int RACK_ROW_PITCH = 22;

    public static final int RACK_BAY_LABEL_W = 16;

    public static final int PLAYER_INV_X = 125;
    public static final int PLAYER_INV_Y = 288;
    public static final int HOTBAR_Y = 346;

    private final VccMk3BlockEntity controller;
    private final Level level;

    private boolean slotsVisible = true;

    public VccMk3Menu(int containerId, Inventory playerInventory, VccMk3BlockEntity controller) {
        super(ModMenuTypes.VCC_MK3.get(), containerId);
        this.controller = controller;
        this.level = playerInventory.player.level();

        for (int bay = 0; bay < ROW_COUNT; bay++) {
            int column = bay % RACK_COLUMNS;
            int row = bay / RACK_COLUMNS;
            int x = RACK_GRID_X + column * RACK_BAY_PITCH + RACK_BAY_LABEL_W;
            int y = RACK_GRID_Y + row * RACK_ROW_PITCH;
            addSlot(new RackSlot(bay * 2, x, y));
            addSlot(new RackSlot(bay * 2 + 1, x + 18, y));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new HideableSlot(playerInventory, 9 + row * 9 + column,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new HideableSlot(playerInventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
        }
    }

    public void setSlotsVisible(boolean visible) {
        this.slotsVisible = visible;
    }

    private final class HideableSlot extends Slot {
        private HideableSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    public VccMk3Menu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, resolve(playerInventory, buffer.readBlockPos()));
    }

    private static VccMk3BlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        if (be instanceof VccMk3BlockEntity controller) return controller;
        throw new IllegalStateException("No VCC Mk III at " + pos);
    }

    public VccMk3BlockEntity controller() {
        return controller;
    }

    private final class RackSlot extends SlotItemHandler {
        private final int rackIndex;

        private RackSlot(int index, int x, int y) {
            super(controller.inventory(), index, x, y);
            this.rackIndex = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return controller.inventory().isItemValid(rackIndex, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < SLOT_COUNT) {
            if (!moveItemStackTo(stack, SLOT_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(stack, 0, SLOT_COUNT, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (controller.isRemoved()) return false;

        return player.level() == level
                && player.distanceToSqr(
                        controller.getBlockPos().getX() + 0.5,
                        controller.getBlockPos().getY() + 0.5,
                        controller.getBlockPos().getZ() + 0.5) <= 144.0;
    }

    @Nullable
    public static VccMk3BlockEntity controllerAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof VccMk3BlockEntity be ? be : null;
    }
}
