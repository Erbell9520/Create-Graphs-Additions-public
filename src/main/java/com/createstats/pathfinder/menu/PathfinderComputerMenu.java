package com.createstats.pathfinder.menu;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class PathfinderComputerMenu extends AbstractContainerMenu {
    public static final int BUTTON_CLEAR_LINK = 1;
    public static final int BUTTON_REFRESH_LINK = 2;
    public static final int BUTTON_SPEED_DOWN = 10;
    public static final int BUTTON_SPEED_UP = 11;
    public static final int BUTTON_CORNER_CLEARANCE_BASE = 30;

    private final PathfinderComputerBE computer;

    public PathfinderComputerMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(containerId, inv, lookupBE(inv, extraData.readBlockPos()));
    }

    public PathfinderComputerMenu(int containerId, Inventory inv, @Nullable PathfinderComputerBE computer) {
        super(ModMenuTypes.PATHFINDER_COMPUTER.get(), containerId);
        if (computer == null) {
            throw new IllegalStateException("PathfinderComputerMenu opened without a backing block entity");
        }
        this.computer = computer;
    }

    public PathfinderComputerBE getComputer() {
        return computer;
    }

    @Override
    public boolean stillValid(Player player) {
        return computer.isPlayerInInteractionRange(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!computer.isPlayerInInteractionRange(player)) return false;
        if (id >= BUTTON_CORNER_CLEARANCE_BASE
            && id <= BUTTON_CORNER_CLEARANCE_BASE + PathfinderComputerBE.CORNER_CLEARANCE_STEPS) {
            computer.setCornerClearanceStep(id - BUTTON_CORNER_CLEARANCE_BASE);
            return true;
        }
        switch (id) {
            case BUTTON_CLEAR_LINK -> computer.clearLinkedScanner();
            case BUTTON_REFRESH_LINK -> computer.refreshLinkedScanner();
            case BUTTON_SPEED_DOWN -> computer.adjustMaxGuidanceSpeed(-1.0f);
            case BUTTON_SPEED_UP -> computer.adjustMaxGuidanceSpeed(1.0f);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static PathfinderComputerBE lookupBE(Inventory inv, BlockPos pos) {
        Level level = inv.player.level();
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(level, pos);
        if (!(blockEntity instanceof PathfinderComputerBE) && level.isLoaded(pos)) {
            blockEntity = level.getBlockEntity(pos);
        }
        return blockEntity instanceof PathfinderComputerBE computer ? computer : null;
    }
}
