package com.createstats.block;

import com.createstats.blockentity.OverflowGateBE;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.tunnel.BrassTunnelBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Item;

public class OverflowGateBlock extends BrassTunnelBlock {
    public OverflowGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        InteractionResult result = onBlockEntityUse(world, pos, be -> interact(be, world, pos, player));
        if (result == InteractionResult.SUCCESS) return ItemInteractionResult.SUCCESS;
        if (result == InteractionResult.CONSUME) return ItemInteractionResult.CONSUME;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
        BlockHitResult hit) {
        return onBlockEntityUse(world, pos, be -> interact(be, world, pos, player));
    }

    private InteractionResult interact(BlockEntity be, Level world, BlockPos pos, Player player) {
        if (!(be instanceof OverflowGateBE gate)) {
            return InteractionResult.PASS;
        }

        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            List<ItemStack> storedStacks = gate.grabStoredStacks(false);
            if (storedStacks.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.create_stats.overflow_gate.buffer_empty").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.SUCCESS;
            }

            for (ItemStack itemStack : storedStacks) {
                player.getInventory().placeItemBackInInventory(itemStack.copy());
                player.displayClientMessage(Component.translatable("message.create_stats.overflow_gate.collected",
                    itemStack.getCount(), itemStack.getHoverName()).withStyle(ChatFormatting.GOLD), true);
            }
            world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
                1f + world.random.nextFloat());
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(gate.getRoutingSummary(), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends OverflowGateBE> getBlockEntityType() {
        return ModBlockEntities.OVERFLOW_GATE.get();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "block.create_stats.overflow_gate");
    }
}
