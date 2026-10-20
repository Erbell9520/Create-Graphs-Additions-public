package com.createstats.netscan;

import java.util.List;
import java.util.Locale;

import com.createstats.netscan.scan.ScanGraph;
import com.createstats.power.FeGoggle;

import net.minecraft.network.chat.Component;

public final class NetscanGoggle {
    private NetscanGoggle() {
    }

    public static boolean append(List<Component> tooltip, NetscanHost host) {
        if (host == null) {
            return false;
        }
        final ScannerTier tier = host.scannerTier();
        tooltip.add(FeGoggle.title("Topology Scanner"));

        if (!host.scanPowered()) {
            tooltip.add(FeGoggle.warn(host.scannerForm().isInline()
                    ? "No rotation on the shaft" : "No power"));
            return true;
        }
        if (host.scanSeed() == null) {
            tooltip.add(FeGoggle.warn("Not clamped to a kinetic block"));
            return true;
        }

        final NetscanEngine engine = host.scanEngine();
        final ScanGraph graph = engine.graph();
        if (engine.sweeping()) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Sweeping: %d of %d elements",
                    engine.sweepCount(), tier.elementBudget())));
            return true;
        }
        if (graph.isEmpty()) {
            tooltip.add(FeGoggle.dim("No survey yet"));
            return true;
        }

        tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "%d elements, %d sources",
                graph.size(), graph.sourceCount())));
        if (graph.truncated() && graph.sourceCount() == 0) {
            tooltip.add(FeGoggle.warn("No source reached: capacity is incomplete"));
        }
        final float load = graph.loadFraction();
        final String stress = String.format(Locale.ROOT, "Stress %.0f of %.0f su (%.0f%%)",
                graph.totalStress(), graph.totalCapacity(), load * 100.0f);
        tooltip.add(graph.isOverStressed() || load >= 0.85f ? FeGoggle.bad(stress)
                : load >= 0.7f ? FeGoggle.warn(stress) : FeGoggle.ok(stress));
        if (graph.truncated()) {
            tooltip.add(FeGoggle.warn("Survey cut off at this tier's " + tier.elementBudget()
                    + " element limit"));
        }
        return true;
    }
}
