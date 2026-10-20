package com.createstats.pathfinder.display;

import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PathScannerDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof PathScannerBE scanner)) {
            return EMPTY;
        }
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        add(lines, maxRows, Component.translatable("create_stats.path_scanner.display.telemetry",
            shortId(scanner.scannerId()), scanner.lastScanTick(), scanner.expectedChunkCount(), scanner.loadedChunkCount(),
            scanner.unknownChunkCount(), scanner.totalPassableCells(), scanner.totalMarginalCells(),
            scanner.totalBlockedCells(), scanner.queueDepth()));
        add(lines, maxRows, Component.translatable("create_stats.path_scanner.display.summary",
            shortId(scanner.scannerId()), scanner.loadedChunkCount(), Math.max(1, scanner.expectedChunkCount()),
            scanner.unknownChunkCount()));
        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "path_scanner";
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
