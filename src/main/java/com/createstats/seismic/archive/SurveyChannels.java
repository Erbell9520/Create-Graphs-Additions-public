package com.createstats.seismic.archive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.seismic.SeismicWave;
import com.createstats.seismic.SurveyShells;

// channels without building the scene, the scene is tens of ms on a busy site
public final class SurveyChannels {
    public static final long HEARD_TICKS = 100L;

    public static final long NEW_TICKS = 12_000L;

    private SurveyChannels() {
    }

    public record Figures(String target, double nearest, int nearestX, int nearestY, int nearestZ, int nearestKind,
                          long blocks, long fresh, long heard) {
    }

    public static Map<String, Figures> compute(List<ArchiveBlow> blows, double refX, double refZ, long now) {
        final Map<String, double[]> acc = new LinkedHashMap<>();
        for (final ArchiveBlow b : blows) {
            final double[] a = acc.computeIfAbsent(b.target, k -> new double[] {Double.NaN, 0, 0, 0, 0, 0, 0, 0});
            for (final int c : b.cells()) {
                final int p = ArchiveBlow.packedOf(c);
                final int dx = SurveyShells.dx(p);
                final int dy = SurveyShells.dy(p);
                final int dz = SurveyShells.dz(p);
                final long heardAt = b.at + SeismicWave.echoTick(SurveyShells.distanceOf(dx, dy, dz));
                if (now < heardAt) {
                    continue;
                }
                a[3]++;
                if (now - heardAt <= NEW_TICKS) {
                    a[4]++;
                }
                if (now - heardAt <= HEARD_TICKS) {
                    a[5]++;
                }
                final double gx = b.x + dx + 0.5 - refX;
                final double gz = b.z + dz + 0.5 - refZ;
                final double ground = Math.sqrt(gx * gx + gz * gz);
                if (Double.isNaN(a[0]) || ground < a[0]) {
                    a[0] = ground;
                    a[1] = b.y + dy;
                    a[2] = ArchiveBlow.kindOf(c);
                    a[6] = b.x + dx;
                    a[7] = b.z + dz;
                }
            }
        }
        final Map<String, Figures> out = new LinkedHashMap<>();
        for (final Map.Entry<String, double[]> e : acc.entrySet()) {
            final double[] a = e.getValue();
            out.put(e.getKey(), new Figures(e.getKey(), a[0], (int) a[6], (int) a[1], (int) a[7], (int) a[2],
                    (long) a[3], (long) a[4], (long) a[5]));
        }
        return out;
    }

    public static boolean hot(List<ArchiveBlow> blows, long now) {
        return within(blows, now, HEARD_TICKS);
    }

    public static boolean warm(List<ArchiveBlow> blows, long now) {
        return within(blows, now, NEW_TICKS);
    }

    private static boolean within(List<ArchiveBlow> blows, long now, long after) {
        for (final ArchiveBlow b : blows) {
            if (now >= b.at && now - b.at <= SeismicWave.listenTicks(b.reach) + after) {
                return true;
            }
        }
        return false;
    }
}
