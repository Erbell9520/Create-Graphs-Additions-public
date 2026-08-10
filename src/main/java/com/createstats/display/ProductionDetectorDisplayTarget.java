package com.createstats.display;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.registry.ModBlocks;
import com.createstats.util.InstrumentSourceHelper;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

public class ProductionDetectorDisplayTarget extends DisplayTarget {
    public static final int MAX_LINES = ProductionDetectorBE.MAX_DISPLAY_LINK_LINES;

    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        final ProductionDetectorBE be = resolveLowerBE(context);
        if (be == null) return;
        final BlockEntity sourceBE = context.getSourceBlockEntity();
        if (sourceBE != null) {
            final InstrumentSourceHelper.SourceReadout readout = InstrumentSourceHelper.readSource(sourceBE, null);
            if (readout.sourceKind() == InstrumentSourceHelper.SourceKind.ENERGY_STORAGE && !readout.metrics().isEmpty()) {
                be.acceptEnergyReadout(context.getSourcePos(), readout);
                return;
            }
        }
        if (text == null || text.isEmpty()) return;
        be.beginDisplayLinkBatch();
        for (int i = 0; i < text.size(); ++i) {
            final int row = line + i;
            if (row >= MAX_LINES) break;
            be.acceptDisplayLinkLine(row, text.get(i));
        }
        be.endDisplayLinkBatch();
    }

    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(MAX_LINES, 32, this);
    }

    public void reset(DisplayLinkContext context) {
        final ProductionDetectorBE be = resolveLowerBE(context);
        if (be != null) be.clearDisplayLinkLines();
    }

    @Nullable
    private static ProductionDetectorBE resolveLowerBE(DisplayLinkContext ctx) {
        final Level level = ctx.level();
        if (level == null) return null;
        BlockPos pos = ctx.getTargetPos();
        if (pos == null) return null;
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.PRODUCTION_DETECTOR.get())
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
        }
        final BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ProductionDetectorBE pdbe ? pdbe : null;
    }
}
