package com.createstats.gui.home;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

public final class LampSpot {
    private LampSpot() {
    }

    public static final int REACH = 14;

    public static final int NEAR = 2;

    public record Spot(int x, int z, int lit) {
    }

    public static Map<Long, Integer> reach(long from, LongPredicate passes, int reach) {
        final Map<Long, Integer> depth = new HashMap<>();
        final ArrayDeque<Long> q = new ArrayDeque<>();
        depth.put(from, 0);
        q.add(from);
        while (!q.isEmpty()) {
            final long p = q.poll();
            final int d = depth.get(p);
            if (d >= reach) {
                continue;
            }
            final int x = PlanPainter.unpackX(p);
            final int z = PlanPainter.unpackZ(p);
            for (int k = 0; k < 4; k++) {
                final long n = PlanPainter.pack(x + PlanGrid.dx(k), z + PlanGrid.dz(k));
                if (depth.containsKey(n) || !passes.test(n)) {
                    continue;
                }
                depth.put(n, d + 1);
                q.add(n);
            }
        }
        return depth;
    }

    public static int lights(long from, Set<Long> dark, LongPredicate passes, int reach) {
        int n = 0;
        for (long c : reach(from, passes, reach).keySet()) {
            if (dark.contains(c)) {
                n++;
            }
        }
        return n;
    }

    public static Spot best(Set<Long> dark, LongPredicate floor, LongPredicate passes, int reach) {
        if (dark.isEmpty()) {
            return null;
        }
        final Set<Long> candidates = new HashSet<>();
        long sx = 0;
        long sz = 0;
        for (long c : dark) {
            final int x = PlanPainter.unpackX(c);
            final int z = PlanPainter.unpackZ(c);
            sx += x;
            sz += z;
            for (int dz = -NEAR; dz <= NEAR; dz++) {
                for (int dx = -NEAR; dx <= NEAR; dx++) {
                    final long n = PlanPainter.pack(x + dx, z + dz);
                    if (floor.test(n) && passes.test(n)) {
                        candidates.add(n);
                    }
                }
            }
        }
        final double mx = sx / (double) dark.size();
        final double mz = sz / (double) dark.size();
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        long bestKey = Long.MAX_VALUE;
        for (long c : candidates) {
            final int lit = lights(c, dark, passes, reach);
            final double ddx = PlanPainter.unpackX(c) - mx;
            final double ddz = PlanPainter.unpackZ(c) - mz;
            final double distance = ddx * ddx + ddz * ddz;
            if (best == null || lit > best.lit() || (lit == best.lit() && (distance < bestDistance
                    || (distance == bestDistance && c < bestKey)))) {
                best = new Spot(PlanPainter.unpackX(c), PlanPainter.unpackZ(c), lit);
                bestDistance = distance;
                bestKey = c;
            }
        }
        return best;
    }
}
