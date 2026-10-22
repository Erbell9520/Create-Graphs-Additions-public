package com.createstats.compat;

import com.createstats.CreateStats;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.item.CopperWireItem;
import com.createstats.power.FeStudPanel;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.ForeignTerminalNode;
import com.createstats.power.PduStudWiring;
import com.createstats.power.WireTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

// their wire item, our link
@EventBusSubscriber(modid = CreateStats.MOD_ID)
public final class ForeignSpoolHandler {
    private static final Map<UUID, BlockPos> PENDING_BRIDGE = new HashMap<>();

    private ForeignSpoolHandler() {
    }

    public static void clearPending(UUID playerId) {
        PENDING_BRIDGE.remove(playerId);
    }

    public static boolean hasPendingBridge(@Nullable Player player) {
        return player != null && PENDING_BRIDGE.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        if (event.getUsePhase() != UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK) {
            return;
        }
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final ItemStack held = event.getItemStack();
        final ForeignSpools.Spool spool = ForeignSpools.of(held);
        final boolean remover = ForeignSpools.isRemover(held);
        final boolean ourWire = held.getItem() instanceof CopperWireItem;
        if (spool == null && !remover && !ourWire) {
            return;
        }
        final Level level = event.getLevel();
        final BlockPos pos = event.getPos();

        if (ForeignTerminalNode.isBridgeTarget(level, pos)) {
            if (!ourWire && !(spool != null && CopperWireItem.pendingOf(player.getUUID()) != null)) {
                return;
            }
            finish(event, onForeignConnector(event.getUseOnContext(), level, pos, player, held, ourWire));
            return;
        }
        if (finishesCouplerSeam(level, pos, player)) {
            finish(event, closeCouplerSeam(event.getUseOnContext(), level, pos, player, held, ourWire));
            return;
        }
        if (spool == null && !remover) {
            return;
        }
        if (!isOurWiring(level, pos)) {
            return;
        }
        finish(event, onOurTerminal(event.getUseOnContext(), level, pos, player, held, remover));
    }

    private static boolean isOurWiring(Level level, BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        return be instanceof FeWireTerminal || be instanceof FeStudPanel;
    }

    private static boolean finishesCouplerSeam(Level level, BlockPos pos, Player player) {
        final PduStudWiring.Endpoint pending = CopperWireItem.pendingOf(player.getUUID());
        if (pending == null || pending.isStud()) {
            return false;
        }
        return level.getBlockEntity(BlockPos.of(pending.pos()))
                        instanceof com.createstats.blockentity.UniversalPowerCouplerBE
                && com.createstats.blockentity.UniversalPowerCouplerBE.isSeamTarget(level, pos);
    }

    private static InteractionResult closeCouplerSeam(UseOnContext context, Level level, BlockPos pos,
                                                      Player player, ItemStack held, boolean ourWire) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final PduStudWiring.Endpoint pending = CopperWireItem.pendingOf(player.getUUID());
        if (pending == null
                || !(level.getBlockEntity(BlockPos.of(pending.pos())) instanceof FeWireTerminal terminal)) {
            CopperWireItem.clearPending(player.getUUID());
            actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
            return InteractionResult.CONSUME;
        }

