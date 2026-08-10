package com.createstats.display;

import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.DamageObserverBE;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BattleScreenDisplayTarget extends DisplayTarget {

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        BattleScreenDisplayBE display = resolveDisplay(context);
        if (display == null) {
            return;
        }
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof DamageObserverBE observer)) {
            return;
        }
        display.acceptLinkedObserver(observer, observer.getBlockPos());
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(5, 48, this);
    }

    public void reset(DisplayLinkContext context) {
        // Create can transiently call reset while a Display Link is still valid.
        // Let the battle screen's stale timeout own cleanup instead of dropping the observer immediately.
    }

    @Nullable
    private static BattleScreenDisplayBE resolveDisplay(DisplayLinkContext context) {
        BlockEntity target = context.getTargetBlockEntity();
        if (target instanceof BattleScreenDisplayBE display) {
            return display;
        }
        Level level = context.level();
        BlockPos targetPos = context.getTargetPos();
        if (level == null || targetPos == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(targetPos);
        return blockEntity instanceof BattleScreenDisplayBE display ? display : null;
    }
}