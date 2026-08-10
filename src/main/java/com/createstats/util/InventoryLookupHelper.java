package com.createstats.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/** [1] */
public final class InventoryLookupHelper {

    private InventoryLookupHelper() {
    }

    @Nullable
    public static IItemHandler resolveItemHandler(Level level, BlockPos pos, @Nullable BlockEntity be, @Nullable Direction accessFace) {
        if (level == null || pos == null) {
            return null;
        }
        BlockEntity blockEntity = be;
        if (blockEntity == null) {
            blockEntity = level.getBlockEntity(pos);
        }

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, accessFace);
        if (handler == null && accessFace != null) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        }
        if (handler != null) {
            return handler;
        }

        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            final Container container = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (container != null) {
                return new InvWrapper(container);
            }
        }

        return null;
    }
}
