package com.createstats.display;

import com.createstats.blockentity.AutoTransmissionClutchBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class AutoTransmissionClutchDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof AutoTransmissionClutchBE transmission)) {
            return EMPTY;
        }

        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());

        Component stateStr;
        if (!transmission.getSettings().isEnabled()) {
            stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.disabled");
        } else {
            switch (transmission.getAlarmState()) {
                case CONCERNING:
                    stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.min_rpm");
                    break;
                case VERY_CONCERNING:
                    stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.emergency");
                    break;
                default:
                    if (!transmission.isNetworkRunning()) {
                        stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.idle");
                    } else if (transmission.isBoosting()) {
                        stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.boosting");
                    } else if (transmission.isActive()) {
                        stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.throttling");
                    } else {
                        stateStr = Component.translatable("create_stats.auto_transmission_clutch.display.state.pass_through");
                    }
            }
        }
        lines.add(Component.translatable("create_stats.auto_transmission_clutch.display.line", stateStr));

        if (maxRows >= 2) {
            lines.add(Component.translatable("create_stats.auto_transmission_clutch.display.io",
                    String.format("%.0f", transmission.getSourceSpeed()),
                    String.format("%.0f", transmission.getTransmittedSpeed()),
                    String.format("%.0f", transmission.getCurrentModifier() * 100.0f)));
        }

        if (maxRows >= 3) {
            String modeStr = transmission.getSettings().getLimitMode().getDisplayName();
            float lower = transmission.getSettings().getLowerBound();
            float upper = transmission.getSettings().getUpperBound();
            lines.add(Component.translatable("create_stats.auto_transmission_clutch.display.band",
                    String.format("%.0f", lower), String.format("%.0f", upper), modeStr,
                    String.format("%.0f", transmission.getSettings().getMinimumRPM()),
                    String.format("%.0f", transmission.getSettings().getMaximumRPM())));
        }

        if (maxRows >= 4) {
            lines.add(Component.translatable("create_stats.auto_transmission_clutch.display.footer"));
        }

        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "stats_link";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
