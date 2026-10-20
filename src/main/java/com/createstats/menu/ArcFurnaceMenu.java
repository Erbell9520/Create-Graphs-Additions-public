package com.createstats.menu;

import com.createstats.blockentity.ArcFurnaceBE;
import com.createstats.registry.ModMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

// furnace layout, you already know how to read it
public class ArcFurnaceMenu extends AbstractContainerMenu {
    public static final int SLOT_A_X = 29;
    public static final int SLOT_A_Y = 21;
    public static final int SLOT_B_X = 29;
    public static final int SLOT_B_Y = 47;
    public static final int SLOT_OUT_X = 151;
    public static final int SLOT_OUT_Y = 31;
    public static final int SLOT_SLAG_X = 151;
    public static final int SLOT_SLAG_Y = 59;
    public static final int INV_X = 8;
    public static final int INV_Y = 112;
    public static final int HOTBAR_Y = 170;

    public static final int DATA_ENERGY = 0;

    public static final int DATA_PROGRESS = 1;

    public static final int DATA_DURATION = 2;

    public static final int DATA_TAP = 3;

    public static final int DATA_FLAGS = 4;

    public static final int DATA_DRAW = 5;

    public static final int DATA_INTAKE = 6;

    public static final int DATA_GRADE = 7;
    public static final int DATA_SIZE = 8;

    public static final int FLAG_RUNNING = 1;
    public static final int FLAG_ARC = 2;
    public static final int FLAG_STARVED = 4;
    public static final int FLAG_SLAG_FULL = 8;

    public static final int FLAG_CUT = 16;

    public static final int FLAG_SHED = 32;

    public static final int FLAG_BLOCKED = 64;

    private final ArcFurnaceBE furnace;
    private final ContainerData data;

    public ArcFurnaceMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    public ArcFurnaceMenu(int containerId, Inventory playerInventory, ArcFurnaceBE furnace) {
        this(containerId, playerInventory, furnace,
                furnace == null ? new SimpleContainerData(DATA_SIZE) : furnace.dataAccess());
    }

    private ArcFurnaceMenu(int containerId, Inventory playerInventory, ArcFurnaceBE furnace,
                           ContainerData data) {
        super(ModMenuTypes.ARC_FURNACE.get(), containerId);
        this.furnace = furnace;
        this.data = data;
        final Container container = furnace == null
                ? new SimpleContainer(ArcFurnaceBE.SLOT_COUNT) : furnace.menuContainer();

        addSlot(new ChargeSlot(container, ArcFurnaceBE.SLOT_A, SLOT_A_X, SLOT_A_Y));
        addSlot(new ChargeSlot(container, ArcFurnaceBE.SLOT_B, SLOT_B_X, SLOT_B_Y));
        addSlot(new OutputSlot(container, ArcFurnaceBE.SLOT_OUT, SLOT_OUT_X, SLOT_OUT_Y));
        addSlot(new OutputSlot(container, ArcFurnaceBE.SLOT_SLAG, SLOT_SLAG_X, SLOT_SLAG_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18,
                        INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    private static ArcFurnaceBE resolve(Inventory playerInventory, BlockPos pos) {
        final BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof ArcFurnaceBE furnace ? furnace : null;
    }

    public ArcFurnaceBE furnace() {
        return furnace;
    }

    public int reading(int index) {
        return data.get(index);
    }

    public boolean flag(int mask) {
        return (data.get(DATA_FLAGS) & mask) != 0;
    }

    public int tap() {
        return Math.max(ArcFurnaceBE.MIN_TAP, Math.min(ArcFurnaceBE.MAX_TAP, data.get(DATA_TAP)));
    }

    public float progress() {
        final int duration = data.get(DATA_DURATION);
        if (duration <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, data.get(DATA_PROGRESS) / (float) duration);
    }

    public float charge() {
        return Math.min(1.0f, data.get(DATA_ENERGY) / (float) ArcFurnaceBE.BUFFER);
    }

    @Override
    public boolean stillValid(Player player) {
        if (furnace == null || furnace.isRemoved()) {
            return false;
        }
        final BlockPos pos = furnace.getBlockPos();
        return player.level().getBlockEntity(pos) == furnace
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        final Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        final ItemStack inSlot = slot.getItem();
        final ItemStack copy = inSlot.copy();
        final int playerStart = ArcFurnaceBE.SLOT_COUNT;
        final int playerEnd = slots.size();

        if (index < playerStart) {
            if (!moveItemStackTo(inSlot, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(inSlot, copy);
        } else if (!moveItemStackTo(inSlot, ArcFurnaceBE.SLOT_A, ArcFurnaceBE.SLOT_B + 1, false)) {
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

    private class ChargeSlot extends Slot {
        ChargeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return furnace == null || furnace.isCharge(stack);
        }
    }

    private static class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
