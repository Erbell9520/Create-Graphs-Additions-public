package com.createstats.client.ponder.seismic;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicTrace;
import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.ShellScan;
import com.createstats.seismic.SurveyShells;
import com.createstats.seismic.VeinBook;
import com.createstats.seismic.VeinOutline;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// the real ShellScan over the stage's ore, truth by construction
public final class SeismicPonderSurvey {
    public static final String IRON = "c:ores/iron";

    public record Vein(String kind, String label, String depth, int colour, int count, int echo, double nearest, int amplitude,
                       double[] centre, double[] near, int[][] cells, int[] runs) {
    }

    public record Survey(List<Vein> veins, int reach, double strength, int[] echoes, int nearest, int bottom, int[] rig) {
        public int lastEcho() {
            int last = 0;
            for (final Vein v : veins) {
                last = Math.max(last, v.echo());
            }
            return last;
        }

        public int readingTick() {
            return SeismicWave.listenTicks(reach);
        }
    }

    private SeismicPonderSurvey() {
    }

    public static Survey survey(int[] rig, int radius, List<int[]> ore) {
        final Set<Long> iron = new HashSet<>();
        for (final int[] c : ore) {
            iron.add(key(c[0], c[1], c[2]));
        }
        final Object any = new Object();
        final ShellScan.Ground<Object> ground = new ShellScan.Ground<>() {
            @Override
            public Object section(int sx, int sy, int sz) {
                return any;
            }

            @Override
            public int kindAt(Object s, int x, int y, int z) {
                return iron.contains(key(x, y, z)) ? 0 : -1;
            }

            @Override
            public boolean covered(int x, int y, int z) {
                return true;
            }
        };
        final int reach = SeismicCharge.surveyRadius(radius, SeismicCharge.burstFor(radius));
        final ShellScan<Object> scan = new ShellScan<>(ground, rig[0], rig[1], rig[2], reach,
                new VeinBook(32_768, true));
        final List<VeinBook.Vein> closed = new ArrayList<>();
        while (!scan.done()) {
            closed.addAll(scan.step());
        }
        closed.sort((a, b) -> Float.compare(a.nearest(), b.nearest()));
        final List<Vein> veins = new ArrayList<>();
        final int[] echoes = new int[closed.size() * 2];
        for (int i = 0; i < closed.size(); i++) {
            final VeinBook.Vein v = closed.get(i);
            veins.add(vein(rig, v));
            echoes[i * 2] = v.echoTick();
            echoes[i * 2 + 1] = SeismicTrace.echoAmplitude(v.count(), v.nearest());
        }
        return new Survey(List.copyOf(veins), reach, SeismicTrace.blowStrength(SeismicCharge.reachOf(SeismicCharge.burstFor(radius))),
                echoes, scan.nearest(), SeismicCatalog.bowlBottom(SeismicStage.RIG_Y, reach), rig.clone());
    }

    private static Vein vein(int[] rig, VeinBook.Vein v) {
        final int[] blocks = v.blocks();
        final int[][] cells = new int[blocks.length][];
        double best = Double.MAX_VALUE;
        double[] near = null;
        for (int i = 0; i < blocks.length; i++) {
            final int dx = SurveyShells.dx(blocks[i]);
            final int dy = SurveyShells.dy(blocks[i]);
            final int dz = SurveyShells.dz(blocks[i]);
            cells[i] = new int[] {rig[0] + dx, rig[1] + dy, rig[2] + dz};
            final double d = SurveyShells.distanceOf(dx, dy, dz);
            if (d < best) {
                best = d;
                near = new double[] {cells[i][0] + 0.5, cells[i][1] + 0.5, cells[i][2] + 0.5};
            }
        }

        final int[] edges = VeinOutline.edges(VeinOutline.cells(blocks, VeinOutline.cellFor(v.count())));
        final int[] runs = new int[edges.length];
        for (int i = 0; i + 4 < edges.length; i += 5) {
            runs[i] = rig[0] + edges[i];
            runs[i + 1] = rig[1] + edges[i + 1];
            runs[i + 2] = rig[2] + edges[i + 2];
            runs[i + 3] = edges[i + 3];
            runs[i + 4] = edges[i + 4];
        }
        final double[] centre = {rig[0] + v.cx(), rig[1] + v.cy(), rig[2] + v.cz()};
        final String depth = "Y " + (int) Math.floor(SeismicStage.worldY(centre[1], rig[1]));
        final String kind = SeismicCatalog.veinLabel(IRON, v.kind());
        return new Vein(kind, kind + " " + v.count(), depth, SeismicCatalog.colour(IRON, v.kind()),
                v.count(), v.echoTick(), v.nearest(), SeismicTrace.echoAmplitude(v.count(), v.nearest()), centre, near,
                cells, runs);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 1024) << 42) | ((long) (y + 1024) << 21) | (z + 1024L);
    }

    public static double distance(Vein v, double x, double y, double z) {
        double best = Double.MAX_VALUE;
        for (final int[] c : v.cells()) {
            final double dx = Math.max(Math.max(c[0] - x, 0.0), x - (c[0] + 1));
            final double dy = Math.max(Math.max(c[1] - y, 0.0), y - (c[1] + 1));
            final double dz = Math.max(Math.max(c[2] - z, 0.0), z - (c[2] + 1));
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(best);
    }
}
