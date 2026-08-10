package com.createstats.vccmk2;

import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** [1] */
public class VccMk2DisplayTarget extends DisplayTarget {

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        VccMk2BlockEntity vcc = resolve(context);
        if (vcc == null) {
            return;
        }
        Level level = context.level();
        if (level == null) {
            return;
        }
        BlockPos sourcePos = context.getSourcePos();
        if (sourcePos == null) {
            return;
        }
        BlockEntity sourceBe = context.getSourceBlockEntity();
        if (sourceBe == null) {
            sourceBe = level.getBlockEntity(sourcePos);
        }
        if (sourceBe == null) {
            return;
        }

        if (SableBridge.matchesGimbalSensor(sourceBe)) {
            if (!sourcePos.equals(vcc.sensorManualPos(VccMk2BlockEntity.SENSOR_GIMBAL))) {
                vcc.setManualGimbalPos(sourcePos.immutable());
            }
        } else if (SableBridge.matchesAltitudeSensor(sourceBe)) {
            if (!sourcePos.equals(vcc.sensorManualPos(VccMk2BlockEntity.SENSOR_ALTITUDE))) {
                vcc.setManualAltitudePos(sourcePos.immutable());
            }
        } else if (SableBridge.matchesVelocitySensor(sourceBe)) {
            if (!sourcePos.equals(vcc.sensorManualPos(VccMk2BlockEntity.SENSOR_VELOCITY))) {
                vcc.setManualVelocityPos(sourcePos.immutable());
            }
        } else if (SableBridge.matchesNavTable(sourceBe)) {
            if (!sourcePos.equals(vcc.sensorManualPos(VccMk2BlockEntity.SENSOR_NAVTABLE))) {
                vcc.setManualNavTablePos(sourcePos.immutable());
            }
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(1, 32, this);
    }

    public void reset(DisplayLinkContext context) {
        // [2]
    }

    @Nullable
    private static VccMk2BlockEntity resolve(DisplayLinkContext ctx) {
        Level level = ctx.level();
        if (level == null) {
            return null;
        }
        BlockPos pos = ctx.getTargetPos();
        if (pos == null) {
            return null;
        }
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        return be instanceof VccMk2BlockEntity vcc ? vcc : null;
    }
}
