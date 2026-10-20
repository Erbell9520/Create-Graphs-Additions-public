package com.createstats.item;

import com.createstats.power.FixtureLinker;
import com.createstats.power.Gateable;
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
import java.util.Objects;
import java.util.UUID;

public class SwitchLinkCardItem extends Item {
    private static final Map<UUID, Held> HELD = new HashMap<>();

    private record Held(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos,
                        @org.jetbrains.annotations.Nullable UUID identity) {
    }

    public SwitchLinkCardItem(Properties properties) {
        super(properties);
    }

    public static void clearPending(UUID playerId) {
        HELD.remove(playerId);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        final Player player = context.getPlayer();
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            HELD.remove(player.getUUID());
            say(player, "message.create_stats.switch_link.cleared", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof FixtureLinker host) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            if (!host.mayLink(player)) {
                HELD.remove(player.getUUID());
                say(player, "message.create_stats.switch_link.not_yours", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            HELD.put(player.getUUID(), new Held(level.dimension(), pos.immutable(), host.linkIdentity()));
            say(player, "message.create_stats.switch_link.held", ChatFormatting.AQUA);
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof Gateable
                || com.createstats.security.SecurityDevices.isDevice(level.getBlockEntity(pos))) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            final Held held = HELD.get(player.getUUID());
            if (held == null) {
                say(player, "message.create_stats.switch_link.no_switch", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            // refuse without looking, looking would load the chunk

            if (!held.dimension().equals(level.dimension()) || !level.isLoaded(held.pos())) {
                HELD.remove(player.getUUID());
                say(player, "message.create_stats.switch_link.out_of_reach", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if (!(level.getBlockEntity(held.pos()) instanceof FixtureLinker sw)
                    || !Objects.equals(sw.linkIdentity(), held.identity())) {
                HELD.remove(player.getUUID());
                say(player, "message.create_stats.switch_link.gone", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            if (!sw.accepts(level.getBlockEntity(pos))) {
                say(player, "message.create_stats.switch_link.wrong_kind", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            final FixtureLinker.Outcome outcome = sw.link(pos, player);
            if (outcome == FixtureLinker.Outcome.REFUSED) {
                say(player, "message.create_stats.switch_link.refused", ChatFormatting.RED);
                return InteractionResult.FAIL;
            }
            final boolean added = outcome == FixtureLinker.Outcome.LINKED;
            player.displayClientMessage(Component.translatable(
                    added ? "message.create_stats.switch_link.linked"
                          : "message.create_stats.switch_link.unlinked",
                    sw.linkedFixtures().size()).withStyle(added ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                    true);

            if (added && !player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static void say(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "item.create_stats.switch_link_card", List.of(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.note(
                                "item.create_stats.switch_link_card.tooltip.note"),
                        com.createstats.util.CreateStatsTooltipHelper.note(
                                "item.create_stats.switch_link_card.tooltip.spent")));
    }
}
