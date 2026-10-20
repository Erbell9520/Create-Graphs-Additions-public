package com.createstats.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.createstats.CreateStats;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.ground.DriveMath;
import com.createstats.ground.DriveReadout;
import com.createstats.ground.GroundAlerts;
import com.createstats.ground.GroundSnapshot;
import com.createstats.ground.GroundTelemetrySource;
import com.createstats.ground.GroundText;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class GroundDisplaySource extends DisplaySource implements StatsLinkSource {
    private final Map<BlockEntity, List<GroundAlerts.Code>> held = new WeakHashMap<>();

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.getTargetBlockEntity() instanceof StatsDisplayPanelBE) {
            return EMPTY;
        }
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof GroundTelemetrySource source)) {
            return EMPTY;
        }
        final GroundSnapshot snapshot;
        try {
            snapshot = source.groundSnapshot();
        } catch (RuntimeException e) {
            CreateStats.LOGGER.debug("[Ground] snapshot failed at {}: {}", context.getSourcePos(), e.toString());
            return EMPTY;
        }
        if (snapshot == null) {
            return List.of(Component.literal("Vehicle: no report yet"));
        }
        final DriveReadout r = DriveMath.read(snapshot);
        final List<GroundAlerts.Alert> alerts;
        synchronized (this.held) {
            alerts = GroundAlerts.evaluate(r, this.held.getOrDefault(be, List.of()), 0.0);
            this.held.put(be, GroundAlerts.codes(alerts));
        }
        final List<MutableComponent> out = new ArrayList<>();
        for (final String line : GroundText.lines(r, alerts, Math.max(1, stats.maxRows()))) {
            out.add(Component.literal(line));
        }
        return out;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "vcc_ground";
    }
}
