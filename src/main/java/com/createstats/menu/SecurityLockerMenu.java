package com.createstats.menu;

import com.createstats.blockentity.SecurityLockerBE;
import com.createstats.item.StorageUpgradeItem;
import com.createstats.registry.ModMenuTypes;
import com.createstats.upgrade.UpgradeRack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class SecurityLockerMenu extends AbstractContainerMenu {
    public static final int PANEL_W = 190;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 30;
    public static final int INV_X = 8;

    public static final int VISIBLE_ROWS = 6;

    public static final int TOP_H = 29;
    public static final int ROW_H = 18;
    public static final int BOTTOM_H = 97;

    public static final int INV_DY = 14;
    public static final int HOTBAR_DY = 72;

    public static final int RACK_W = 38;
    public static final int RACK_LIP = 4;
    public static final int RACK_X = PANEL_W - RACK_LIP;
    public static final int RACK_HEAD_H = 12;
    public static final int RACK_SLOT_X = RACK_X + 12;
    public static final int RACK_SLOT_DY = 13;

    public static final int READ_X = RACK_X + 6;
    public static final int READ_W = 30;
    public static final int READ_H = 14;
    public static final int READ_DY0 = 72;
    public static final int READ_DY1 = 88;

    public static final int TOTAL_W = RACK_X + RACK_W;

    public static final int SCROLL_X = 172;
    public static final int SCROLL_W = 12;
    public static final int KNOB_H = 15;

    @Nullable
    private final SecurityLockerBE locker;

    private final int openRows;

    private final int storageEnd;
    private final int rackEnd;

    private final double openStackMultiplier;

    private final int maxStack;

    private final DataSlot scrollRow = DataSlot.standalone();

    @Nullable
    private final ScrollWindow window;

    public SecurityLockerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                extraData.readVarInt(), extraData.readDouble(), extraData.readVarInt());
    }

    public SecurityLockerMenu(int containerId, Inventory playerInventory,
                              @Nullable SecurityLockerBE locker) {
        this(containerId, playerInventory, locker,
                locker == null ? SecurityLockerBE.BASE_ROWS : locker.rows(),
                locker == null ? 1.0 : locker.stackMultiplier(),
                locker == null ? 64 : locker.maxStackCeiling());
    }

    private SecurityLockerMenu(int containerId, Inventory playerInventory,
                               @Nullable SecurityLockerBE locker, int rows, double stackMultiplier,
                               int maxStack) {
        super(ModMenuTypes.SECURITY_LOCKER.get(), containerId);
        this.locker = locker;
        this.openRows = Math.max(SecurityLockerBE.BASE_ROWS, rows);
        this.openStackMultiplier = Math.max(1.0, stackMultiplier);
        this.maxStack = Math.max(64, maxStack);

        final boolean server = locker != null && locker.getLevel() != null
                && !locker.getLevel().isClientSide();
        this.window = server ? new ScrollWindow(locker) : null;
        final Container storage = server ? this.window : new DeepContainer(visibleSlots());
        final Container rack = locker == null
                ? new SimpleContainer(UpgradeRack.SIZE) : locker.rackContainer();

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int col = 0; col < SecurityLockerBE.COLUMNS; col++) {
                addSlot(new StorageSlot(storage, col + row * SecurityLockerBE.COLUMNS,
                        GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        this.storageEnd = this.slots.size();

        for (int i = 0; i < UpgradeRack.SIZE; i++) {
            addSlot(new RackSlot(rack, i, RACK_SLOT_X, rackSlotY(i)));
        }
        this.rackEnd = this.slots.size();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18,
                        invY() + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, hotbarY()));
        }

        addDataSlot(this.scrollRow);
    }

    public static void writeOpenData(SecurityLockerBE locker, FriendlyByteBuf buf) {
        buf.writeBlockPos(locker.getBlockPos());
        buf.writeVarInt(locker.rows());
        buf.writeDouble(locker.stackMultiplier());
        buf.writeVarInt(locker.maxStackCeiling());
    }

    @Nullable
    private static SecurityLockerBE resolve(Inventory playerInventory, BlockPos pos) {
        final BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof SecurityLockerBE locker ? locker : null;
    }

    @Nullable
    public SecurityLockerBE locker() {
        return this.locker;
    }

    public int rows() {
        return this.locker == null ? this.openRows
                : Math.max(SecurityLockerBE.BASE_ROWS, this.locker.rows());
    }

    public int visibleRows() {
        return VISIBLE_ROWS;
    }

    public boolean rowLive(int viewportRow) {
        return scrollRow() + viewportRow < rows();
    }

    public boolean scrollable() {
        return rows() > VISIBLE_ROWS;
    }

    public int gridBottom() {
        return TOP_H + VISIBLE_ROWS * ROW_H;
    }

    public static int rackSlotY(int index) {
        return TOP_H + RACK_SLOT_DY + index * 18;
    }

    public int invY() {
        return gridBottom() + INV_DY;
    }

    public int hotbarY() {
        return gridBottom() + HOTBAR_DY;
    }

    public int panelHeight() {
        return gridBottom() + BOTTOM_H;
    }

    private double stackMultiplier() {
        return this.locker == null ? this.openStackMultiplier
                : Math.max(1.0, this.locker.stackMultiplier());
    }

    public int stackCap() {
        return com.createstats.upgrade.UpgradeScale.stackCap(64, stackMultiplier(), this.maxStack);
    }

    public boolean deepened() {
        return stackMultiplier() > 1.0;
    }

    public int scrollRow() {
        return Math.max(0, Math.min(scrollRange(), this.scrollRow.get()));
    }

    public int visibleSlots() {
        return VISIBLE_ROWS * SecurityLockerBE.COLUMNS;
    }

    private boolean mapped(int visibleSlot) {
        final int index = scrollRow() * SecurityLockerBE.COLUMNS + visibleSlot;
        if (index < 0 || visibleSlot >= visibleSlots()) {
            return false;
        }
        return index < (this.locker == null ? this.openRows * SecurityLockerBE.COLUMNS
                : this.locker.slotCount());
    }

    public int scrollRange() {
        return Math.max(0, rows() - VISIBLE_ROWS);
    }

    public void setScrollRow(int row) {
        this.scrollRow.set(Math.max(0, Math.min(scrollRange(), row)));
    }

    private class ScrollWindow implements Container {
        private final SecurityLockerBE owner;

        ScrollWindow(SecurityLockerBE owner) {
            this.owner = owner;
        }

        private int map(int slot) {
            if (slot < 0 || slot >= visibleSlots()) {
                return -1;
            }
            final int index = scrollRow() * SecurityLockerBE.COLUMNS + slot;
            return index < this.owner.slotCount() ? index : -1;
        }

        @Override
        public int getContainerSize() {
            return visibleSlots();
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < getContainerSize(); i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            final int index = map(slot);
            return index < 0 ? ItemStack.EMPTY : this.owner.menuContainer().getItem(index);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            final int index = map(slot);
            return index < 0 ? ItemStack.EMPTY
                    : this.owner.menuContainer().removeItem(index, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            final int index = map(slot);
            return index < 0 ? ItemStack.EMPTY
                    : this.owner.menuContainer().removeItemNoUpdate(index);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            final int index = map(slot);
            if (index >= 0) {
                this.owner.menuContainer().setItem(index, stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return stackCap();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return com.createstats.upgrade.UpgradeScale.stackCap(
                    stack.isEmpty() ? 64 : stack.getMaxStackSize(),
                    SecurityLockerMenu.this.stackMultiplier(), SecurityLockerMenu.this.maxStack);
        }

        @Override
        public void setChanged() {
            this.owner.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < getContainerSize(); i++) {
                setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private class DeepContainer extends SimpleContainer {
        DeepContainer(int size) {
            super(size);
        }

        @Override
        public int getMaxStackSize() {
            return stackCap();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return com.createstats.upgrade.UpgradeScale.stackCap(
                    stack.isEmpty() ? 64 : stack.getMaxStackSize(),
                    SecurityLockerMenu.this.stackMultiplier(), SecurityLockerMenu.this.maxStack);
        }
    }

    private class StorageSlot extends Slot {
        StorageSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return mapped(getContainerSlot());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return mapped(getContainerSlot()) && super.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize() {
            return stackCap();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return com.createstats.upgrade.UpgradeScale.stackCap(
                    stack.isEmpty() ? 64 : stack.getMaxStackSize(),
                    SecurityLockerMenu.this.stackMultiplier(), SecurityLockerMenu.this.maxStack);
        }

        @Override
        public ItemStack safeTake(int count, int limit, Player player) {
            final ItemStack held = getItem();
            final int ordinary = held.isEmpty() ? 64 : Math.max(1, held.getMaxStackSize());
            return super.safeTake(count, Math.min(limit, ordinary), player);
        }
    }

    private class RackSlot extends Slot {
        RackSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return StorageUpgradeItem.of(stack) != null;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public boolean mayPickup(Player player) {
            final SecurityLockerBE be = SecurityLockerMenu.this.locker;
            return be == null || be.mayRemoveUpgrade(getContainerSlot());
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.locker == null || this.locker.isRemoved()) {
            return false;
        }
        final BlockPos pos = this.locker.getBlockPos();
        if (player.level().getBlockEntity(pos) != this.locker
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (!this.locker.isOpen()) {
            return false;
        }

        if (!player.level().isClientSide()) {
            this.locker.noteViewer();
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.SWAP && slotId >= 0 && slotId < this.storageEnd) {
            final ItemStack held = this.slots.get(slotId).getItem();
            if (!held.isEmpty() && held.getCount() > Math.max(1, held.getMaxStackSize())) {
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    private boolean insertIntoStorage(ItemStack stack) {
        if (this.locker == null || this.window == null) {
            return false;
        }
        final Container storage = this.locker.menuContainer();
        final int size = this.locker.slotCount();
        boolean moved = false;
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            final ItemStack held = storage.getItem(i);
            if (held.isEmpty() || !ItemStack.isSameItemSameComponents(held, stack)) {
                continue;
            }
            final int room = this.locker.stackCapFor(held) - held.getCount();
            if (room <= 0) {
                continue;
            }
            final int take = Math.min(room, stack.getCount());
            held.grow(take);
            stack.shrink(take);
            storage.setChanged();
            moved = true;
        }
        for (int i = 0; i < size && !stack.isEmpty(); i++) {
            if (!storage.getItem(i).isEmpty()) {
                continue;
            }
            storage.setItem(i, stack.split(Math.min(this.locker.stackCapFor(stack),
                    stack.getCount())));
            moved = true;
        }
        return moved;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        final Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack inSlot = slot.getItem();
        final ItemStack copy = inSlot.copy();
        final int playerEnd = this.slots.size();

        if (index < this.rackEnd) {
            if (!moveItemStackTo(inSlot, this.rackEnd, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (StorageUpgradeItem.of(inSlot) != null) {
            if (!moveItemStackTo(inSlot, this.storageEnd, this.rackEnd, false)
                    && !insertIntoStorage(inSlot)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertIntoStorage(inSlot)) {
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (inSlot.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, inSlot);
        return copy;
    }
}
