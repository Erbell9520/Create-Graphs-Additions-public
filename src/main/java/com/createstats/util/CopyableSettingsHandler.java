package com.createstats.util;

import com.createstats.CreateStats;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class CopyableSettingsHandler {
    private CopyableSettingsHandler() {
    }

    @SubscribeEvent
    public static void rightClickToCopy(PlayerInteractEvent.RightClickBlock event) {
        interact(event, false);
    }

    @SubscribeEvent
    public static void leftClickToPaste(PlayerInteractEvent.LeftClickBlock event) {
        interact(event, true);
    }

    @Nullable
    public static CopyableSettings target(@Nullable BlockEntity be) {
        if (be instanceof SmartBlockEntity || !(be instanceof CopyableSettings settings)) {
            return null;
        }
        return settings.hasCopyableSettings() ? settings : null;
    }

    private static void interact(PlayerInteractEvent event, boolean paste) {
        final ItemStack stack = event.getItemStack();
        if (!AllBlocks.CLIPBOARD.isIn(stack)) {
            return;
        }
        final Player player = event.getEntity();
        if (player == null || player.isSpectator() || player.isShiftKeyDown()) {
            return;
        }
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();
        final CopyableSettings settings = target(level.getBlockEntity(pos));
        if (settings == null) {
            return;
        }
        ClipboardContent content = stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT, ClipboardContent.EMPTY);
        CompoundTag values = content.copiedValues().orElse(null);
        if (paste && values == null) {
            return;
        }
        if (!paste) {
            values = new CompoundTag();
        }
        final String key = settings.getClipboardKey();
        final boolean success;
        if (paste) {
            success = settings.readFromClipboard(level.registryAccess(), values.getCompound(key), player,
                    event.getFace(), level.isClientSide());
        } else {
            final CompoundTag own = new CompoundTag();
            success = settings.writeToClipboard(level.registryAccess(), own, event.getFace());
            if (success) {
                values.put(key, own);
            }
        }

        ((ICancellableEvent) event).setCanceled(true);
        if (event instanceof PlayerInteractEvent.RightClickBlock rightClick) {
            rightClick.setCancellationResult(InteractionResult.SUCCESS);
        }
        if (level.isClientSide() || !success) {
            return;
        }
        player.displayClientMessage(CreateLang.translate(paste ? "clipboard.pasted_to" : "clipboard.copied_from",
                        level.getBlockState(pos).getBlock().getName().withStyle(ChatFormatting.WHITE))
                .style(ChatFormatting.GREEN)
                .component(), true);
        if (!paste) {
            content = content.setType(ClipboardOverrides.ClipboardType.WRITTEN).setCopiedValues(values);
            stack.set(AllDataComponents.CLIPBOARD_CONTENT, content);
        }
    }
}
