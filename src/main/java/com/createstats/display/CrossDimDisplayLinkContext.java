package com.createstats.display;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CrossDimDisplayLinkContext extends DisplayLinkContext {
    private final ServerLevel targetLevel;
    private final BlockPos targetPos;

    public CrossDimDisplayLinkContext(ServerLevel sourceLevel, DisplayLinkBlockEntity blockEntity, ServerLevel targetLevel, BlockPos targetPos) {
        super(sourceLevel, blockEntity);
        this.targetLevel = targetLevel;
        this.targetPos = targetPos;
    }

    @Override
    public BlockEntity getTargetBlockEntity() {
        return this.targetLevel.getBlockEntity(this.targetPos);
    }

    @Override
    public BlockPos getTargetPos() {
        return this.targetPos;
    }

    public ServerLevel targetLevel() {
        return this.targetLevel;
    }
}