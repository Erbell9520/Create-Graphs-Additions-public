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

/** [1] */
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

        // Already running: carry the line on to wherever the player pointed.
        if (CablePlacement.anchorOf(player) != null) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return extendRun(context, player, level, clicked, face);
        }

        // [2]
        return startRun(context, player, level, clicked, face);
    }

    /** [3] */
    private InteractionResult sneak(UseOnContext context, Player player, Level level, BlockPos clicked) {
        if (CablePlacement.anchorOf(player) != null) {
            if (!level.isClientSide()) {
                CablePlacement.clear(player.getUUID());
                say(player, "message.create_stats.cable.cancelled", ChatFormatting.GRAY);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!(level.getBlockState(clicked).getBlock() instanceof CableBlock)) {
            return super.useOn(context);   // sneaking on a wall is an ordinary placement
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return ripUp(player, level, clicked);
    }

    /** [4] */
    private InteractionResult ripUp(Player player, Level level, BlockPos clicked) {
        final List<BlockPos> run = CableRun.collect(
                CableBlock.linkViewOf(level), clicked, CableRun.MAX_RUN_BLOCKS);
        int taken = 0;
        for (BlockPos at : run) {
            final BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof CableBlock kind)) {
                continue;
            }
            // [5]
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

    /** [6] */
    private InteractionResult startRun(UseOnContext context, Player player, Level level,
                                       BlockPos clicked, Direction face) {
        if (level.getBlockState(clicked).getBlock() instanceof CableBlock) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            // anchor on the existing block/face the player clicked, so the router starts from a
            // step that's actually there
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
        // [7]
        for (BlockPos landed : new BlockPos[]{clicked.relative(face), clicked}) {
            if (level.getBlockState(landed).getBlock() instanceof CableBlock) {
                CablePlacement.setAnchor(player, new CableRoute.Step(landed.immutable(), face.getOpposite()));
                say(player, "message.create_stats.cable.anchored", ChatFormatting.AQUA);
                break;
            }
        }
        return placed;
    }

    /** [8] */
    private InteractionResult extendRun(UseOnContext context, Player player, Level level,
                                        BlockPos clicked, Direction face) {
        final CableRoute.Step anchor = CablePlacement.anchorOf(player);
        final CableRoute.Step step =
                CablePlacement.endpointFor(level, clicked, face, context.getClickLocation());
        if (anchor.equals(step)) {
            return InteractionResult.SUCCESS;   // clicked where the line already ends
        }
        final List<CableRoute.Step> route = CablePlacement.route(level, anchor, step);
        if (route.isEmpty()) {
            say(player, "message.create_stats.cable.no_route", ChatFormatting.RED);
            return InteractionResult.FAIL;      // keep running so a nearer aim can retry
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
        // [9]
        if (isPowerTerminal(level, clicked)) {
            CablePlacement.clear(player.getUUID());
            say(player, "message.create_stats.cable.connected", ChatFormatting.GREEN);
        } else {
            CablePlacement.setAnchor(player, step);
            player.displayClientMessage(Component.translatable(
                    "message.create_stats.cable.run", placed).withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** [10] */
    private static DyeColor runColour(Level level, CableRoute.Step anchor) {
        final BlockState at = level.getBlockState(anchor.pos());
        return at.getBlock() instanceof CableBlock ? at.getValue(CableBlock.COLOR) : DyeColor.ORANGE;
    }

    /** The face a cable at this position is mounted on, preferring the one the player pointed at. */
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

    /** [11] */
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

    /** [12] */
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        return cable().getStateForPlacement(context);
    }

    private static void say(Player player, String key, ChatFormatting style) {
        player.displayClientMessage(Component.translatable(key).withStyle(style), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        final String key = getDescriptionId();
        tooltip.add(Component.translatable(key + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.create_stats.cable.assist").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.create_stats.cable.colour").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.create_stats.wire_rate", cable().multiplier())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
