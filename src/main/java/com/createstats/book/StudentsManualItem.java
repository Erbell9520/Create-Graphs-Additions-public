package com.createstats.book;

import com.createstats.util.ClientScreenAccess;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class StudentsManualItem extends Item {
    public static final String MOD_ID = "create_stats";

    public StudentsManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientScreenAccess.openStudentsManual(null);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(context.getLevel()
                .getBlockState(context.getClickedPos()).getBlock());
        if (!MOD_ID.equals(id.getNamespace())) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            ClientScreenAccess.openStudentsManual(id.toString());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, "item.create_stats.students_manual");
    }
}
