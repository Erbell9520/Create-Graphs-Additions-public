package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.statsmk3.ui.Tokens;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Peers {
    private Peers() {
    }

    public static final class Peer {
        public final SourceProbe.Unit unit;
        public final String sourceId;

        public final String variant;
        // keyed per variant or two stores in one machine draw on top of each other
        public final String key;

        public final int index;

        public final String numeral;

        public final String bearing;

        public final int series;

        public float urgency;

        Peer(SourceProbe.Unit unit, int index, String bearing) {
            this(unit, index, bearing, "");
        }

        Peer(SourceProbe.Unit unit, int index, String bearing, String variant) {
            this.unit = unit;
            this.sourceId = unit.config() == null ? "" : unit.config().getSourceId();
            this.variant = variant == null ? "" : variant;
            this.key = this.variant.isEmpty() ? this.sourceId : this.sourceId + "#" + this.variant;
            this.index = index;
            this.numeral = "N" + (index + 1);
            this.bearing = bearing;

            this.series = Tokens.seriesFor(this.key);
        }

        public String label(boolean ambiguous) {
            return SourceNames.label(this.unit.config(), true, ambiguous);
        }

        public boolean named() {
            return SourceNames.named(this.unit.config());
        }

        public BlockPos pos() {
            return this.unit.config() == null ? null : this.unit.config().getSourcePos();
        }
    }

    public static List<Peer> of(StatsDisplayPanelBE panel, String... families) {
        final List<Peer> peers = new ArrayList<>();
        if (panel == null) {
            return peers;
        }
        final BlockPos here = panel.getBlockPos();
        int i = 0;
        for (final SourceProbe.Unit unit : SourceProbe.byFamily(panel, families)) {
            peers.add(new Peer(unit, i++, bearing(here, unit.config() == null
                    ? null : unit.config().getSourcePos())));
        }
        return peers;
    }

    public static List<Peer> matching(StatsDisplayPanelBE panel, Predicate<SourceConfig> filter) {
        final List<Peer> peers = new ArrayList<>();
        if (panel == null || filter == null) {
            return peers;
        }
        final BlockPos here = panel.getBlockPos();
        int i = 0;
        for (final SourceProbe.Unit unit : SourceProbe.all(panel)) {
            if (!filter.test(unit.config())) {
                continue;
            }
            peers.add(new Peer(unit, i++, bearing(here, unit.config() == null
                    ? null : unit.config().getSourcePos())));
        }
        return peers;
    }

    public static String bearing(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return "";
        }
        final int dx = to.getX() - from.getX();
        final int dy = to.getY() - from.getY();
        final int dz = to.getZ() - from.getZ();
        final int flat = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        final StringBuilder sb = new StringBuilder();
        sb.append(flat).append("m");
        final String compass = compass(dx, dz);
        if (!compass.isEmpty()) {
            sb.append(' ').append(compass);
        }
        if (dy != 0) {
            sb.append(", y").append(dy > 0 ? "+" : "").append(dy);
        }
        return sb.toString();
    }

    private static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "";
        }
        final boolean north = dz < 0;
        final boolean east = dx > 0;
        final int ax = Math.abs(dx);
        final int az = Math.abs(dz);

        if (ax > az * 2) {
            return east ? "E" : "W";
        }
        if (az > ax * 2) {
            return north ? "N" : "S";
        }
        return (north ? "N" : "S") + (east ? "E" : "W");
    }

    public static List<int[]> twins(List<Peer> peers, String... sharedMetricIds) {
        final List<int[]> groups = new ArrayList<>();
        if (peers == null || peers.size() < 2 || sharedMetricIds == null || sharedMetricIds.length == 0) {
            return groups;
        }
        final boolean[] taken = new boolean[peers.size()];
        for (int i = 0; i < peers.size(); i++) {
            if (taken[i]) {
                continue;
            }
            final List<Integer> group = new ArrayList<>();
            group.add(i);
            for (int j = i + 1; j < peers.size(); j++) {
                if (!taken[j] && sameSystem(peers.get(i), peers.get(j), sharedMetricIds)) {
                    taken[j] = true;
                    group.add(j);
                }
            }
            if (group.size() > 1) {
                taken[i] = true;
                final int[] out = new int[group.size()];
                for (int k = 0; k < out.length; k++) {
                    out[k] = group.get(k);
                }
                groups.add(out);
            }
        }
        return groups;
    }

    private static boolean sameSystem(Peer a, Peer b, String[] metricIds) {
        for (final String id : metricIds) {
            if (!a.unit.has(id) || !b.unit.has(id)) {
                return false;
            }
            final float va = a.unit.value(id, Float.NaN);
            final float vb = b.unit.value(id, Float.NaN);
            if (Float.isNaN(va) || Float.isNaN(vb)) {
                return false;
            }
            final float scale = Math.max(1.0f, Math.max(Math.abs(va), Math.abs(vb)));
            if (Math.abs(va - vb) > scale * 0.001f) {
                return false;
            }
        }
        return true;
    }

    public static int groupOf(List<int[]> groups, int peerIndex) {
        for (int g = 0; g < groups.size(); g++) {
            for (final int member : groups.get(g)) {
                if (member == peerIndex) {
                    return g;
                }
            }
        }
        return -1;
    }

    public static String describe(List<Peer> peers, int[] group) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < group.length; i++) {
            if (i > 0) {
                sb.append(i == group.length - 1 ? " and " : ", ");
            }
            sb.append(peers.get(group[i]).numeral);
        }
        return sb.toString();
    }

    public static String count(int n, String singular) {
        return n + " " + (n == 1 ? singular : singular + "s");
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
