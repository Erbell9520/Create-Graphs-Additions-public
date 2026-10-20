package com.createstats.item;

import com.createstats.power.BoreLinks;
import com.createstats.power.IndoorCable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// through, not around
public class CableBoreItem extends Item {
    public CableBoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Player player = context.getPlayer();
        final Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }

        final Direction into = context.getClickedFace().getOpposite();
        final BlockPos near = nearEnd(level, context.getClickedPos(), context.getClickedFace());
        if (near == null) {
            if (level.isClientSide()) {
                say(player, "Point at what is in the way, with cable behind you.", true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return player.isShiftKeyDown()
                ? pull(level, player, near, into)
                : cut(level, player, near, into);
    }

    @Nullable
    private static BlockPos nearEnd(Level level, BlockPos clicked, Direction face) {
        final BlockPos outside = clicked.relative(face);
        if (BoreLinks.hostAt(level, outside) != null) {
            return outside;
        }
        return BoreLinks.hostAt(level, clicked) != null ? clicked : null;
    }

    private static InteractionResult cut(Level level, Player player, BlockPos near, Direction into) {
        if (BoreLinks.hostAt(level, near) instanceof BoreLinks.Host host
                && host.bores().stream().anyMatch(
                        far -> BoreLinks.axisBetween(host.boreAnchorFor(far), far) == into)) {
            say(player, "Already bored that way.", true);
            return InteractionResult.FAIL;
        }
        final BlockPos partner = BoreLinks.findPartner(level, near, into);
        if (partner == null) {
            say(player, String.format(Locale.ROOT,
                    "No matching cable within %d blocks that way.", IndoorCable.MAX_BORE), true);
            return InteractionResult.FAIL;
        }
        if (!BoreLinks.join(level, near, partner)) {
            say(player, "Nothing to bore through.", true);
            return InteractionResult.FAIL;
        }
        level.playSound(null, near, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7f, 1.4f);

        final List<BlockPos> ends = BoreLinks.ends(level, near, partner);
        final BlockPos from = ends.isEmpty() ? near : ends.get(0);
        final BlockPos to = ends.isEmpty() ? partner : ends.get(1);
        final List<BlockPos> through = BoreLinks.crossings(from, to);
        showCut(level, from, to, through);
        say(player, String.format(Locale.ROOT, "Bored through %d block%s.",
                through.size(), through.size() == 1 ? "" : "s"), true);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult pull(Level level, Player player, BlockPos near, Direction into) {
        final BoreLinks.Host host = BoreLinks.hostAt(level, near);
        if (host == null) {
            return InteractionResult.FAIL;
        }
        for (BlockPos far : List.copyOf(host.bores())) {
            final BlockPos anchor = host.boreAnchorFor(far);
            if (BoreLinks.axisBetween(anchor, far) == into && BoreLinks.part(level, anchor, far)) {
                level.playSound(null, near, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f);
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    for (BlockPos at : BoreLinks.crossings(anchor, far)) {
                        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                                at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.0);
                    }
                }
                say(player, "Bore pulled.", true);
                return InteractionResult.CONSUME;
            }
        }
        say(player, "No bore that way.", true);
        return InteractionResult.FAIL;
    }

    private static void showCut(Level level, BlockPos near, BlockPos far, List<BlockPos> through) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        for (BlockPos at : through) {
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            net.minecraft.core.particles.ParticleTypes.BLOCK, server.getBlockState(at)),
                    at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.0);
        }

        for (BlockPos mouth : List.of(near, far)) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    mouth.getX() + 0.5, mouth.getY() + 0.5, mouth.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private static void say(Player player, String message, boolean actionBar) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), actionBar);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "item.create_stats.cable_bore", List.of(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.note(
                        "item.create_stats.cable_bore.tooltip.use")));
    }
}
