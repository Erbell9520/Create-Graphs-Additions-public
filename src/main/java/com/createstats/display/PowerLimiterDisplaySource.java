package com.createstats.display;

import com.createstats.blockentity.PowerLimiterBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PowerLimiterDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof PowerLimiterBE limiter)) {
            return EMPTY;
        }
        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());

        final PowerLimiterBE.Status state = limiter.status();
        if (state == PowerLimiterBE.Status.LOOPED || state == PowerLimiterBE.Status.NO_SUPPLY
                || state == PowerLimiterBE.Status.NO_LOAD) {
            lines.add(Component.translatable(PowerLimiterBE.statusKey(state)));
            return lines;
        }

        lines.add(Component.translatable("create_stats.power_limiter.line.rate",
                String.format(Locale.ROOT, "%,d", limiter.getRateFePerTick())));
        if (lines.size() < maxRows) {
            lines.add(Component.translatable("create_stats.power_limiter.line.cap",
                    String.format(Locale.ROOT, "%,d", limiter.effectiveLimitFePerTick())));
        }
        if (lines.size() < maxRows) {
            lines.add(Component.translatable(PowerLimiterBE.statusKey(state)));
        }
        if (lines.size() < maxRows) {
            final Component load = limiter.getLoadName();
            lines.add(Component.translatable("create_stats.power_limiter.line.feeding",
                    load == null ? Component.translatable("create_stats.power_limiter.line.circuit") : load));
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
        return "power_limiter";
    }
}
