package com.createstats.item;

import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import java.util.Set;
import java.util.HashSet;
import com.createstats.display.StatsDisplaySettings;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.blockentity.StatsAlarmBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.blockentity.HolographicProjectorBE;
import com.createstats.util.ClientScreenAccess;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;

public class StatsConfiguratorItem extends Item
{
    private static final String TAG_COPIED = "CopiedSettings";

    public StatsConfiguratorItem(Item.Properties properties) {
        super(properties);
    }

    public InteractionResult useOn(UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final BlockEntity be = level.getBlockEntity(pos);
        final Player player = context.getPlayer();
        if (be instanceof HolographicProjectorBE projector) {
            if (level.isClientSide()) {
                ClientScreenAccess.openProjectorSettings(projector);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (be instanceof StatsAlarmBE alarm) {
            if (level.isClientSide()) {
                ClientScreenAccess.openAlarmSettings(alarm);
            }
            else if (player != null && alarm.getOwnerUUID() == null) {
                alarm.setOwnerUUID(player.getUUID());
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (be instanceof StatusTowerLightBE towerLight) {
            if (level.isClientSide()) {
                ClientScreenAccess.openStatusTowerLight(towerLight);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!(be instanceof StatsDisplayPanelBE)) {
            return InteractionResult.PASS;
        }
        final StatsDisplayPanelBE panel = (StatsDisplayPanelBE)be;
        StatsDisplayPanelBE controller = panel.isController() ? panel : this.getController(level, panel);
        if (controller == null) {
            controller = panel;
        }
        final ItemStack stack = context.getItemInHand();
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                final StatsDisplaySettings settings = controller.getSettings();
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put("CopiedSettings", (Tag)settings.save()));
                player.displayClientMessage(Component.translatable("message.create_stats.configurator.copied"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("CopiedSettings")) {
            if (!level.isClientSide()) {
                final CompoundTag copiedTag = customData.copyTag().getCompound("CopiedSettings");
                final StatsDisplaySettings loadedSettings = StatsDisplaySettings.load(copiedTag);
                loadedSettings.setHiddenChannelIds(new HashSet<String>());
                controller.setSettings(loadedSettings);
                level.sendBlockUpdated(controller.getBlockPos(), controller.getBlockState(), controller.getBlockState(), 3);
                CompoundTag remaining = customData.copyTag();
                remaining.remove("CopiedSettings");
                if (remaining.isEmpty()) {
                    stack.remove(DataComponents.CUSTOM_DATA);
                } else {
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(remaining));
                }
                player.displayClientMessage(Component.translatable("message.create_stats.configurator.applied"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) {
            final StatsDisplayPanelBE ctrl = controller;
            ClientScreenAccess.openSettings(ctrl);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("CopiedSettings")) {
            tooltip.add(Component.translatable("item.create_stats.stats_configurator.tooltip.holding"));
        }
        else {
            tooltip.add(Component.translatable("item.create_stats.stats_configurator.tooltip.copy"));
            tooltip.add(Component.translatable("item.create_stats.stats_configurator.tooltip.paste"));
            tooltip.add(Component.translatable("item.create_stats.stats_configurator.tooltip.alarm"));
        }
    }

    @Nullable
    private StatsDisplayPanelBE getController(Level level, StatsDisplayPanelBE member) {
        final BlockPos controllerPos = member.getControllerPos();
        if (controllerPos != null) {
            final BlockEntity be = level.getBlockEntity(controllerPos);
            if (be instanceof StatsDisplayPanelBE ctrl) {
                if (ctrl.isController()) {
                    return ctrl;
                }
            }
        }
        return null;
    }
}
