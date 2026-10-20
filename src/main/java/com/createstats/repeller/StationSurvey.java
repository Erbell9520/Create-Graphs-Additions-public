package com.createstats.repeller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// only what can spawn in range, the full registry is hundreds long in a modpack
public final class StationSurvey {
    public static final int MAX_SAMPLES = 700;

    public record Sighting(String id, double distance) {
    }

    public record Station(String id, int tenths, int minRadius, boolean column) {
        public boolean seen() {
            return minRadius > 0;
        }
    }

    private StationSurvey() {
    }

    public static List<int[]> samples(int radius) {
        final List<int[]> out = new ArrayList<>();
        final int[][] shells = {{0, 12, 4}, {12, 32, 8}, {32, 64, 16}};
        for (int[] shell : shells) {
            final int lo = shell[0];
            final int hi = Math.min(shell[1], radius);
            final int step = shell[2];
            if (hi < lo || (lo > 0 && hi == lo)) {
                continue;
            }
            for (int dx = -hi; dx <= hi; dx += step) {
                for (int dy = -hi; dy <= hi; dy += step) {
                    for (int dz = -hi; dz <= hi; dz += step) {
                        final int d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 <= hi * hi && (lo == 0 || d2 > lo * lo)) {
                            out.add(new int[] {dx, dy, dz});
                        }
                    }
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1] + a[2] * a[2],
                b[0] * b[0] + b[1] * b[1] + b[2] * b[2]));
        return out.size() > MAX_SAMPLES ? new ArrayList<>(out.subList(0, MAX_SAMPLES)) : out;
    }

    public static int notchReaching(double distance) {
        if (distance > RepellerDraw.MAX_RADIUS) {
            return 0;
        }
        final int over = (int) Math.ceil(Math.max(0.0, distance - RepellerDraw.MIN_RADIUS) / RepellerDraw.RADIUS_STEP
                - 1.0e-9);
        return RepellerDraw.clampRadius(RepellerDraw.MIN_RADIUS + over * RepellerDraw.RADIUS_STEP);
    }

    public static List<Station> merge(Collection<Sighting> sightings, FrequencyTable table, String tuned,
                                      Set<String> columns) {
        final Map<String, Integer> nearest = new HashMap<>();
        for (Sighting s : sightings) {
            final int notch = notchReaching(s.distance());
            if (notch == 0) {
                continue;
            }
            nearest.merge(s.id(), notch, Math::min);
        }
        if (tuned != null && !tuned.isEmpty()) {
            nearest.putIfAbsent(tuned, 0);
        }
        final List<Station> out = new ArrayList<>(nearest.size());
        for (Map.Entry<String, Integer> e : nearest.entrySet()) {
            out.add(new Station(e.getKey(), table.tenthsOf(e.getKey()), e.getValue(), columns.contains(e.getKey())));
        }
        out.sort((a, b) -> {
            final int c = Integer.compare(a.tenths(), b.tenths());
            return c != 0 ? c : a.id().compareTo(b.id());
        });
        return out;
    }

    public static List<Station> inRange(List<Station> all, int radius, String tuned) {
        final List<Station> out = new ArrayList<>(all.size());
        for (Station s : all) {
            if ((s.seen() && s.minRadius() <= radius) || s.id().equals(tuned)) {
                out.add(s);
            }
        }
        return out;
    }
}