        final String wrongSocket = com.createstats.power.CouplerSockets.refusalKey(
                CopperWireItem.pendingSocketOf(player.getUUID()), true, false);
        if (wrongSocket != null) {
            CopperWireItem.clearPending(player.getUUID());
            actionBar(player, Component.translatable(wrongSocket));
            return InteractionResult.CONSUME;
        }
        final Component refusal = refuse(level, terminal, pos);
        if (refusal != null) {
            actionBar(player, refusal);
            return InteractionResult.CONSUME;
        }
        strike(level, player, terminal, pos, tierInHand(held, ourWire), stockInHand(held, ourWire),
                context.getItemInHand());
        CopperWireItem.clearPending(player.getUUID());
        actionBar(player, bridged(level, terminal, pos));
        return InteractionResult.CONSUME;
    }

    private static InteractionResult onOurTerminal(UseOnContext context, Level level, BlockPos pos,
                                                   Player player, ItemStack held, boolean remover) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (remover) {
            return CopperWireItem.useWith(context, WireTier.COPPER, ForeignSpoolHandler::spendNothing, true);
        }
        final WireTier tier = ForeignSpools.tierOf(held);
        if (tier == null) {
            return InteractionResult.PASS;
        }
        final BiConsumer<Player, ItemStack> stock = ForeignSpools.stockFor(held);

        final BlockPos anchor = ForeignAnchorReader.peek(held);
        if (anchor != null && ForeignTerminalNode.isBridgeTarget(level, anchor)) {
            final Component wrongEnd = refuseForeignEnd(level, pos, context);
            if (wrongEnd != null) {
                actionBar(player, wrongEnd);
                return InteractionResult.CONSUME;
            }
            if (!(level.getBlockEntity(pos) instanceof FeWireTerminal terminal)) {
                actionBar(player, Component.translatable("message.create_stats.foreign_wire.stud_no_bridge"));
                return InteractionResult.CONSUME;
            }
            final Component refusal = refuse(level, terminal, anchor);
            if (refusal != null) {
                actionBar(player, refusal);
                return InteractionResult.CONSUME;
            }
            strike(level, player, terminal, anchor, tier, stock, context.getItemInHand());
            ForeignAnchorReader.clear(held);
            CopperWireItem.clearPending(player.getUUID());
            actionBar(player, Component.translatable("message.create_stats.foreign_wire.adopted",
                    held.getHoverName(), nameOf(level, anchor)));
            return InteractionResult.CONSUME;
        }
        return CopperWireItem.useWith(context, tier, stock, player.isShiftKeyDown());
    }

    @Nullable
    private static Component refuseForeignEnd(Level level, BlockPos pos, UseOnContext context) {
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof com.createstats.block.UniversalPowerCouplerBlock)) {
            return null;
        }

        final com.createstats.power.CouplerSockets.End socket = com.createstats.power.CouplerSockets.endAt(state,
                pos, context.getClickLocation(), context.getClickedFace());
        final String refusal = com.createstats.power.CouplerSockets.refusalKey(
                socket == null ? com.createstats.power.CouplerSockets.End.GRID : socket, true, true);
        return refusal == null ? null : Component.translatable(refusal);
    }

    @Nullable
    private static Component refuseForeignSocket(Level level, FeWireTerminal terminal, UseOnContext context) {
        if (!(terminal instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler)) {
            return null;
        }
        final com.createstats.power.CouplerSockets.End socket = com.createstats.power.CouplerSockets.endAt(
                coupler.getBlockState(), coupler.getBlockPos(), context.getClickLocation(), context.getClickedFace());
        final String refusal = com.createstats.power.CouplerSockets.refusalKey(
                socket == null ? com.createstats.power.CouplerSockets.End.GRID : socket, true, true);
        return refusal == null ? null : Component.translatable(refusal);
    }

    private static InteractionResult onForeignConnector(UseOnContext context, Level level, BlockPos pos,
                                                        Player player, ItemStack held, boolean ourWire) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        final PduStudWiring.Endpoint pending = CopperWireItem.pendingOf(player.getUUID());
        if (pending != null) {
            final BlockPos startPos = BlockPos.of(pending.pos());
            if (!(level.getBlockEntity(startPos) instanceof FeWireTerminal terminal)) {
                CopperWireItem.clearPending(player.getUUID());
                actionBar(player, Component.translatable(pending.isStud()
                        ? "message.create_stats.foreign_wire.stud_no_bridge"
                        : "message.create_stats.copper_wire.lost_first"));
                return InteractionResult.CONSUME;
            }

            final String wrongSocket = com.createstats.power.CouplerSockets.refusalKey(
                    CopperWireItem.pendingSocketOf(player.getUUID()), true, false);
            if (wrongSocket != null) {
                CopperWireItem.clearPending(player.getUUID());
                actionBar(player, Component.translatable(wrongSocket));
                return InteractionResult.CONSUME;
            }
            final Component refusal = refuse(level, terminal, pos);
            if (refusal != null) {
                actionBar(player, refusal);
                return InteractionResult.CONSUME;
            }
            final WireTier tier = tierInHand(held, ourWire);
            strike(level, player, terminal, pos, tier, stockInHand(held, ourWire), context.getItemInHand());
            CopperWireItem.clearPending(player.getUUID());
            actionBar(player, bridged(level, terminal, pos));
            return InteractionResult.CONSUME;
        }
        if (!ourWire) {
            return InteractionResult.PASS;
        }
        final BlockPos already = PENDING_BRIDGE.get(player.getUUID());
        if (already != null) {
            if (already.equals(pos)) {
                PENDING_BRIDGE.remove(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.cancelled"));
                return InteractionResult.CONSUME;
            }
            actionBar(player, Component.translatable("message.create_stats.foreign_wire.both_foreign"));
            return InteractionResult.CONSUME;
        }
        PENDING_BRIDGE.put(player.getUUID(), pos.immutable());
        level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, 1.4f);
        actionBar(player, Component.translatable("message.create_stats.foreign_wire.attached",
                nameOf(level, pos)));
        return InteractionResult.CONSUME;
    }

    public static InteractionResult finishBridge(UseOnContext context, Player player, Level level,
                                                 FeWireTerminal terminal, WireTier tier,
                                                 BiConsumer<Player, ItemStack> stock) {
        final BlockPos foreign = PENDING_BRIDGE.get(player.getUUID());
        if (foreign == null) {
            return InteractionResult.PASS;
        }
        if (!level.isLoaded(foreign) || !ForeignTerminalNode.isBridgeTarget(level, foreign)) {
            PENDING_BRIDGE.remove(player.getUUID());
            actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
            return InteractionResult.CONSUME;
        }

        final Component wrongSocket = refuseForeignSocket(level, terminal, context);
        if (wrongSocket != null) {
            actionBar(player, wrongSocket);
            return InteractionResult.CONSUME;
        }
        final Component refusal = refuse(level, terminal, foreign);
        if (refusal != null) {
            actionBar(player, refusal);
            return InteractionResult.CONSUME;
        }
        strike(level, player, terminal, foreign, tier, stock, context.getItemInHand());
        PENDING_BRIDGE.remove(player.getUUID());
        actionBar(player, bridged(level, terminal, foreign));
        return InteractionResult.CONSUME;
    }

    public static boolean refuseBridgeFromStud(Player player) {
        if (!hasPendingBridge(player)) {
            return false;
        }
        actionBar(player, Component.translatable("message.create_stats.foreign_wire.stud_no_bridge"));
        return true;
    }

    @Nullable
    private static Component refuse(Level level, FeWireTerminal terminal, BlockPos foreign) {
        if (terminal.links().contains(foreign)) {
            return Component.translatable("message.create_stats.copper_wire.already_linked");
        }
        if (terminal.links().size() >= terminal.maxLinks()) {
            return Component.translatable("message.create_stats.copper_wire.full");
        }

        final Component ownRule = terminal.refuseLink(level, foreign);
        if (ownRule != null) {
            return ownRule;
        }
        final int range = PowerConnectorBE.wireRange();
        if (terminal.terminalPos().distSqr(foreign) > (double) range * range) {
            return Component.translatable("message.create_stats.copper_wire.too_far", range);
        }
        if (ForeignTerminalNode.at(level, foreign, WireTier.COPPER) == null
                && !com.createstats.blockentity.UniversalPowerCouplerBE.isSeamTarget(level, foreign)) {
            return Component.translatable("message.create_stats.copper_wire.lost_first");
        }
        return null;
    }

    private static void strike(Level level, Player player, FeWireTerminal terminal, BlockPos foreign,
                               WireTier tier, BiConsumer<Player, ItemStack> stock, ItemStack held) {
        terminal.addLink(foreign.immutable(), tier);
        stock.accept(player, held);
        level.playSound(null, foreign, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
    }

    private static Component bridged(Level level, FeWireTerminal terminal, BlockPos foreign) {
        final Component plain = Component.translatable("message.create_stats.foreign_wire.bridged",
                nameOf(level, foreign));
        if (!(terminal instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler)
                || coupler.mode() != com.createstats.power.PowerCouplerMode.PULL) {
            return plain;
        }
        final BlockEntity endpoint = level.getBlockEntity(foreign);
        if (!NewAgeCompatHelper.isElectricalConnector(endpoint)
                || NewAgeCompatHelper.pullSourceCount(endpoint) > 0) {
            return plain;
        }
        return Component.translatable("message.create_stats.foreign_wire.bridged_needs_pull",
                nameOf(level, foreign));
    }

    private static WireTier tierInHand(ItemStack held, boolean ourWire) {
        if (ourWire && held.getItem() instanceof CopperWireItem wire) {
            return wire.tier();
        }
        final WireTier foreign = ForeignSpools.tierOf(held);
        return foreign == null ? WireTier.COPPER : foreign;
    }

    private static BiConsumer<Player, ItemStack> stockInHand(ItemStack held, boolean ourWire) {
        return ourWire ? ForeignSpoolHandler::spendOne : ForeignSpools.stockFor(held);
    }

    private static void spendOne(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static void spendNothing(Player player, ItemStack stack) {
    }

    private static Component nameOf(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName();
    }

    private static void actionBar(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
    }

    private static void finish(UseItemOnBlockEvent event, InteractionResult result) {
        switch (result) {
            case SUCCESS -> event.cancelWithResult(ItemInteractionResult.SUCCESS);
            case CONSUME -> event.cancelWithResult(ItemInteractionResult.CONSUME);
            case CONSUME_PARTIAL -> event.cancelWithResult(ItemInteractionResult.CONSUME_PARTIAL);
            case FAIL -> event.cancelWithResult(ItemInteractionResult.FAIL);
            case PASS -> {
            }
        }
    }
}
