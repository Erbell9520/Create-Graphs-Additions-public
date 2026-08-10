package com.createstats.flight.item;

import com.createstats.flight.blockentity.RpmGovernorBE;
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
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Governor Card, slotted into the FCC's "governor" slot of any binding row.
 *
 * <p>Linked by right-clicking an {@link RpmGovernorBE} in the world.
 * Sneak-right-click in air clears the link.</p>
 */
public class GovernorCardItem extends Item {

    /** Stable tag key in {@code DataComponents.CUSTOM_DATA}. */
    public static final String TAG_KEY = "FccGovernorCard";

    public GovernorCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof RpmGovernorBE)) {
            Player player = context.getPlayer();
            if (be != null && !level.isClientSide() && player != null) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.governor_card.not_governor")
                        .withStyle(ChatFormatting.RED),
                    true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        FlightCardData previous = FlightCardData.get(stack, TAG_KEY);
        FlightCardData data = new FlightCardData(pos, level.dimension().location());
        FlightCardData.set(stack, TAG_KEY, data);

        Player player = context.getPlayer();
        if (!level.isClientSide() && player != null) {
            Component msg;
            if (previous.isLinked() && previous.getLinkedPos() != null
                    && !previous.getLinkedPos().equals(pos)) {
                msg = Component.translatable("message.create_stats.governor_card.relinked",
                        formatPos(previous.getLinkedPos()), formatPos(pos))
                    .withStyle(ChatFormatting.GOLD);
            } else {
                msg = Component.translatable("message.create_stats.governor_card.linked", formatPos(pos))
                    .withStyle(ChatFormatting.GOLD);
            }
            player.displayClientMessage(msg, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        FlightCardData data = FlightCardData.get(stack, TAG_KEY);
        if (data.isLinked()) {
            data.clear();
            FlightCardData.set(stack, TAG_KEY, data);
            if (!level.isClientSide()) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.governor_card.cleared")
                        .withStyle(ChatFormatting.RED),
                    true);
            }
        } else if (!level.isClientSide()) {
            player.displayClientMessage(
                Component.translatable("message.create_stats.governor_card.empty")
                    .withStyle(ChatFormatting.GRAY),
                true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FlightCardData data = FlightCardData.get(stack, TAG_KEY);
        if (data.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.governor_card.tooltip.linked").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(formatPos(data.getLinkedPos())).withStyle(ChatFormatting.DARK_AQUA));
            if (data.getDimension() != null) {
                tooltip.add(Component.literal(data.getDimension().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("item.create_stats.governor_card.tooltip.hint")
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.create_stats.governor_card.tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return Component.translatable("create_stats.card.unset").getString();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
