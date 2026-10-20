package com.createstats.netscan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.netscan.scan.ScanGraph;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

// without this create says "Not a Display Source" on every scanner
public class NetscanDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        final BlockEntity be = context.getSourceBlockEntity();
        if (!(be instanceof NetscanHost host)) {
            return EMPTY;
        }
        final int maxRows = Math.max(1, stats.maxRows());
        final List<MutableComponent> lines = new ArrayList<>();

        if (!host.scanPowered()) {
            lines.add(Component.translatable(host.scannerForm().isInline()
                    ? "create_stats.netscan.status.no_rotation"
                    : "create_stats.netscan.status.no_power"));
            return lines;
        }
        if (host.scanSeed() == null) {
            lines.add(Component.translatable("create_stats.netscan.status.not_clamped"));
            return lines;
        }

        final NetscanEngine engine = host.scanEngine();
        final ScanGraph graph = engine.graph();
        if (graph.isEmpty()) {
            lines.add(Component.translatable(engine.sweeping()
                    ? "create_stats.netscan.status.sweeping"
                    : "create_stats.netscan.status.no_network"));
            return lines;
        }

        add(lines, maxRows, Component.translatable("create_stats.netscan.display.elements",
                graph.size(), graph.sourceCount(), graph.machineCount()));
        add(lines, maxRows, Component.translatable("create_stats.netscan.display.stress",
                compact(graph.totalStress()), compact(graph.totalCapacity()),
                String.format(Locale.ROOT, "%.0f", graph.loadFraction() * 100.0f)));
        if (graph.isOverStressed()) {
            add(lines, maxRows, Component.translatable("create_stats.netscan.display.overstressed"));
        }
        if (graph.truncated()) {
            add(lines, maxRows, Component.translatable("create_stats.netscan.display.truncated",
                    host.scannerTier().elementBudget()));
        }
        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "topology_scanner";
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

    private static String compact(float value) {
        final float magnitude = Math.abs(value);
        if (magnitude >= 1.0e6f) {
            return String.format(Locale.ROOT, "%.1fM", value / 1.0e6f);
        }
        if (magnitude >= 1000.0f) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000.0f);
        }
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
