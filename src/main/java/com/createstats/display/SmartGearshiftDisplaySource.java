package com.createstats.display;

import com.createstats.blockentity.SmartGearshiftBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class SmartGearshiftDisplaySource extends DisplaySource implements StatsLinkSource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof SmartGearshiftBE gearshift)) {
            return EMPTY;
        }

        List<MutableComponent> lines = new ArrayList<>();
        int maxRows = Math.max(1, stats.maxRows());

        Component state;
        if (gearshift.getSourceSpeed() <= 0.0f) {
            state = Component.translatable("create_stats.smart_gearshift.display.state.no_input");
        } else if (gearshift.isBoosting()) {
            state = Component.translatable("create_stats.smart_gearshift.display.state.boosting");
        } else if (gearshift.isThrottling()) {
            state = Component.translatable("create_stats.smart_gearshift.display.state.throttling");
        } else {
            state = Component.translatable("create_stats.smart_gearshift.display.state.locked");
        }
        lines.add(Component.translatable("create_stats.smart_gearshift.display.line", state));

        if (maxRows >= 2) {
            lines.add(Component.translatable("create_stats.smart_gearshift.display.stage",
                    gearshift.getCurrentStage(),
                    gearshift.getTargetStage(),
                    SmartGearshiftBE.getTargetRPMForStage(gearshift.getTargetStage())));
        }

        if (maxRows >= 3) {
            lines.add(Component.translatable("create_stats.smart_gearshift.display.io",
                    String.format("%.0f", gearshift.getSourceSpeed()),
                    String.format("%.0f", gearshift.getTransmittedSpeed())));
        }

        if (maxRows >= 4) {
            lines.add(Component.translatable("create_stats.smart_gearshift.display.modifier",
                    String.format("%.2f", gearshift.getCurrentModifier()),
                    Component.translatable(gearshift.isBoosting() ? "create_stats.smart_gearshift.display.mode.boost"
                            : gearshift.isThrottling() ? "create_stats.smart_gearshift.display.mode.throttle"
                            : "create_stats.smart_gearshift.display.mode.neutral")));
        }

        if (maxRows >= 5) {
            lines.add(Component.translatable("create_stats.smart_gearshift.display.footer"));
        }

        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "stats_link";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 5;
    }
}
