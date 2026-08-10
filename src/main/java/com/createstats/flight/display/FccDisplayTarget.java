package com.createstats.flight.display;

import com.createstats.flight.blockentity.FlightControlComputerBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Display target that lets a Gimbal / Altitude / Velocity / Nav Table sensor be
 * linked to the Flight Control Computer via the Display Link tool.
 */
public class FccDisplayTarget extends DisplayTarget {

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        FlightControlComputerBE fcc = resolveFcc(context);
        if (fcc == null) return;

        Level level = context.level();
        if (level == null) return;

        BlockPos sourcePos = context.getSourcePos();
        if (sourcePos == null) return;

        BlockEntity sourceBe = context.getSourceBlockEntity();
        if (sourceBe == null) sourceBe = level.getBlockEntity(sourcePos);
        if (sourceBe == null) return;

        if (SableBridge.matchesGimbalSensor(sourceBe)) {
            if (!sourcePos.equals(fcc.getGimbalPos())) fcc.setManualGimbalPos(sourcePos);
        } else if (SableBridge.matchesAltitudeSensor(sourceBe)) {
            if (!sourcePos.equals(fcc.getAltitudeSensorPos())) fcc.setManualAltitudePos(sourcePos);
        } else if (SableBridge.matchesVelocitySensor(sourceBe)) {
            if (!sourcePos.equals(fcc.getVelocitySensorPos())) fcc.setManualVelocityPos(sourcePos);
        } else if (SableBridge.matchesNavTable(sourceBe)) {
            if (!sourcePos.equals(fcc.getNavTablePos())) fcc.setManualNavTablePos(sourcePos);
        } else if (sourceBe instanceof PathfinderComputerBE computer) {
            fcc.ingestPathfinderGuidance(computer, sourcePos);
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        return new DisplayTargetStats(1, 32, this);
    }

    public void reset(DisplayLinkContext context) {
        // Display link broken or removed, no automatic clear of sensor binding.
        // The FCC will detect the sensor offline via SensorDiscovery hysteresis.
    }

    @Nullable
    private static FlightControlComputerBE resolveFcc(DisplayLinkContext ctx) {
        Level level = ctx.level();
        if (level == null) return null;
        BlockPos pos = ctx.getTargetPos();
        if (pos == null) return null;
        // Use the null/exception-safe chunk lookup to avoid crashes on unloaded chunks.
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        return be instanceof FlightControlComputerBE fcc ? fcc : null;
    }
}
