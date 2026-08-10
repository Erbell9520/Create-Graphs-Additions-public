package com.createstats.item;

import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatsRecorderBE;
import com.createstats.data.MemoryCogData;
import com.createstats.util.ClientScreenAccess;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class StatsHandheldItem extends Item {

    private static final String TAG_KEY = "StatsHandheldLink";

    public StatsHandheldItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        HandheldLinkData data = new HandheldLinkData();
        if (be instanceof StatsDisplayPanelBE panel) {
            StatsDisplayPanelBE controller = panel.getControllerBE();
            data.setTargetType(HandheldLinkData.TargetType.STATS_DISPLAY);
            data.setTargetPos(controller.getBlockPos());
            data.setTargetName(Component.translatable("block.create_stats.stats_display_panel").getString());
        } else if (be instanceof StatsRecorderBE recorder) {
            data.setTargetType(HandheldLinkData.TargetType.STATS_RECORDER);
            data.setTargetPos(recorder.getBlockPos());
            data.setTargetName(Component.translatable("block.create_stats.stats_recorder").getString());
        } else if (be instanceof DispatchBoardBE board) {
            DispatchBoardBE controller = board.getControllerBE();
            data.setTargetType(HandheldLinkData.TargetType.DISPATCH_BOARD);
            data.setTargetPos(controller.getBlockPos());
            data.setTargetName(Component.translatable("block.create_stats.dispatch_board").getString());
        } else {
            return InteractionResult.PASS;
        }

        data.setDimensionId(level.dimension().location());
        setLinkData(stack, data);
        if (!level.isClientSide() && player != null) {
            player.displayClientMessage(Component.translatable("message.create_stats.stats_handheld.linked", data.getTargetName()).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            HandheldLinkData data = getLinkData(stack);
            if (data.isLinked()) {
                data.clear();
                setLinkData(stack, data);
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable("message.create_stats.stats_handheld.cleared").withStyle(ChatFormatting.RED), true);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            }
        }
        if (level.isClientSide()) {
            ClientScreenAccess.openStatsHandheld(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("create_stats.tooltip.handheld").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.stats_handheld.tooltip.remote").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.stats_handheld.tooltip.local").withStyle(ChatFormatting.DARK_GRAY));
        HandheldLinkData data = getLinkData(stack);
        if (data.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.stats_handheld.tooltip.linked", data.getTargetName()).withStyle(ChatFormatting.GOLD));
            if (data.getDimensionId() != null) {
                tooltip.add(Component.literal(data.getDimensionId().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("item.create_stats.stats_handheld.tooltip.link_hint").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.create_stats.stats_handheld.tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static HandheldLinkData getLinkData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.copyTag().contains(TAG_KEY)) {
            return new HandheldLinkData();
        }
        return HandheldLinkData.load(customData.copyTag().getCompound(TAG_KEY));
    }

    public static void setLinkData(ItemStack stack, HandheldLinkData data) {
        if (!data.isLinked()) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                return;
            }
            CompoundTag tag = customData.copyTag();
            tag.remove(TAG_KEY);
            if (tag.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            } else {
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_KEY, data.save()));
    }

    public static ItemStack getReadableCog(Player player, InteractionHand handheldHand) {
        InteractionHand otherHand = handheldHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        return MemoryCogData.hasData(otherStack) ? otherStack : ItemStack.EMPTY;
    }
}
