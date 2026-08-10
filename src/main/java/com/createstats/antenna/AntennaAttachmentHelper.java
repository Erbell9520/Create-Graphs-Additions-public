package com.createstats.antenna;

import com.createstats.item.SignalAntennaItem;
import com.createstats.registry.ModItems;
import com.simibubi.create.AllItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class AntennaAttachmentHelper {
    private AntennaAttachmentHelper() {
    }

    public static ItemInteractionResult handleUse(ItemStack stack, Level level, Player player, InteractionHand hand, BlockEntity target) {
        if (!(target instanceof RangeAttachment attachment)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof SignalAntennaItem antennaItem) {
            return attach(stack, level, player, attachment, antennaItem.getTier());
        }
        if (player.isShiftKeyDown() && attachment.hasAntenna() && (stack.isEmpty() || AllItems.WRENCH.isIn(stack))) {
            return remove(level, player, attachment);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static ItemInteractionResult attach(ItemStack stack, Level level, Player player, RangeAttachment attachment, AntennaTier tier) {
        if (attachment.hasAntenna()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.create_stats.antenna.already_attached").withStyle(ChatFormatting.RED), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!level.isClientSide()) {
            attachment.setAntennaTier(tier);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
                player.displayClientMessage(Component.translatable("message.create_stats.antenna.attached", tierLabel(tier))
                    .append(Component.literal(" "))
                    .append(Component.translatable("message.create_stats.antenna.status." + tier.getSerializedName()))
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private static ItemInteractionResult remove(Level level, Player player, RangeAttachment attachment) {
        AntennaTier tier = attachment.getAntennaTier();
        if (!level.isClientSide()) {
            attachment.removeAntenna();
            ItemStack drop = itemFor(tier);
            if (!drop.isEmpty() && !player.addItem(drop)) {
                player.drop(drop, false);
            }
            player.displayClientMessage(Component.translatable("message.create_stats.antenna.removed", tierLabel(tier)).withStyle(ChatFormatting.YELLOW), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private static ItemStack itemFor(AntennaTier tier) {
        return switch (tier) {
            case SMALL -> new ItemStack(ModItems.SMALL_SIGNAL_ANTENNA.get());
            case ADVANCED -> new ItemStack(ModItems.ADVANCED_SIGNAL_ANTENNA.get());
            case TRT -> new ItemStack(ModItems.TRT_SIGNAL_ANTENNA.get());
            default -> ItemStack.EMPTY;
        };
    }

    private static Component tierLabel(AntennaTier tier) {
        return Component.translatable("item.create_stats.signal_antenna.tier." + tier.getSerializedName());
    }
}