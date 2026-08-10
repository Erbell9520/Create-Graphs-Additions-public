package com.createstats.pathfinder.display;

import com.createstats.pathfinder.blockentity.PathfinderComputerBE;
import com.createstats.pathfinder.model.RouteGuidance;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PathfinderComputerDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof PathfinderComputerBE computer)) {
            return EMPTY;
        }
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        RouteGuidance guidance = computer.activeGuidance();
        if (guidance == null || guidance.waypoints().size() < 2 || guidance.confidence() <= 0f) {
            add(lines, maxRows, Component.translatable("create_stats.pathfinder.display.no_route", computer.plannerStatus()));
            add(lines, maxRows, Component.translatable("create_stats.pathfinder.display.waiting"));
            return lines;
        }
        add(lines, maxRows, Component.translatable("create_stats.pathfinder.display.telemetry",
            shortId(guidance.routeId()), String.format(Locale.ROOT, "%.2f", guidance.confidence()), guidance.waypoints().size(),
            String.format(Locale.ROOT, "%.1f", guidance.estimatedDistance()), guidance.hazardFlags()));
        add(lines, maxRows, Component.translatable("create_stats.pathfinder.display.route",
            guidance.routeName(), guidance.waypoints().size(), String.format(Locale.ROOT, "%.0f", guidance.routeCost())));
        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "pathfinder_computer";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    private static void add(List<MutableComponent> lines, int maxRows, MutableComponent text) {
        if (lines.size() < maxRows) {
            lines.add(text);
        }
    }

    private static String shortId(java.util.UUID id) {
        return id == null ? "unknown" : id.toString().substring(0, 8);
    }
}