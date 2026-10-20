package com.createstats.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AerialTelemetrySource;
import com.createstats.aerial.AerialText;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.CreateStats;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class AerialDisplaySource extends DisplaySource implements StatsLinkSource {
    private final Map<BlockEntity, List<AerialAlerts.Code>> held = new WeakHashMap<>();

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.getTargetBlockEntity() instanceof StatsDisplayPanelBE) {
            return EMPTY;
        }
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof AerialTelemetrySource source)) {
            return EMPTY;
        }
        final AerialSnapshot snapshot;
        try {
            snapshot = source.aerialSnapshot();
        } catch (RuntimeException e) {
            // debug only, a board would repeat it every second

            CreateStats.LOGGER.debug("[Aerial] snapshot failed at {}: {}", context.getSourcePos(), e.toString());
            return EMPTY;
        }
        if (snapshot == null) {
            return List.of(Component.literal("Aerial: no report yet"));
        }
        final AirframeReadout r = AirframeMath.read(snapshot);
        final List<AerialAlerts.Alert> alerts;
        synchronized (this.held) {
            alerts = AerialAlerts.evaluate(r, this.held.getOrDefault(be, List.of()), 0.0);
            this.held.put(be, AerialAlerts.codes(alerts));
        }
        final List<MutableComponent> out = new ArrayList<>();
        for (final String line : AerialText.lines(r, alerts, Math.max(1, stats.maxRows()))) {
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
        return "vcc_aerial";
    }
}
