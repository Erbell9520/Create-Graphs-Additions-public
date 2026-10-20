package com.createstats.item;

import com.createstats.block.CableBlock;
import com.createstats.blockentity.CableBE;
import com.createstats.power.CablePlacement;
import com.createstats.power.CableRoute;
import com.createstats.power.CableRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class CableItem extends BlockItem {
    public CableItem(CableBlock block, Properties properties) {
        super(block, properties);
    }

    private CableBlock cable() {
        return (CableBlock) getBlock();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Player player = context.getPlayer();
        final Level level = context.getLevel();
        if (player == null) {
            return super.useOn(context);
        }
        final BlockPos clicked = context.getClickedPos();
        final Direction face = context.getClickedFace();

        if (player.isShiftKeyDown()) {
            return sneak(context, player, level, clicked);
        }

        if (CablePlacement.anchorOf(player) != null) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return extendRun(context, player, level, clicked, face);
        }

        return startRun(context, player, level, clicked, face);
    }

    private InteractionResult sneak(UseOnContext context, Player player, Level level, BlockPos clicked) {
        if (CablePlacement.anchorOf(player) != null) {
            if (!level.isClientSide()) {
                CablePlacement.clear(player);
                say(player, "message.create_stats.cable.cancelled", ChatFormatting.GRAY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!(level.getBlockState(clicked).getBlock() instanceof CableBlock)) {
            return super.useOn(context);
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return ripUp(player, level, clicked);
    }

    private InteractionResult ripUp(Player player, Level level, BlockPos clicked) {
        final List<BlockPos> run = CableRun.collect(
                CableBlock.linkViewOf(level), clicked, CableRun.MAX_RUN_BLOCKS);
        int taken = 0;
        for (BlockPos at : run) {
            final BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof CableBlock kind)) {
                continue;
            }

            int faces = 0;
            for (Direction f : Direction.values()) {
                if (CableBlock.hasFace(state, f)) {
                    faces++;
                }
            }
            level.setBlock(at, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            for (int i = 0; i < faces; i++) {
                if (!player.getInventory().add(new ItemStack(kind.asItem()))) {
                    player.drop(new ItemStack(kind.asItem()), false);
                }
                taken++;
            }
        }
        if (taken == 0) {
            return InteractionResult.PASS;
        }
        player.displayClientMessage(Component.translatable(
                "message.create_stats.cable.ripped", taken).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }

    private InteractionResult startRun(UseOnContext context, Player player, Level level,
                                       BlockPos clicked, Direction face) {
        if (level.getBlockState(clicked).getBlock() instanceof CableBlock) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            final Direction mount = mountedFaceOf(level.getBlockState(clicked), face);
            if (mount == null) {
                return InteractionResult.PASS;
            }
            CablePlacement.setAnchor(player, new CableRoute.Step(clicked.immutable(), mount));
            say(player, "message.create_stats.cable.resumed", ChatFormatting.AQUA);
            return InteractionResult.CONSUME;
        }

        final InteractionResult placed = super.useOn(context);
        if (!placed.consumesAction() || level.isClientSide()) {
            return placed;
        }

        for (BlockPos landed : new BlockPos[]{clicked.relative(face), clicked}) {
            if (level.getBlockState(landed).getBlock() instanceof CableBlock) {
                CablePlacement.setAnchor(player, new CableRoute.Step(landed.immutable(), face.getOpposite()));
                say(player, "message.create_stats.cable.anchored", ChatFormatting.AQUA);
                break;
            }
        }
        return placed;
    }

    private InteractionResult extendRun(UseOnContext context, Player player, Level level,
                                        BlockPos clicked, Direction face) {
        final CableRoute.Step anchor = CablePlacement.anchorOf(player);
        final CableRoute.Step step =
                CablePlacement.endpointFor(level, clicked, face, context.getClickLocation());
        if (anchor.equals(step)) {
            return InteractionResult.SUCCESS;
        }
        final List<CableRoute.Step> route = CablePlacement.route(level, anchor, step);
        if (route.isEmpty()) {
            say(player, "message.create_stats.cable.no_route", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        final int needed = CablePlacement.cost(level, route, cable());
        final ItemStack held = context.getItemInHand();
        final int available = player.getAbilities().instabuild ? needed : held.getCount();
        if (available < needed) {
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.cable.short", needed, available)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        final int placed = CablePlacement.place(level, route, cable(), runColour(level, anchor), needed);
        if (!player.getAbilities().instabuild) {
            held.shrink(placed);
        }

        if (isPowerTerminal(level, clicked)) {
            CablePlacement.clear(player);
            say(player, "message.create_stats.cable.connected", ChatFormatting.GREEN);
        } else {
            CablePlacement.setAnchor(player, step);
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.cable.run", placed).withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static DyeColor runColour(Level level, CableRoute.Step anchor) {
        final BlockState at = level.getBlockState(anchor.pos());
        return at.getBlock() instanceof CableBlock ? at.getValue(CableBlock.COLOR) : DyeColor.ORANGE;
    }

    private static Direction mountedFaceOf(BlockState state, Direction clickedFace) {
        if (CableBlock.hasFace(state, clickedFace.getOpposite())) {
            return clickedFace.getOpposite();
        }
        for (Direction f : Direction.values()) {
            if (CableBlock.hasFace(state, f)) {
                return f;
            }
        }
        return null;
    }

    private static boolean isPowerTerminal(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CableBE) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            if (level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                    pos, dir) != null) {
                return true;
            }
        }
        return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                pos, null) != null;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        return cable().getStateForPlacement(context);
    }

    private static void say(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, getDescriptionId(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec("tooltip.create_stats.wire_rate",
                        com.createstats.power.FeRates.perTick(
                                com.createstats.power.FeRates.cable(cable().tier())))),
                List.of(com.createstats.util.CreateStatsTooltipHelper.note("tooltip.create_stats.cable.assist"),
                        com.createstats.util.CreateStatsTooltipHelper.note("tooltip.create_stats.cable.colour")));
    }
}
