package com.createstats.support;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class SupportTestRig {
    public static final String RIG_KINETIC = "kinetic";
    public static final String RIG_CLEAR = "clear";

    private SupportTestRig() {
    }

    public static void place(ServerPlayer player, SupportPolicy.Decision decision, String rig) {
        switch (rig) {
            case RIG_CLEAR -> clear(player, decision);
            case RIG_KINETIC -> kinetic(player, decision);
            default -> player.sendSystemMessage(
                Component.literal("Unknown rig: " + rig).withStyle(ChatFormatting.RED));
        }
    }

    private static void kinetic(ServerPlayer player, SupportPolicy.Decision decision) {
        ServerLevel level = player.serverLevel();
        Direction facing = player.getDirection();
        BlockPos origin = player.blockPosition().relative(facing, 2);

        SupportSessions.tier2(player, decision, "rig", RIG_KINETIC, origin.toShortString());

        List<String> placed = new ArrayList<>();
        Direction axis = facing.getClockWise();

        if (setKinetic(player, level, origin, "create:creative_motor", axis.getOpposite())) {
            placed.add("creative_motor");
        }
        for (int i = 1; i <= 3; i++) {
            if (setKinetic(player, level, origin.relative(axis, i), "create:shaft", axis)) {
                placed.add("shaft");
            }
        }
        if (setKinetic(player, level, origin.relative(axis, 4), "create:stressometer", axis)) {
            placed.add("stressometer");
        }

        if (setPlain(player, level, origin.relative(axis, 2).above(), "create_stats:stats_display_panel")) {
            placed.add("stats_display_panel");
        }

        if (placed.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                "Rig failed: none of the expected blocks are registered on this server.")
                .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.literal(
            "Placed test rig at " + origin.toShortString() + ": " + String.join(", ", placed))
            .withStyle(ChatFormatting.GREEN));
    }

    private static void clear(ServerPlayer player, SupportPolicy.Decision decision) {
        SupportSessions.tier2(player, decision, "rig", RIG_CLEAR);
        int removed = SupportSessions.clearRig(player);
        player.sendSystemMessage(Component.literal(removed == 0
                ? "No rig blocks from this session are left to clear."
                : "Cleared " + removed + " rig blocks.")
            .withStyle(ChatFormatting.GREEN));
    }

    private static boolean setKinetic(ServerPlayer player, ServerLevel level, BlockPos pos, String blockId,
                                      Direction facing) {
        BlockState state = resolve(blockId);
        if (state == null) {
            return false;
        }

        for (var property : state.getProperties()) {
            if (property == BlockStateProperties.FACING || property == BlockStateProperties.HORIZONTAL_FACING) {
                state = state.setValue((DirectionProperty) property, facing);
            } else if (property == BlockStateProperties.AXIS) {
                state = state.setValue((EnumProperty<Direction.Axis>) property, facing.getAxis());
            }
        }
        return setPlain(player, level, pos, state);
    }

    private static boolean setPlain(ServerPlayer player, ServerLevel level, BlockPos pos, String blockId) {
        BlockState state = resolve(blockId);
        return state != null && setPlain(player, level, pos, state);
    }

    private static boolean setPlain(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        BlockState existing = level.getBlockState(pos);
        if (!existing.isAir() && !existing.canBeReplaced()) {
            return false;
        }
        level.setBlock(pos, state, 3);
        SupportSessions.noteRigBlock(player, level, pos, state.getBlock());
        return true;
    }

    private static BlockState resolve(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }
}
