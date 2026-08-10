package com.createstats.vccmk2;

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

/** [1] */
public class VccMk2Menu extends AbstractContainerMenu {

    public static final int ROWS = VccMk2BlockEntity.CARD_ROWS;
    public static final int PROP_SLOT_START = 0;
    public static final int GOV_SLOT_START = PROP_SLOT_START + ROWS;
    public static final int FREQ_SLOT_START = GOV_SLOT_START + ROWS;
    public static final int PLAYER_SLOT_START = FREQ_SLOT_START + ROWS * 2;

    // [2]
    public static final int STAGE_X = 152;   // mirrors VccMk2Screen.NAV_W
    public static final int STAGE_Y = 26;    // mirrors VccMk2Screen.BAR_H
    public static final int PAD = 22;        // mirrors VccMk2Screen.PAD
    public static final int TABLE_TOP = STAGE_Y + 56;
    public static final int ROW_H = 20;
    public static final int COL_PART = STAGE_X + PAD + 22;
    public static final int COL_OUT = STAGE_X + PAD + 66;
    public static final int COL_TYPE = STAGE_X + PAD + 128;
    public static final int COL_ROLE = STAGE_X + PAD + 206;
    public static final int DOCK_TOP = TABLE_TOP + ROWS * ROW_H + 18;
    public static final int DOCK_X = STAGE_X + PAD + 8;

    public static int rowItemY(int row) {
        return TABLE_TOP + row * ROW_H + (ROW_H - 16) / 2;
    }

    public static int[] partSlotXY(int row) {
        return new int[]{COL_PART, rowItemY(row)};
    }

    public static int[] outSlotXY(int row, int which) {
        return new int[]{COL_OUT + which * 22, rowItemY(row)};
    }

    public static int[] dockSlotXY(int j) {
        int top = DOCK_TOP + 14;
        if (j < 27) {
            return new int[]{DOCK_X + (j % 9) * 18, top + (j / 9) * 18};
        }
        return new int[]{DOCK_X + (j - 27) * 18, top + 3 * 18 + 4};
    }

    @Nullable
    private final VccMk2BlockEntity blockEntity;
    private final BlockPos pos;
    private boolean clientBindActive;

    public VccMk2Menu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(containerId, inv, resolve(inv, buf.readBlockPos()));
    }

    public VccMk2Menu(int containerId, Inventory inv, @Nullable VccMk2BlockEntity be) {
        super(ModMenuTypes.VCC_MK_II.get(), containerId);
        this.blockEntity = be;
        this.pos = be != null ? be.getBlockPos() : BlockPos.ZERO;

        if (be != null) {
            ItemStackHandler cards = be.cards();
            ItemStackHandler freq = be.engineFreq();
            for (int row = 0; row < ROWS; row++) {
                int[] xy = partSlotXY(row);
                this.addSlot(new PartCardSlot(cards, VccMk2BlockEntity.propSlot(row), xy[0], xy[1]));
            }
            for (int row = 0; row < ROWS; row++) {
                int[] xy = outSlotXY(row, 0);
                this.addSlot(new GovernorSlot(cards, VccMk2BlockEntity.govSlot(row), row, xy[0], xy[1]));
            }
            for (int row = 0; row < ROWS; row++) {
                int[] a = outSlotXY(row, 0);
                int[] b = outSlotXY(row, 1);
                this.addSlot(new FreqSlot(freq, row * 2, row, a[0], a[1]));
                this.addSlot(new FreqSlot(freq, row * 2 + 1, row, b[0], b[1]));
            }
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    int[] xy = dockSlotXY(r * 9 + c);
                    this.addSlot(new DockSlot(inv, c + r * 9 + 9, xy[0], xy[1]));
                }
            }
            for (int c = 0; c < 9; c++) {
                int[] xy = dockSlotXY(27 + c);
                this.addSlot(new DockSlot(inv, c, xy[0], xy[1]));
            }
        }
    }

    @Nullable
    private static VccMk2BlockEntity resolve(Inventory inv, BlockPos pos) {
        Level level = inv.player.level();
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        if (!(be instanceof VccMk2BlockEntity) && level.isLoaded(pos)) {
            be = level.getBlockEntity(pos);
        }
        return (be instanceof VccMk2BlockEntity v) ? v : null;
    }

    @Nullable
    public VccMk2BlockEntity blockEntity() {
        return blockEntity;
    }

    public BlockPos pos() {
        return pos;
    }

    /** The screen calls this each frame so slots only render / accept clicks on the Bind station. */
    public void setBindActive(boolean active) {
        this.clientBindActive = active;
    }

    /** Server-side this is always true (so interactions validate); client-side it tracks the station. */
    public boolean isBindActive() {
        if (blockEntity != null) {
            Level level = blockEntity.getLevel();
            if (level != null && level.isClientSide()) {
                return clientBindActive;
            }
        }
        return true;
    }

    private boolean rowIsRedstone(int row) {
        return blockEntity != null && blockEntity.isRedstoneEngine(row);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_SLOT_START) {
            if (!this.moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved;
            if (stack.getItem() instanceof PropellerCardItem) {
                moved = this.moveItemStackTo(stack, PROP_SLOT_START, PROP_SLOT_START + ROWS, false);
            } else if (stack.getItem() instanceof GovernorCardItem) {
                moved = this.moveItemStackTo(stack, GOV_SLOT_START, GOV_SLOT_START + ROWS, false);
            } else {
                moved = false;
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // slot types
    private class PartCardSlot extends SlotItemHandler {
        PartCardSlot(ItemStackHandler handler, int idx, int x, int y) {
            super(handler, idx, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof PropellerCardItem;
        }

        @Override
        public boolean isActive() {
            return isBindActive();
        }
    }

    private class GovernorSlot extends SlotItemHandler {
        private final int row;

        GovernorSlot(ItemStackHandler handler, int idx, int row, int x, int y) {
            super(handler, idx, x, y);
            this.row = row;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !rowIsRedstone(row) && stack.getItem() instanceof GovernorCardItem;
        }

        @Override
        public boolean isActive() {
            return isBindActive() && !rowIsRedstone(row);
        }
    }

    private class FreqSlot extends SlotItemHandler {
        private final int row;

        FreqSlot(ItemStackHandler handler, int idx, int row, int x, int y) {
            super(handler, idx, x, y);
            this.row = row;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return rowIsRedstone(row);
        }

        @Override
        public boolean isActive() {
            return isBindActive() && rowIsRedstone(row);
        }
    }

    private class DockSlot extends Slot {
        DockSlot(Inventory inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }

        @Override
        public boolean isActive() {
            return isBindActive();
        }
    }
}
