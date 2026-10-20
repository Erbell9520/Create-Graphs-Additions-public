package com.createstats.gui.home;

import java.util.Set;
import java.util.function.LongPredicate;

// never on a fixture mark, furniture costs a point, open floor costs nothing
public final class LabelPlacer {
    private LabelPlacer() {
    }

    public static final int ROWS = 4;

    public static final int WALL_COST = 3;

    public record Spot(int x, int z, int inset) {
    }

    public static Spot place(Set<Long> cells, long centre, Set<Long> marked, LongPredicate clear, LongPredicate wallish,
                             Set<Long> taken, int widthPx, int scale) {
        final int cx = PlanPainter.unpackX(centre);
        final int cz = PlanPainter.unpackZ(centre);

        final int span = Math.max(1, (widthPx + 2 + scale - 1) / scale);
        Spot best = null;
        int bestScore = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (int k = 0; k <= 2 * ROWS; k++) {
            final int z = cz + ((k & 1) == 0 ? k / 2 : -(k + 1) / 2);
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (long c : cells) {
                if (PlanPainter.unpackZ(c) == z) {
                    minX = Math.min(minX, PlanPainter.unpackX(c));
                    maxX = Math.max(maxX, PlanPainter.unpackX(c));
                }
            }
            if (minX > maxX) {
                continue;
            }
            for (int x0 = minX - span + 1; x0 <= maxX; x0++) {
                int score = 0;
                int inside = 0;
                boolean out = false;
                for (int x = x0; x < x0 + span; x++) {
                    final long c = PlanPainter.pack(x, z);
                    if (marked.contains(c) || taken.contains(c)) {
                        out = true;
                        break;
                    }
                    if (cells.contains(c)) {
                        inside++;
                        if (!clear.test(c)) {
                            score++;
                        }
                    } else if (wallish.test(c)) {
                        score += WALL_COST;
                    } else {
                        out = true;
                        break;
                    }
                }
                if (out || inside * 2 < span) {
                    continue;
                }
                final int distance = Math.abs(2 * x0 + span - 1 - 2 * cx) + 2 * Math.abs(z - cz);
                if (score < bestScore || (score == bestScore && distance < bestDistance)) {
                    bestScore = score;
                    bestDistance = distance;
                    best = new Spot(x0, z, (span * scale - widthPx) / 2);
                }
            }
            if (best != null && bestScore == 0 && k >= 1) {
                break;
            }
        }
        return best;
    }

    public static void take(Set<Long> taken, Spot spot, int widthPx, int scale) {
        final int span = Math.max(1, (widthPx + 2 + scale - 1) / scale);
        for (int x = spot.x() - 1; x <= spot.x() + span; x++) {
            taken.add(PlanPainter.pack(x, spot.z()));
        }
    }
}
