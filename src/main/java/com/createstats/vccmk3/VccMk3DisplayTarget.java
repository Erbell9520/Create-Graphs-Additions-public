package com.createstats.vccmk3;

import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VccMk3DisplayTarget extends DisplayTarget {
    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        VccMk3BlockEntity controller = resolveController(context);
        if (controller == null) return;

        Level level = context.level();
        BlockPos sourcePos = context.getSourcePos();
        if (level == null || sourcePos == null) return;

        BlockEntity source = context.getSourceBlockEntity();
        if (source == null) source = level.getBlockEntity(sourcePos);
        if (source == null) return;

        if (SableBridge.matchesGimbalSensor(source)) {
            controller.bindSensor("gimbal", sourcePos);
        } else if (SableBridge.matchesAltitudeSensor(source)) {
            controller.bindSensor("altitude", sourcePos);
        } else if (SableBridge.matchesVelocitySensor(source)) {
            controller.bindSensor("velocity", sourcePos);
        } else if (SableBridge.matchesOpticalSensor(source)) {
            controller.bindSensor("optical", sourcePos);
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(1, 32, this);
    }

    @Nullable
    private static VccMk3BlockEntity resolveController(DisplayLinkContext context) {
        BlockEntity be = context.getTargetBlockEntity();
        return be instanceof VccMk3BlockEntity controller ? controller : null;
    }
}
