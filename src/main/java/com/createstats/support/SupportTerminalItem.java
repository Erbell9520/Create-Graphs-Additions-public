package com.createstats.support;

import java.util.List;

import com.createstats.util.ClientScreenAccess;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class SupportTerminalItem extends Item {
    public SupportTerminalItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        boolean allowed = player instanceof ServerPlayer serverPlayer
            ? SupportSessions.access(serverPlayer).atLeast(SupportPolicy.Level.SAFE)
            : SupportClientState.canOpen(player);
        if (!allowed) {
            return InteractionResultHolder.pass(held);
        }

        if (level.isClientSide()) {
            ClientScreenAccess.openSupportTerminal();
        }
        return InteractionResultHolder.sidedSuccess(held, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Mod author support tooling.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click to open the terminal.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
