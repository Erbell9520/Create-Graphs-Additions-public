package com.createstats.pathfinder.display;

import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class PathfinderDisplayTarget extends DisplayTarget {
    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        PathfinderComputerBE computer = resolveComputer(context);
        if (computer == null) return;
        BlockEntity source = context.getSourceBlockEntity();
        BlockPos sourcePos = context.getSourcePos();
        if (source instanceof PathScannerBE scanner && sourcePos != null) {
            computer.ingestScannerSummary(scanner, sourcePos);
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(2, 128, this);
    }

    private static PathfinderComputerBE resolveComputer(DisplayLinkContext context) {
        Level level = context.level();
        if (level == null) return null;
        BlockPos targetPos = context.getTargetPos();
        if (targetPos == null) return null;
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        return blockEntity instanceof PathfinderComputerBE computer ? computer : null;
    }
}
