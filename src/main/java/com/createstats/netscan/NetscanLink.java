package com.createstats.netscan;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.netscan.scan.ScanGraph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class NetscanLink {
    public static final String SOURCE_TYPE = "Topology Scanner";

    private NetscanLink() {
    }

    public static List<BlockPos> scannersFor(StatsDisplayPanelBE panel) {
        final List<BlockPos> out = new ArrayList<>();
        if (panel == null || panel.getLevel() == null) {
            return out;
        }
        final Level level = panel.getLevel();
        for (final SourceConfig config : panel.getSourceRegistry().values()) {
            if (isScanner(level, config)) {
                out.add(config.getSourcePos().immutable());
            }
        }
        return out;
    }

    public static boolean isScanner(Level level, SourceConfig config) {
        final BlockPos pos = config == null ? null : config.getSourcePos();
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockEntity(pos) instanceof NetscanHost;
    }

    public static BlockPos scannerFor(StatsDisplayPanelBE panel) {
        final List<BlockPos> all = scannersFor(panel);
        return all.isEmpty() ? null : all.get(0);
    }

    public static NetscanHost hostFor(StatsDisplayPanelBE panel) {
        final BlockPos pos = scannerFor(panel);
        if (pos == null) {
            return null;
        }
        return panel.getLevel().getBlockEntity(pos) instanceof NetscanHost host ? host : null;
    }

    public static ScanGraph graphFor(StatsDisplayPanelBE panel) {
        final NetscanHost host = hostFor(panel);
        return host == null ? ScanGraph.EMPTY : host.scanEngine().graph();
    }
}
