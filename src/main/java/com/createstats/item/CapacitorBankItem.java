package com.createstats.item;

import com.createstats.block.CapacitorBankBlock;
import com.createstats.blockentity.CapacitorBankBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class CapacitorBankItem extends BlockItem {
    public CapacitorBankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        final InteractionResult result = super.place(context);
        if (!result.consumesAction()) {
            return result;
        }
        completeLayer(context, context.getClickedPos());
        return result;
    }

    public void completeLayer(BlockPlaceContext context, BlockPos placedAt) {
        final Level level = context.getLevel();
        final Player player = context.getPlayer();
        if (level.isClientSide() || player == null || player.isShiftKeyDown()) {
            return;
        }
        if (!(level.getBlockEntity(placedAt) instanceof CapacitorBankBE bank)) {
            return;
        }
        final BlockPos min = bank.structureMin();
        final BlockPos max = bank.structureMax();
        if ((max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1) <= 1) {
            return;
        }

        final List<BlockPos> missing = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                final BlockPos at = new BlockPos(x, placedAt.getY(), z);
                final BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof CapacitorBankBlock) {
                    continue;
                }
                if (!state.canBeReplaced()) {
                    return;
                }
                missing.add(at);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        final ItemStack stack = context.getItemInHand();
        if (!player.isCreative() && stack.getCount() < missing.size()) {
            return;
        }
        final Direction face = context.getClickedFace();
        for (BlockPos at : missing) {
            super.place(BlockPlaceContext.at(context, at, face));
        }
    }

    public void completeLayer(Level level, Player player, net.minecraft.world.InteractionHand hand,
                              BlockPos placedAt, BlockHitResult ray) {
        completeLayer(new BlockPlaceContext(level, player, hand, player.getItemInHand(hand), ray), placedAt);
    }
}
