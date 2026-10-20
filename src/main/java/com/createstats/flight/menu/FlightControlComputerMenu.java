package com.createstats.flight.menu;

import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class FlightControlComputerMenu extends AbstractContainerMenu {
    public static final int ROW_COUNT      = FlightControlComputerBE.ROW_COUNT;
    public static final int SLOT_COUNT     = FlightControlComputerBE.SLOT_COUNT;
    public static final int MIN_VISIBLE_ROWS = FlightControlComputerBE.MIN_VISIBLE_CARD_ROWS;
    public static final int RACK_VIEWPORT_ROWS = 7;
    public static final int MENU_CARD_SLOT_COUNT = ROW_COUNT * RACK_VIEWPORT_ROWS * 2;
    public static final int LINKED_CONTROLLER_BUTTON_COUNT = FlightControlComputerBE.LINKED_CONTROLLER_BUTTON_COUNT;
    public static final int LINKED_CONTROLLER_SLOT_COUNT = FlightControlComputerBE.LINKED_CONTROLLER_SLOT_COUNT;
    public static final int MENU_LINKED_CONTROLLER_SLOT_START = MENU_CARD_SLOT_COUNT;
    public static final int PLAYER_SLOT_START = MENU_LINKED_CONTROLLER_SLOT_START + LINKED_CONTROLLER_SLOT_COUNT;
    public static final int SCROLL_BUTTON_BASE = 20_000;
    public static final int BUTTON_TOGGLE_PATHFINDER_SYSTEMS = 20_100;

    public static final int ROW_HEIGHT     = 34;
    public static final int ROW_START_Y    = 92;
    public static final int PROP_SLOT_X    = 32;
    public static final int GOV_SLOT_X     = 62;
    public static final int LINKED_CONTROLLER_SLOT_A_X = 460;
    public static final int LINKED_CONTROLLER_SLOT_B_X = 482;
    public static final int LINKED_CONTROLLER_SLOT_START_Y = 126;
    public static final int LINKED_CONTROLLER_ROW_HEIGHT = 28;
    public static final int PLAYER_INV_X   = 27;
    public static final int PLAYER_INV_Y   = 428;
    public static final int HOTBAR_Y       = 486;

    private final FlightControlComputerBE fcc;
    private int cardScrollStart;
    private int clientRedstoneRowMask;
    private boolean clientLinkedControllerPageActive;

    public FlightControlComputerMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(containerId, inv, lookupBE(inv, extraData.readBlockPos()));
    }

    public FlightControlComputerMenu(int containerId, Inventory inv, @Nullable FlightControlComputerBE fcc) {
        super(ModMenuTypes.FLIGHT_CONTROL_COMPUTER.get(), containerId);
        if (fcc == null) {
            throw new IllegalStateException("FlightControlComputerMenu opened without a backing block entity");
        }
        this.fcc = fcc;

        ItemStackHandler handler = fcc.getInventory();
        for (int row = 0; row < ROW_COUNT; row++) {
            for (int visualRow = 0; visualRow < RACK_VIEWPORT_ROWS; visualRow++) {
                int y = ROW_START_Y + visualRow * ROW_HEIGHT;
                this.addSlot(new PropellerCardSlot(this, row, visualRow, handler,
                    FlightControlComputerBE.getPropellerSlotIndex(row), PROP_SLOT_X, y));
                this.addSlot(new GovernorCardSlot(this, row, visualRow, handler,
                    FlightControlComputerBE.getGovernorSlotIndex(row), GOV_SLOT_X, y));
            }
        }
                ItemStackHandler linkedHandler = fcc.getLinkedControllerInventory();
                for (int button = 0; button < LINKED_CONTROLLER_BUTTON_COUNT; button++) {
                    int y = LINKED_CONTROLLER_SLOT_START_Y + button * LINKED_CONTROLLER_ROW_HEIGHT;
                    this.addSlot(new LinkedControllerFrequencySlot(this, linkedHandler,
                        FlightControlComputerBE.getLinkedControllerSlotIndex(button, false), LINKED_CONTROLLER_SLOT_A_X, y));
                    this.addSlot(new LinkedControllerFrequencySlot(this, linkedHandler,
                        FlightControlComputerBE.getLinkedControllerSlotIndex(button, true), LINKED_CONTROLLER_SLOT_B_X, y));
                }

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.addSlot(new Slot(inv, c, PLAYER_INV_X + c * 18, HOTBAR_Y));
        }
    }

    public FlightControlComputerBE getController() {
        return fcc;
    }

    public void setClientRedstoneRowMask(int mask) {
        clientRedstoneRowMask = mask;
    }

    public void setClientLinkedControllerPageActive(boolean active) {
        clientLinkedControllerPageActive = active;
    }

    public boolean isLinkedControllerPageActive() {
        if (fcc.getLevel() != null && fcc.getLevel().isClientSide()) {
            return clientLinkedControllerPageActive;
        }
        return true;
    }

    public boolean isRedstonePairRow(int row) {
        if (row < 0 || row >= ROW_COUNT) return false;
        if (fcc.getLevel() != null && fcc.getLevel().isClientSide()) {
            return (clientRedstoneRowMask & (1 << row)) != 0;
        }
        return fcc.isRedstonePairRow(row);
    }

    public int getVisibleRowCount() {
        return fcc.getVisibleCardRowCount();
    }

    public int getRackViewportRows() {
        return Math.min(RACK_VIEWPORT_ROWS, getVisibleRowCount());
    }

    public int getMaxCardScrollStart() {
        return Math.max(0, getVisibleRowCount() - RACK_VIEWPORT_ROWS);
    }

    public int getCardScrollStart() {
        int max = getMaxCardScrollStart();
        if (cardScrollStart > max) cardScrollStart = max;
        if (cardScrollStart < 0) cardScrollStart = 0;
        return cardScrollStart;
    }

    public void setCardScrollStart(int scrollStart) {
        cardScrollStart = Math.max(0, Math.min(scrollStart, getMaxCardScrollStart()));
    }

    public boolean isCardSlotActive(int row, int visualRow) {
        int start = getCardScrollStart();
        int visible = getRackViewportRows();
        return row >= start && row < start + visible && row - start == visualRow;
    }

    @Nullable
    private static FlightControlComputerBE lookupBE(Inventory inv, BlockPos pos) {
        Level level = inv.player.level();
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (!(be instanceof FlightControlComputerBE) && level.isLoaded(pos)) {
            be = level.getBlockEntity(pos);
        }
        return (be instanceof FlightControlComputerBE fcc) ? fcc : null;
    }

    @Override
    public boolean stillValid(Player player) {
        return fcc.isPlayerInInteractionRange(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stackInSlot = slot.getItem();
        ItemStack original = stackInSlot.copy();

        if (index < MENU_CARD_SLOT_COUNT) {
            if (!slot.isActive()) return ItemStack.EMPTY;

            if (!this.moveItemStackTo(stackInSlot, PLAYER_SLOT_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_SLOT_START) {
            if (!slot.isActive()) return ItemStack.EMPTY;
            if (!this.moveItemStackTo(stackInSlot, PLAYER_SLOT_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(stackInSlot, 0, MENU_CARD_SLOT_COUNT, false)) {
                if (!this.moveItemStackTo(stackInSlot, MENU_LINKED_CONTROLLER_SLOT_START, PLAYER_SLOT_START, false)) {
                    return ItemStack.EMPTY;
                }
            }
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

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= SCROLL_BUTTON_BASE && id < SCROLL_BUTTON_BASE + ROW_COUNT) {
            setCardScrollStart(id - SCROLL_BUTTON_BASE);
            return true;
        }
        if (id == BUTTON_TOGGLE_PATHFINDER_SYSTEMS) {
            fcc.setPathfinderSystemsEnabled(!fcc.isPathfinderSystemsEnabled());
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    private static class PropellerCardSlot extends SlotItemHandler {
        private final FlightControlComputerMenu menu;
        private final int row;
        private final int visualRow;

        public PropellerCardSlot(FlightControlComputerMenu menu, int row, int visualRow, ItemStackHandler handler, int idx, int x, int y) {
            super(handler, idx, x, y);
            this.menu = menu;
            this.row = row;
            this.visualRow = visualRow;
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            if (isActive() && menu.isRedstonePairRow(row)) return true;
            return isActive() && (stack.isEmpty() || stack.getItem() instanceof PropellerCardItem);
        }
        @Override
        public boolean mayPickup(Player player) {
            return isActive() && super.mayPickup(player);
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
        @Override
        public boolean isActive() {
            return menu.isCardSlotActive(row, visualRow);
        }
    }

    private static class GovernorCardSlot extends SlotItemHandler {
        private final FlightControlComputerMenu menu;
        private final int row;
        private final int visualRow;

        public GovernorCardSlot(FlightControlComputerMenu menu, int row, int visualRow, ItemStackHandler handler, int idx, int x, int y) {
            super(handler, idx, x, y);
            this.menu = menu;
            this.row = row;
            this.visualRow = visualRow;
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            if (isActive() && menu.isRedstonePairRow(row)) return true;
            return isActive() && (stack.isEmpty() || stack.getItem() instanceof GovernorCardItem);
        }
        @Override
        public boolean mayPickup(Player player) {
            return isActive() && super.mayPickup(player);
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
        @Override
        public boolean isActive() {
            return menu.isCardSlotActive(row, visualRow);
        }
    }

    private static class LinkedControllerFrequencySlot extends SlotItemHandler {
        private final FlightControlComputerMenu menu;

        public LinkedControllerFrequencySlot(FlightControlComputerMenu menu, ItemStackHandler handler, int idx, int x, int y) {
            super(handler, idx, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive();
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive() && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return menu.isLinkedControllerPageActive();
        }
    }
}
