package com.createstats.home;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class RoomFill {
    private RoomFill() {
    }

    public static final int MAX_CELLS = 1024;

    public static final int MAX_OPENINGS = 3;

    public enum Cell {
        FLOOR,

        WALL,

        DOOR,

        OPEN
    }

    @FunctionalInterface
    public interface Grid {
        Cell at(int x, int z);
    }

    public enum Refusal {
        NONE,

        NOT_FLOOR,

        ESCAPED,

        TOO_BIG
    }

    public record Result(Refusal refusal, Set<Long> cells) {
        public boolean isRoom() {
            return refusal == Refusal.NONE;
        }
    }

    public static Result fill(Grid grid, int x, int z) {
        return fill(grid, x, z, MAX_CELLS);
    }

    public static Result fill(Grid grid, int x, int z, int limit) {
        if (grid.at(x, z) != Cell.FLOOR) {
            return new Result(Refusal.NOT_FLOOR, Set.of());
        }
        final Set<Long> seen = new HashSet<>();
        final Set<Long> openings = new HashSet<>();
        final Deque<long[]> queue = new ArrayDeque<>();
        seen.add(pack(x, z));
        queue.add(new long[] {x, z});
        while (!queue.isEmpty()) {
            final long[] at = queue.poll();
            final int cx = (int) at[0];
            final int cz = (int) at[1];
            for (int d = 0; d < 4; d++) {
                final int nx = cx + (d == 0 ? 1 : d == 1 ? -1 : 0);
                final int nz = cz + (d == 2 ? 1 : d == 3 ? -1 : 0);
                final long key = pack(nx, nz);
                if (seen.contains(key)) {
                    continue;
                }
                final Cell cell = grid.at(nx, nz);
                if (cell == Cell.OPEN) {
                    openings.add(key);
                    if (openings.size() > MAX_OPENINGS) {
                        return new Result(Refusal.ESCAPED, Set.of());
                    }
                    continue;
                }
                if (cell != Cell.FLOOR) {
                    continue;
                }
                seen.add(key);
                if (seen.size() > limit) {
                    return new Result(Refusal.TOO_BIG, Set.of());
                }
                queue.add(new long[] {nx, nz});
            }
        }
        return new Result(Refusal.NONE, Set.copyOf(seen));
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }
}
