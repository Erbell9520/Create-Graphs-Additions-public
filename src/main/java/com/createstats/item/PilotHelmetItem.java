package com.createstats.item;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.util.ClientScreenAccess;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PilotHelmetItem extends ArmorItem {
    private static final String TAG_KEY = "PilotHelmet";

    public PilotHelmetItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        PilotHelmetData data = getData(stack);
        String targetLabel;
        PilotHelmetData.LinkedNode targetNode;
        if (blockEntity instanceof FlightControlComputerBE computer) {
            targetLabel = "create_stats.pilot_helmet.link.fcc";
            targetNode = data.getFccLink();
            targetNode.setTargetPos(computer.getBlockPos());
            targetNode.setTargetName(Component.translatable("block.create_stats.flight_control_computer").getString());
        } else if (blockEntity instanceof BattleScreenDisplayBE display) {
            targetLabel = "create_stats.pilot_helmet.link.battle_bridge";
            targetNode = data.getBattleBridgeLink();
            targetNode.setTargetPos(display.getBlockPos());
            targetNode.setTargetName(Component.translatable("block.create_stats.battle_screen_display").getString());
        } else if (blockEntity instanceof StatsDisplayPanelBE panel) {
            targetLabel = "create_stats.pilot_helmet.link.stats_display";
            targetNode = data.getStatsDisplayLink();
            StatsDisplayPanelBE controller = panel.getControllerBE();
            targetNode.setTargetPos(controller.getBlockPos());
            targetNode.setTargetName(Component.translatable("block.create_stats.stats_display_panel").getString());
        } else {
            return InteractionResult.PASS;
        }
        targetNode.setDimensionId(level.dimension().location());
        setData(stack, data);
        if (!level.isClientSide() && player != null) {
            player.displayClientMessage(Component.translatable("message.create_stats.pilot_helmet.linked", Component.translatable(targetLabel), targetNode.getTargetName()).withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            ClientScreenAccess.openPilotHelmet(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.summary").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.feature.armor").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.feature.goggles").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.feature.hud").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.feature.link").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.feature.setup").withStyle(ChatFormatting.DARK_GRAY));

        PilotHelmetData data = getData(stack);
        tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.mode", data.getMode().getDisplayName()).withStyle(ChatFormatting.AQUA));
        appendLinkTooltip(tooltip, "create_stats.pilot_helmet.link.fcc", data.getFccLink());
        appendLinkTooltip(tooltip, "create_stats.pilot_helmet.link.battle_bridge", data.getBattleBridgeLink());
        appendLinkTooltip(tooltip, "create_stats.pilot_helmet.link.stats_display", data.getStatsDisplayLink());

        if (!net.neoforged.fml.ModList.get().isLoaded("geckolib")) {
            tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.geckolib_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void appendLinkTooltip(List<Component> tooltip, String labelKey, PilotHelmetData.LinkedNode link) {
        if (link.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.pilot_helmet.tooltip.link_line", Component.translatable(labelKey), link.getTargetName()).withStyle(ChatFormatting.GOLD));
        }
    }

    public static PilotHelmetData getData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.copyTag().contains(TAG_KEY)) {
            return new PilotHelmetData();
        }
        return PilotHelmetData.load(customData.copyTag().getCompound(TAG_KEY));
    }

    public static void setData(ItemStack stack, PilotHelmetData data) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG_KEY, data.save()));
    }
}
