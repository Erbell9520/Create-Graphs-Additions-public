package com.createstats.menu;

import com.createstats.blockentity.FactoryLogicControllerBE;
import com.createstats.item.MachineSelectionCardItem;
import com.createstats.item.SafetyClutchLinkCardItem;
import com.createstats.registry.ModMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class FactoryLogicControllerMenu extends AbstractContainerMenu {

    public static final int ROW_COUNT = FactoryLogicControllerBE.RULE_COUNT;
    public static final int ROW_HEIGHT = 32;
    public static final int ROW_START_Y = 90;
    public static final int SLOT_Y_OFFSET = 8;
    public static final int MACHINE_SLOT_X = 22;
    public static final int LINK_SLOT_X = 44;
    public static final int PLAYER_INV_X = 424;
    public static final int PLAYER_INV_Y = 224;

    private static final int ACTIONS_PER_ROW = 4;

    private final FactoryLogicControllerBE controller;

    public FactoryLogicControllerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, getController(playerInventory, extraData.readBlockPos()));
    }

    public FactoryLogicControllerMenu(int containerId, Inventory playerInventory, FactoryLogicControllerBE controller) {
        super(ModMenuTypes.FACTORY_LOGIC_CONTROLLER.get(), containerId);
        this.controller = controller;

        ItemStackHandler handler = controller.getInventory();
        for (int row = 0; row < ROW_COUNT; row++) {
            int y = ROW_START_Y + row * ROW_HEIGHT + SLOT_Y_OFFSET;
            this.addSlot(new MachineSelectionSlot(this, row, handler, FactoryLogicControllerBE.getMachineSlotIndex(row), MACHINE_SLOT_X, y));
            this.addSlot(new SafetyClutchLinkSlot(this, row, handler, FactoryLogicControllerBE.getLinkSlotIndex(row), LINK_SLOT_X, y));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18,
                        PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    public FactoryLogicControllerBE getController() {
        return this.controller;
    }

    public int getVisibleRowCount() {
        return this.controller.getVisibleRowCount();
    }

    public boolean isRowVisible(int row) {
        return row >= 0 && row < this.getVisibleRowCount();
    }

    private int getVisibleRuleSlotCount() {
        return this.getVisibleRowCount() * 2;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().getBlockEntity(this.controller.getBlockPos()) != this.controller) {
            return false;
        }
        return player.distanceToSqr(this.controller.getBlockPos().getX() + 0.5,
                this.controller.getBlockPos().getY() + 0.5,
                this.controller.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        result = stackInSlot.copy();

        if (index < FactoryLogicControllerBE.SLOT_COUNT) {
            if (!this.moveItemStackTo(stackInSlot, FactoryLogicControllerBE.SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stackInSlot, 0, this.getVisibleRuleSlotCount(), false)) {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stackInSlot.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return result;
    }

    public static final int STRATEGY_TOGGLE_ID = ROW_COUNT * ACTIONS_PER_ROW;
    public static final int TARGET_RPM_DOWN_ID = STRATEGY_TOGGLE_ID + 1;
    public static final int TARGET_RPM_UP_ID = STRATEGY_TOGGLE_ID + 2;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == STRATEGY_TOGGLE_ID) {
            this.controller.cycleBankStrategy();
            return true;
        }
        if (id == TARGET_RPM_DOWN_ID) {
            this.controller.adjustTargetRpm(-1);
            return true;
        }
        if (id == TARGET_RPM_UP_ID) {
            this.controller.adjustTargetRpm(1);
            return true;
        }
        int row = id / ACTIONS_PER_ROW;
        int action = id % ACTIONS_PER_ROW;
        if (!this.isRowVisible(row)) {
            return false;
        }
        switch (action) {
            case 0 -> this.controller.adjustRowTimeout(row, -1);
            case 1 -> this.controller.adjustRowTimeout(row, 1);
            case 2 -> this.controller.manualTripRow(row);
            case 3 -> this.controller.manualResetRow(row);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static int getButtonId(int row, int action) {
        return row * ACTIONS_PER_ROW + action;
    }

    private static FactoryLogicControllerBE getController(Inventory playerInventory, BlockPos pos) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof FactoryLogicControllerBE controller)) {
            throw new IllegalStateException("Expected FactoryLogicControllerBE at " + pos);
        }
        return controller;
    }

    /** A row whose link card targets a Kinetic Flywheel stands alone and uses no machine card. */
    public boolean isRowFlywheelLinked(int row) {
        return this.controller.isRowFlywheelLinked(row);
    }

    private static class MachineSelectionSlot extends SlotItemHandler {

        private final FactoryLogicControllerMenu menu;
        private final int row;

        public MachineSelectionSlot(FactoryLogicControllerMenu menu, int row, ItemStackHandler itemHandler,
                                    int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
            this.row = row;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof MachineSelectionCardItem && !this.menu.isRowFlywheelLinked(this.row);
        }

        @Override
        public boolean isActive() {
            if (!this.menu.isRowVisible(this.row)) {
                return false;
            }
            // A flywheel row stands alone, hide its machine slot unless a card is stranded there.
            return !this.menu.isRowFlywheelLinked(this.row) || !this.getItem().isEmpty();
        }
    }

    private static class SafetyClutchLinkSlot extends SlotItemHandler {

        private final FactoryLogicControllerMenu menu;
        private final int row;

        public SafetyClutchLinkSlot(FactoryLogicControllerMenu menu, int row, ItemStackHandler itemHandler,
                                    int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
            this.row = row;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof SafetyClutchLinkCardItem;
        }

        @Override
        public boolean isActive() {
            return this.menu.isRowVisible(this.row);
        }
    }
}
