package com.createstats.display;

import com.createstats.surveyor.PathwayStore;
import com.createstats.surveyor.PathwayTraffic;
import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RouteRunner;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PathwayStationDisplaySource extends DisplaySource {
    public static final double REACH = 8.0;

    public record Station(Pathway pathway, PathNode node) {
    }

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.level() instanceof ServerLevel level)) return EMPTY;
        Station st = near(level, context.getSourcePos());
        if (st == null) return List.of(Component.translatable("create_stats.display_source.pathway_station.none"));
        List<MutableComponent> lines = new ArrayList<>();
        lines.add(Component.literal(st.node().name()));
        List<String[]> rows = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (RouteTrack t : PathwayTraffic.on(level, st.pathway().id())) {
            String when = when(t, st.node().id());
            if (when == null) continue;
            rows.add(new String[]{when, t.name(), st.pathway().name()});
            order.add(sortKey(t, st.node().id()));
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) idx.add(i);
        idx.sort(Comparator.comparingInt(order::get));
        int room = Math.max(1, stats.maxRows() - 1);
        for (int k = 0; k < idx.size() && k < room; k++) {
            String[] r = rows.get(idx.get(k));
            lines.add(Component.literal(r[0] + "  " + r[1] + "  " + r[2]));
        }
        if (rows.isEmpty() && stats.maxRows() > 1) lines.add(Component.translatable("create_stats.display_source.pathway_station.nothing"));
        return lines;
    }

    @Nullable
    static String when(RouteTrack t, int node) {
        boolean here = t.nextStop() == node;
        if (!here) return null;
        if (t.state() == RouteRunner.State.WAITING.ordinal()) return "now";
        float eta = t.eta();
        return clock(eta);
    }

    static String clock(double seconds) {
        if (!(seconds >= 0.0) || Double.isInfinite(seconds)) return "~";
        if (seconds < 10.0) return "now";
        if (seconds < 60.0) return (int) (Math.ceil(seconds / 15.0) * 15) + " s";
        return Math.round(seconds / 60.0) + " min";
    }

    private static int sortKey(RouteTrack t, int node) {
        if (t.state() == RouteRunner.State.WAITING.ordinal()) return -1;
        return t.eta() >= 0 ? Math.round(t.eta()) : Integer.MAX_VALUE;
    }

    @Nullable
    public static Station near(ServerLevel level, BlockPos at) {
        Station best = null;
        double bestD = REACH * REACH;
        for (Pathway p : PathwayStore.get(level).all()) {
            for (PathNode n : p.nodes()) {
                if (n.name().isEmpty() || !n.isStop()) continue;
                double dx = n.x() - (at.getX() + 0.5);
                double dy = n.y() - (at.getY() + 0.5);
                double dz = n.z() - (at.getZ() + 0.5);
                double d = dx * dx + dy * dy * 0.25 + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = new Station(p, n);
                }
            }
        }
        return best;
    }

    @Override
    protected String getTranslationKey() {
        return "pathway_station";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
