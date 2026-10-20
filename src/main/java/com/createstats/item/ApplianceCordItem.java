package com.createstats.item;

import com.createstats.blockentity.WallOutletBE;
import com.createstats.power.CordRoute;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ApplianceCordItem extends Item {
    private static final Map<UUID, BlockPos> PENDING = new HashMap<>();

    public ApplianceCordItem(Properties properties) {
        super(properties);
    }

    public static void clearPending(UUID playerId) {
        PENDING.remove(playerId);
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
            return isCordTarget(level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WallOutletBE outlet) {
            return player.isShiftKeyDown()
                    ? unplug(player, outlet, context.getClickLocation()) : take(player, pos);
        }
        final BlockPos socket = PENDING.get(player.getUUID());
        if (socket == null) {
            return InteractionResult.PASS;
        }
        return plug(player, level, socket, pos, context.getItemInHand());
    }

    private static boolean isCordTarget(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WallOutletBE
                || level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null) != null;
    }

    private static InteractionResult take(Player player, BlockPos socket) {
        PENDING.put(player.getUUID(), socket.immutable());
        say(player, "Socket held. Click the machine to plug it in.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult unplug(Player player, WallOutletBE outlet, Vec3 hit) {
        PENDING.remove(player.getUUID());
        if (!outlet.unplug(hit)) {
            say(player, "Nothing is plugged into that socket.");
            return InteractionResult.FAIL;
        }
        final Level level = outlet.getLevel();
        if (level != null) {
            level.playSound(null, outlet.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.6f, 1.1f);
        }
        say(player, "Unplugged.");
        return InteractionResult.CONSUME;
    }

    private static InteractionResult plug(Player player, Level level, BlockPos socket,
                                          BlockPos machine, ItemStack stack) {
        if (!level.isLoaded(socket) || !(level.getBlockEntity(socket) instanceof WallOutletBE outlet)) {
            PENDING.remove(player.getUUID());
            say(player, "That socket is gone.");
            return InteractionResult.FAIL;
        }
        final String refusal = outlet.plugIn(machine);
        if (refusal != null) {
            say(player, refusal);
            return InteractionResult.FAIL;
        }
        PENDING.remove(player.getUUID());
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, machine, SoundEvents.WOODEN_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.7f, 0.8f);
        say(player, String.format(Locale.ROOT, "Plugged in. %d FE/t through the socket.",
                WallOutletBE.RATED_FE_PER_TICK));
        return InteractionResult.CONSUME;
    }

    private static void say(Player player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,
                "item.create_stats.appliance_cord", List.of(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.note(
                        "item.create_stats.appliance_cord.tooltip.use")));
    }
}
