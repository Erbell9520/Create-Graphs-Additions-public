package com.createstats.display;

import com.createstats.blockentity.SafetyClutchBE;
import com.createstats.blockentity.SafetyClutchSettings;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class SafetyClutchDisplaySource extends DisplaySource implements StatsLinkSource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof SafetyClutchBE clutch)) {
            return EMPTY;
        }

        final List<MutableComponent> lines = new ArrayList<>();
        final int maxRows = Math.max(1, stats.maxRows());
        final SafetyClutchSettings settings = clutch.getSettings();
        final float stressPercent = clutch.getLastStressPercent();

        Component stateStr;
        if (!settings.isEnabled()) {
            stateStr = Component.translatable("gui.create_stats.safety_clutch.status.disabled");
        } else if (clutch.isTripped()) {
            stateStr = Component.translatable("gui.create_stats.safety_clutch.status.tripped");
        } else if (!clutch.isNetworkRunning()) {
            stateStr = Component.translatable("gui.create_stats.safety_clutch.status.idle");
        } else {
            stateStr = Component.translatable("gui.create_stats.safety_clutch.status.armed");
        }
        lines.add(Component.translatable("create_stats.safety_clutch.display.state", stateStr));

        if (maxRows >= 2) {
            lines.add(Component.translatable("create_stats.safety_clutch.display.live",
                    String.format("%.1f", stressPercent), String.format("%.0f", settings.getThreshold())));
        }

        if (maxRows >= 3) {
            final Component reason = !settings.isEnabled()
                    ? Component.translatable("create_stats.safety_clutch.display.reason.disabled")
                    : clutch.isTripped()
                    ? settings.getMode() == SafetyClutchSettings.ClutchMode.AUTO_RESET
                        ? Component.translatable("create_stats.safety_clutch.display.reason.retrying",
                            String.format("%.1f", clutch.getAutoResetRetrySeconds()))
                        : Component.translatable("create_stats.safety_clutch.display.reason.manual")
                    : clutch.isNetworkRunning()
                    ? Component.translatable("create_stats.safety_clutch.display.reason.armed")
                    : Component.translatable("create_stats.safety_clutch.display.reason.waiting");
            lines.add(Component.translatable("create_stats.safety_clutch.display.reason_line", reason));
        }

        if (maxRows >= 4) {
            lines.add(Component.translatable("create_stats.safety_clutch.display.su",
                    String.format("%.0f", clutch.getLastStressSU()), String.format("%.0f", clutch.getLastCapacitySU())));
        }

        if (maxRows >= 5) {
            lines.add(Component.translatable("create_stats.safety_clutch.display.mode",
                    settings.getMode().getDisplayName(),
                    Component.translatable(clutch.isNetworkRunning()
                        ? "create_stats.safety_clutch.display.network.live"
                        : "create_stats.safety_clutch.display.network.idle")));
        }

        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "safety_clutch";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}
