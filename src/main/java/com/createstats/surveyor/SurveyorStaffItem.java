package com.createstats.surveyor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

// decided on the client where the aim is, checked on the server
public class SurveyorStaffItem extends Item {
    public SurveyorStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) {
            SurveyorClientBridge.call("useOnBlock", UseOnContext.class, ctx);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) {
            SurveyorClientBridge.call("useAir", Player.class, player);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.surveyor_staff.tooltip.summary").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.surveyor_staff.tooltip.modes").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.surveyor_staff.tooltip.board").withStyle(ChatFormatting.DARK_GRAY));
    }
}
