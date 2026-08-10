package com.createstats.item;

import com.createstats.train.manifest.FreightStationLink;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DispatchBoardItem extends FreightManifestClipboardItem {

    public DispatchBoardItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (be instanceof StationBlockEntity stationBlockEntity) {
            GlobalStation station = stationBlockEntity.getStation();
            if (station == null || station.getBlockEntityPos() == null || station.getBlockEntityDimension() == null) {
                if (!level.isClientSide() && player != null) {
                    player.displayClientMessage(Component.translatable("message.create_stats.station_link.not_ready").withStyle(ChatFormatting.RED), false);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }

            String stationName = station.name != null ? station.name : Component.translatable("create_stats.station.unnamed").getString();
            ResourceLocation dimensionId = station.getBlockEntityDimension().location();
            setStationLink(stack, new FreightStationLink(station.getId(), stationName, dimensionId, station.getBlockEntityPos()));
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.create_stats.dispatch_board_item.linked", stationName).withStyle(ChatFormatting.GOLD), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!hasStationLink(stack)) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.create_stats.dispatch_board_item.link_first").withStyle(ChatFormatting.RED), false);
            }
            return InteractionResult.FAIL;
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FreightStationLink link = getStationLink(stack);
        if (link.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.dispatch_board.tooltip.linked", link.getStationName()).withStyle(ChatFormatting.GOLD));
            if (link.getStationDimension() != null) {
                tooltip.add(Component.literal(link.getStationDimension().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("block.create_stats.dispatch_board.tooltip.link").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("block.create_stats.dispatch_board.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
        if (link.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.dispatch_board.tooltip.relink").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
