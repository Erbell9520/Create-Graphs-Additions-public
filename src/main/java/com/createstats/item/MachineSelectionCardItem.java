package com.createstats.item;

import java.util.List;

import javax.annotation.Nullable;

import com.createstats.factorylogic.MachineSelectionCardData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

public class MachineSelectionCardItem extends Item {

    private static final String TAG_KEY = "MachineSelectionCard";

    public MachineSelectionCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        ResourceLocation dimensionId = level.dimension().location();
        MachineSelectionCardData data = getData(stack);

        if (player != null && player.isShiftKeyDown()) {
            Block clickedBlock = level.getBlockState(clickedPos).getBlock();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(clickedBlock);
            if (blockId == null) {
                return InteractionResult.PASS;
            }
            data.setMachineBlockId(blockId);
            setData(stack, data);
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.create_stats.machine_card.type_set", getBlockDisplayName(blockId))
                        .withStyle(ChatFormatting.GOLD), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (data.getFirstCorner() == null || data.getSecondCorner() != null) {
            data.startAreaSelection(clickedPos, dimensionId);
            setData(stack, data);
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.create_stats.machine_card.corner_a", formatPos(clickedPos))
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (data.hasDimensionMismatch(dimensionId)) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.create_stats.machine_card.dimension_mismatch")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (data.getDimensionId() == null) {
            data.setDimensionId(dimensionId);
        }
        data.setSecondCorner(clickedPos);
        setData(stack, data);
        if (!level.isClientSide() && player != null) {
            player.displayClientMessage(Component.translatable("message.create_stats.machine_card.corner_b",
                    formatPos(clickedPos), data.getAreaSizeLabel()).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        MachineSelectionCardData data = getData(stack);
        data.clearAll();
        setData(stack, data);
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.create_stats.machine_card.cleared")
                    .withStyle(ChatFormatting.RED), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        MachineSelectionCardData data = getData(stack);
        if (data.hasMachineType()) {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.machine", getBlockDisplayName(data.getMachineBlockId()))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.choose")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.getFirstCorner() != null) {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.corner_a", formatPos(data.getFirstCorner()))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (data.getSecondCorner() != null) {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.corner_b", formatPos(data.getSecondCorner()), data.getAreaSizeLabel())
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else if (data.getFirstCorner() != null) {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.set_b")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.set_ab")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (data.getFirstCorner() != null && data.getDimensionId() != null) {
            tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.dimension", data.getDimensionId().toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.idle", data.getIdleTimeoutSeconds())
                .withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("item.create_stats.machine_selection_card.tooltip.clear")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MachineSelectionCardData getData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return new MachineSelectionCardData();
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TAG_KEY)) {
            return new MachineSelectionCardData();
        }
        return MachineSelectionCardData.load(tag.getCompound(TAG_KEY));
    }

    public static void setData(ItemStack stack, MachineSelectionCardData data) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_KEY, data.save()));
    }

    private static Component getBlockDisplayName(@Nullable ResourceLocation blockId) {
        if (blockId == null) {
            return Component.translatable("message.create_stats.machine_card.unset");
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null) {
            return Component.literal(blockId.toString());
        }
        return Component.translatable(block.getDescriptionId());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
