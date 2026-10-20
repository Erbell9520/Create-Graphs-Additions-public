package com.createstats.display;

import com.createstats.blockentity.PowerMeterBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PowerMeterDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof PowerMeterBE meter)) {
            return EMPTY;
        }
        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());

        final PowerMeterBE.Status state = meter.status();
        if (state == PowerMeterBE.Status.LOOPED || state == PowerMeterBE.Status.NO_SUPPLY
                || state == PowerMeterBE.Status.NO_LOAD) {
            lines.add(Component.translatable(PowerMeterBE.faultKey(state)));
            return lines;
        }

        lines.add(Component.translatable("create_stats.power_meter.line.rate",
                String.format(Locale.ROOT, "%,d", meter.getRateFePerTick())));
        if (lines.size() < maxRows) {
            final Component load = meter.getLoadName();
            lines.add(Component.translatable("create_stats.power_meter.line.feeding",
                    load == null ? Component.translatable("create_stats.power_meter.line.circuit") : load));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.power_meter.line.peak",
                    String.format(Locale.ROOT, "%,d", meter.getPeakFePerTick())));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.power_meter.line.total",
                    String.format(Locale.ROOT, "%,d", meter.getTotalFe())));
        }
        return lines;
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
        return "power_meter";
    }
}
