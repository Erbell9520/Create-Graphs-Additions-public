package com.createstats.display;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ProductionDetectorDisplayTarget extends DisplayTarget {
    public static final int MAX_LINES = ProductionDetectorBE.MAX_DISPLAY_LINK_LINES;

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        final ProductionDetectorBE be = resolveLowerBE(context);
        if (be == null) {
            return;
        }
        final DisplayLinkBlockEntity link = context.blockEntity();
        if (link != null && link.getLevel() != null) {
            be.acceptFeeder(link.getLevel().dimension(), link.getBlockPos());
        }
        be.acceptLinkText(line, text);
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(MAX_LINES, ProductionDetectorBE.LINK_LINE_WIDTH, this);
    }

    @Override
    public AABB getMultiblockBounds(LevelAccessor level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        final BlockPos lower = isUpper(state) ? pos.below() : pos;
        return new AABB(lower).expandTowards(0.0, 1.0, 0.0);
    }

    private static boolean isUpper(BlockState state) {
        return state.is(ModBlocks.PRODUCTION_DETECTOR.get())
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    @Nullable
    private static ProductionDetectorBE resolveLowerBE(DisplayLinkContext ctx) {
        final Level level = ctx instanceof CrossDimDisplayLinkContext cross ? cross.targetLevel() : ctx.level();
        if (level == null) {
            return null;
        }
        BlockPos pos = ctx.getTargetPos();
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        if (isUpper(level.getBlockState(pos))) {
            pos = pos.below();
        }
        return level.getBlockEntity(pos) instanceof ProductionDetectorBE detector ? detector : null;
    }
}
