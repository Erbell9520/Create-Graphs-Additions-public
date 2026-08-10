package com.createstats.item;

import java.util.List;

import javax.annotation.Nullable;

import com.createstats.blockentity.ControlClutchBE;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.factorylogic.SafetyClutchLinkData;
import com.createstats.factorylogic.SafetyClutchLinkData.TargetType;

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

public class SafetyClutchLinkCardItem extends Item {

    private static final String TAG_KEY = "SafetyClutchLinkCard";

    public SafetyClutchLinkCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        TargetType targetType;
        if (be instanceof SafetyClutchBE) {
            targetType = TargetType.SAFETY_CLUTCH;
        } else if (be instanceof ControlClutchBE) {
            targetType = TargetType.CONTROL_CLUTCH;
        } else if (be instanceof KineticFlywheelBE) {
            targetType = TargetType.KINETIC_FLYWHEEL;
        } else {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        SafetyClutchLinkData data = new SafetyClutchLinkData();
        data.setTargetType(targetType);
        data.setDimensionId(level.dimension().location());
        data.setClutchPos(pos);
        setData(stack, data);

        Player player = context.getPlayer();
        if (!level.isClientSide() && player != null) {
            player.displayClientMessage(Component.translatable("message.create_stats.clutch_card.linked", data.getTargetDisplayName(), formatPos(pos))
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        SafetyClutchLinkData data = getData(stack);
        data.clear();
        setData(stack, data);
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.create_stats.clutch_card.cleared")
                    .withStyle(ChatFormatting.RED), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        SafetyClutchLinkData data = getData(stack);
        if (data.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.safety_clutch_link_card.tooltip.linked", data.getTargetDisplayName())
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(formatPos(data.getClutchPos()))
                    .withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.literal(String.valueOf(data.getDimensionId()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.create_stats.safety_clutch_link_card.tooltip.hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.create_stats.safety_clutch_link_card.tooltip.clear")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static SafetyClutchLinkData getData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.copyTag().contains(TAG_KEY)) {
            return new SafetyClutchLinkData();
        }
        return SafetyClutchLinkData.load(customData.copyTag().getCompound(TAG_KEY));
    }

    public static void setData(ItemStack stack, SafetyClutchLinkData data) {
        if (!data.isLinked()) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                tag.remove(TAG_KEY);
                if (tag.isEmpty()) {
                    stack.remove(DataComponents.CUSTOM_DATA);
                } else {
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
            }
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_KEY, data.save()));
    }

    private static String formatPos(@Nullable BlockPos pos) {
        if (pos == null) {
            return "Unset";
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
