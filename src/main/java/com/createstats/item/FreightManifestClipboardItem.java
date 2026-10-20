package com.createstats.item;

import java.util.List;
import javax.annotation.Nullable;
import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.train.manifest.FreightStationLink;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FreightManifestClipboardItem extends BlockItem {
    private static final String TAG_STATION_LINK = "FreightStationLink";

    public FreightManifestClipboardItem(Block block, Properties properties) {
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
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            GlobalStation station = stationBlockEntity.getStation();
            if (station == null || station.getId() == null) {
                if (!level.isClientSide() && player != null) {
                    player.displayClientMessage(Component.translatable("message.create_stats.station_link.not_ready").withStyle(ChatFormatting.RED), false);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }

            String stationName = station.name != null ? station.name : Component.translatable("create_stats.station.unnamed").getString();
            // the clicked block is the truth, old graph data can be (0,0,0)
            ResourceLocation dimensionId = level.dimension().location();
            FreightStationLink link = new FreightStationLink(station.getId(), stationName, dimensionId, pos);
            setStationLink(stack, link);

            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.create_stats.clipboard.linked", stationName).withStyle(ChatFormatting.GOLD), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        if (!hasStationLink(stack)) {
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable("message.create_stats.clipboard.link_first").withStyle(ChatFormatting.RED), false);
            }
            return InteractionResult.FAIL;
        }

        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FreightStationLink link = getStationLink(stack);
        if (link.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.freight_manifest_clipboard.tooltip.linked", link.getStationName()).withStyle(ChatFormatting.GOLD));
            if (link.getStationDimension() != null) {
                tooltip.add(Component.literal(link.getStationDimension().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("block.create_stats.freight_manifest_clipboard.tooltip.link").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("block.create_stats.freight_manifest_clipboard.tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
        if (link.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.freight_manifest_clipboard.tooltip.relink").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean hasStationLink(ItemStack stack) {
        return getStationLink(stack).isLinked();
    }

    public static FreightStationLink getStationLink(ItemStack stack) {
        CompoundTag tag = getStationLinkTag(stack);
        if (tag == null) {
            return new FreightStationLink();
        }
        return FreightStationLink.load(tag);
    }

    public static void setStationLink(ItemStack stack, FreightStationLink link) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (!link.isLinked()) {
                tag.remove(TAG_STATION_LINK);
                if (tag.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
                    blockEntityTag.remove(FreightManifestClipboardBE.TAG_STATION_LINK);
                    blockEntityTag.remove(TAG_STATION_LINK);
                    if (blockEntityTag.isEmpty()) {
                        tag.remove("BlockEntityTag");
                    } else {
                        tag.put("BlockEntityTag", blockEntityTag);
                    }
                }
                return;
            }
            tag.put(TAG_STATION_LINK, link.save());
            CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
            blockEntityTag.put(FreightManifestClipboardBE.TAG_STATION_LINK, link.save());
            tag.put("BlockEntityTag", blockEntityTag);
        });
    }

    @Nullable
    private static CompoundTag getStationLinkTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (tag.contains(TAG_STATION_LINK)) {
            return tag.getCompound(TAG_STATION_LINK);
        }
        if (tag.contains("BlockEntityTag")) {
            CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");
            if (blockEntityTag.contains(FreightManifestClipboardBE.TAG_STATION_LINK)) {
                return blockEntityTag.getCompound(FreightManifestClipboardBE.TAG_STATION_LINK);
            }
            if (blockEntityTag.contains(TAG_STATION_LINK)) {
                return blockEntityTag.getCompound(TAG_STATION_LINK);
            }
        }
        return null;
    }
}
