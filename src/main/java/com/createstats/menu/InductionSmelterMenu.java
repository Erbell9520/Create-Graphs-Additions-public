package com.createstats.menu;

import com.createstats.blockentity.InductionSmelterBE;
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

public class InductionSmelterMenu extends AbstractContainerMenu {
    public static final int SLOT_IN_X = 27;
    public static final int SLOT_IN_Y = 33;
    public static final int SLOT_OUT_X = 147;
    public static final int SLOT_OUT_Y = 33;
    public static final int INV_X = 8;
    public static final int INV_Y = 112;
    public static final int HOTBAR_Y = 170;

    public static final int DATA_ENERGY = 0;

    public static final int DATA_HEAT = 1;

    public static final int DATA_PROGRESS = 2;

    public static final int DATA_DURATION = 3;

    public static final int DATA_DAMPER = 4;

    public static final int DATA_FLAGS = 5;

    public static final int DATA_DRAW = 6;

    public static final int DATA_RATED = 7;

    public static final int DATA_INTAKE = 8;

    public static final int DATA_BANK = 9;
    public static final int DATA_SIZE = 10;

    public static final int FLAG_WORKING = 1;

    public static final int FLAG_HOT = 2;
    public static final int FLAG_STARVED = 4;

    public static final int FLAG_BLASTING = 8;

    public static final int FLAG_CUT = 16;

    public static final int FLAG_SHED = 32;

    public static final int FLAG_BLOCKED = 64;

    public static final int FLAG_NEEDS_BLAST = 128;

    public static final int FLAG_NO_RECIPE = 256;

    private final InductionSmelterBE smelter;
    private final ContainerData data;

    public InductionSmelterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_SIZE));
    }

    public InductionSmelterMenu(int containerId, Inventory playerInventory, InductionSmelterBE smelter) {
        this(containerId, playerInventory, smelter,
                smelter == null ? new SimpleContainerData(DATA_SIZE) : smelter.dataAccess());
    }

    private InductionSmelterMenu(int containerId, Inventory playerInventory,
                                 InductionSmelterBE smelter, ContainerData data) {
        super(ModMenuTypes.INDUCTION_SMELTER.get(), containerId);
        this.smelter = smelter;
        this.data = data;
        final Container container = smelter == null
                ? new SimpleContainer(InductionSmelterBE.SLOT_COUNT) : smelter.menuContainer();

        addSlot(new ChargeSlot(container, InductionSmelterBE.SLOT_IN, SLOT_IN_X, SLOT_IN_Y));
        addSlot(new OutputSlot(container, InductionSmelterBE.SLOT_OUT, SLOT_OUT_X, SLOT_OUT_Y));

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

    private static InductionSmelterBE resolve(Inventory playerInventory, BlockPos pos) {
        final BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof InductionSmelterBE smelter ? smelter : null;
    }

    public InductionSmelterBE smelter() {
        return smelter;
    }

    public int reading(int index) {
        return data.get(index);
    }

    public boolean flag(int mask) {
        return (data.get(DATA_FLAGS) & mask) != 0;
    }

    public int damper() {
        return Math.max(0, Math.min(InductionSmelterBE.DAMPER_PERCENT.length - 1,
                data.get(DATA_DAMPER)));
    }

    public float heat() {
        return Math.min(1.0f, data.get(DATA_HEAT) / (float) InductionSmelterBE.HEAT_MAX);
    }

    public float heatCap() {
        return InductionSmelterBE.DAMPER_PERCENT[damper()] / 100.0f;
    }

    public float progress() {
        final int duration = data.get(DATA_DURATION);
        if (duration <= 0) {
            return 0.0f;
        }
        return Math.min(1.0f, data.get(DATA_PROGRESS) / (float) duration);
    }

    public float charge() {
        return Math.min(1.0f, data.get(DATA_ENERGY) / (float) InductionSmelterBE.BUFFER);
    }

    @Override
    public boolean stillValid(Player player) {
        if (smelter == null || smelter.isRemoved()) {
            return false;
        }
        final BlockPos pos = smelter.getBlockPos();
        return player.level().getBlockEntity(pos) == smelter
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
        final int playerStart = InductionSmelterBE.SLOT_COUNT;
        final int playerEnd = slots.size();

        if (index < playerStart) {
            if (!moveItemStackTo(inSlot, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(inSlot, copy);
        } else if (!moveItemStackTo(inSlot, InductionSmelterBE.SLOT_IN,
                InductionSmelterBE.SLOT_IN + 1, false)) {
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
            return smelter == null || smelter.isProcessable(stack);
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
