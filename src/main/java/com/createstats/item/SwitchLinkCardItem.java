package com.createstats.item;

import com.createstats.blockentity.LampBE;
import com.createstats.blockentity.SwitchBE;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** [1] */
public class SwitchLinkCardItem extends Item {

    /** The switch each player is currently holding, cleared on logout. */
    private static final Map<UUID, BlockPos> HELD = new HashMap<>();

    public SwitchLinkCardItem(Properties properties) {
        super(properties);
    }

    public static void clearPending(UUID playerId) {
        HELD.remove(playerId);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Player player = context.getPlayer();
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            HELD.remove(player.getUUID());
            say(player, "message.create_stats.switch_link.cleared", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof SwitchBE) {
            HELD.put(player.getUUID(), pos.immutable());
            say(player, "message.create_stats.switch_link.held", ChatFormatting.AQUA);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof LampBE) {
            final BlockPos switchPos = HELD.get(player.getUUID());
            if (switchPos == null) {
                say(player, "message.create_stats.switch_link.no_switch", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if (!(level.getBlockEntity(switchPos) instanceof SwitchBE sw)) {
                // The switch was broken between the two clicks; drop the stale hold rather than
                // silently doing nothing.
                HELD.remove(player.getUUID());
                say(player, "message.create_stats.switch_link.gone", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            final boolean added = sw.toggleLink(pos);
            player.displayClientMessage(Component.translatable(
                    added ? "message.create_stats.switch_link.linked"
                          : "message.create_stats.switch_link.unlinked",
                    sw.linkedLamps().size()).withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                    true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void say(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.switch_link_card.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.switch_link_card.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.create_stats.switch_link_card.tooltip.note")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
