package com.createstats.display;

import com.createstats.blockentity.ControlClutchBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class ControlClutchDisplaySource extends DisplaySource implements StatsLinkSource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof ControlClutchBE clutch)) {
            return EMPTY;
        }

        List<MutableComponent> lines = new ArrayList<>();
        int maxRows = Math.max(1, stats.maxRows());

        Component state;
        if (!clutch.isControllerLinked()) {
            state = Component.translatable("create_stats.control_clutch.display.state.unlinked");
        } else if (clutch.isControllerRequestingRun()) {
            state = Component.translatable("create_stats.control_clutch.display.state.run");
        } else {
            state = Component.translatable("create_stats.control_clutch.display.state.stop");
        }
        lines.add(Component.translatable("create_stats.control_clutch.display.line", state));

        if (maxRows >= 2) {
            BlockPos controllerPos = clutch.getControllerPos();
            if (controllerPos == null) {
                lines.add(Component.translatable("create_stats.control_clutch.display.no_controller"));
            } else {
                Component rowLabel = clutch.getControllerRow() >= 0
                        ? Component.translatable("create_stats.control_clutch.display.row_label", clutch.getControllerRow() + 1)
                        : Component.empty();
                lines.add(Component.translatable("create_stats.control_clutch.display.controller",
                        controllerPos.getX(),
                        controllerPos.getY(),
                        controllerPos.getZ(),
                        rowLabel,
                        clutch.getLastControllerSignal()));
            }
        }

        if (maxRows >= 3) {
            lines.add(Component.translatable("create_stats.control_clutch.display.rpm",
                    String.format("%.0f", clutch.getSourceSpeed()),
                    String.format("%.0f", clutch.getTransmittedSpeed())));
        }

        if (maxRows >= 4) {
            Component network = clutch.isNetworkRunning()
                    ? Component.translatable("create_stats.control_clutch.display.network.live")
                    : clutch.hasKineticNetwork()
                        ? Component.translatable("create_stats.control_clutch.display.network.connected")
                        : Component.translatable("create_stats.control_clutch.display.network.idle");
            Component command = Component.translatable(clutch.isOutputRunning()
                    ? "create_stats.control_clutch.display.command.run"
                    : "create_stats.control_clutch.display.command.stop");
            lines.add(Component.translatable("create_stats.control_clutch.display.network_line", network, command));
        }

        if (maxRows >= 5) {
            lines.add(Component.translatable("create_stats.control_clutch.display.footer"));
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
