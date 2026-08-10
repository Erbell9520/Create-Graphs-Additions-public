package com.createstats.menu;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class BattleScreenLinkedControllerMenu extends AbstractContainerMenu {

    public static final int ROW_START_Y = 96;
    public static final int ROW_HEIGHT = 24;
    public static final int SLOT_A_X = 332;
    public static final int SLOT_B_X = 354;
    public static final int PLAYER_INV_X = 127;
    public static final int PLAYER_INV_Y = 304;
    public static final int HOTBAR_Y = 362;

    private final BattleScreenDisplayBE display;
    private final BlockPos observerPos;
    private int clientManualAimRpm = BattleScreenDisplayBE.LINKED_CONTROLLER_MANUAL_AIM_RPM_DEFAULT;

    public BattleScreenLinkedControllerMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(containerId, inv, lookupDisplay(inv, extraData.readBlockPos()), extraData.readBlockPos());
    }

    public BattleScreenLinkedControllerMenu(int containerId, Inventory inv, @Nullable BattleScreenDisplayBE display, BlockPos observerPos) {
        super(ModMenuTypes.BATTLE_SCREEN_LINKED_CONTROLLER.get(), containerId);
        if (display == null) {
            throw new IllegalStateException("BattleScreenLinkedControllerMenu opened without a backing display");
        }
        this.display = display;
        this.observerPos = observerPos.immutable();
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return BattleScreenLinkedControllerMenu.this.display.getLinkedControllerManualAimRpm(BattleScreenLinkedControllerMenu.this.observerPos);
            }

            @Override
            public void set(int value) {
                BattleScreenLinkedControllerMenu.this.clientManualAimRpm = value;
            }
        });

        ItemStackHandler handler = display.getLinkedControllerInventory(this.observerPos);
        for (int button = 0; button < BattleScreenDisplayBE.LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            int y = ROW_START_Y + button * ROW_HEIGHT;
            this.addSlot(new LinkedControllerFrequencySlot(handler,
                    BattleScreenDisplayBE.getLinkedControllerSlotIndex(button, false), SLOT_A_X, y));
            this.addSlot(new LinkedControllerFrequencySlot(handler,
                    BattleScreenDisplayBE.getLinkedControllerSlotIndex(button, true), SLOT_B_X, y));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
        }
    }

    public BattleScreenDisplayBE getDisplay() {
        return this.display;
    }

    public BlockPos getDisplayPos() {
        return this.display.getBlockPos();
    }

    public BlockPos getObserverPos() {
        return this.observerPos;
    }

    public int getManualAimRpm() {
        Level level = this.display.getLevel();
        if (level != null && level.isClientSide()) {
            return this.clientManualAimRpm;
        }
        return this.display.getLinkedControllerManualAimRpm(this.observerPos);
    }

    public boolean isConfigured(int button) {
        ItemStackHandler handler = this.display.getLinkedControllerInventory(this.observerPos);
        return !handler.getStackInSlot(BattleScreenDisplayBE.getLinkedControllerSlotIndex(button, false)).isEmpty()
                || !handler.getStackInSlot(BattleScreenDisplayBE.getLinkedControllerSlotIndex(button, true)).isEmpty();
    }

    @Nullable
    public BattleScreenDisplayBE.LinkedObserver getObserver() {
        return this.display.getLinkedObserver(this.observerPos);
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.display.isRemoved()) {
            return false;
        }
        BlockPos pos = this.display.getBlockPos();
        if (player.level().getBlockEntity(pos) != this.display) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();
        int menuSlotCount = BattleScreenDisplayBE.LINKED_CONTROLLER_SLOT_COUNT;
        if (index < menuSlotCount) {
            if (!this.moveItemStackTo(stackInSlot, menuSlotCount, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stackInSlot, 0, menuSlotCount, false)) {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stackInSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return original;
    }

    @Nullable
    private static BattleScreenDisplayBE lookupDisplay(Inventory inv, BlockPos pos) {
        Level level = inv.player.level();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof BattleScreenDisplayBE display ? display : null;
    }

    private static class LinkedControllerFrequencySlot extends SlotItemHandler {
        LinkedControllerFrequencySlot(ItemStackHandler handler, int idx, int x, int y) {
            super(handler, idx, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}