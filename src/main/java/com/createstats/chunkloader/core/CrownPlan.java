package com.createstats.chunkloader.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class CrownPlan {
    public record Cell(int right, int up, int front) {
        public boolean isBase() {
            return right == 0 && up == 0 && front == 0;
        }
    }

    public static final Cell BEHIND_STUDS = new Cell(0, 0, -1);

    private static final List<Cell> BASE = List.of(new Cell(0, 0, 0));
    private static final List<Cell> FROM_6;
    private static final List<Cell> FROM_11;
    private static final List<Cell> FROM_16;

    static {
        final List<Cell> six = new ArrayList<>(BASE);
        six.add(new Cell(0, 1, 0));
        six.add(new Cell(1, 1, 0));
        six.add(new Cell(-1, 1, 0));
        six.add(new Cell(0, 1, 1));
        six.add(new Cell(0, 1, -1));
        six.add(new Cell(1, 0, 0));
        six.add(new Cell(-1, 0, 0));
        FROM_6 = Collections.unmodifiableList(six);

        final List<Cell> eleven = new ArrayList<>(FROM_6);
        eleven.add(new Cell(1, 1, 1));
        eleven.add(new Cell(-1, 1, 1));
        eleven.add(new Cell(1, 1, -1));
        eleven.add(new Cell(-1, 1, -1));
        FROM_11 = Collections.unmodifiableList(eleven);

        final List<Cell> sixteen = new ArrayList<>(FROM_11);
        sixteen.add(new Cell(0, 2, 0));
        sixteen.add(new Cell(1, 2, 0));
        sixteen.add(new Cell(-1, 2, 0));
        sixteen.add(new Cell(0, 2, 1));
        sixteen.add(new Cell(0, 2, -1));
        sixteen.add(new Cell(1, 2, 1));
        sixteen.add(new Cell(-1, 2, 1));
        sixteen.add(new Cell(1, 2, -1));
        sixteen.add(new Cell(-1, 2, -1));
        FROM_16 = Collections.unmodifiableList(sixteen);
    }

    private CrownPlan() {
    }

    public static List<Cell> cellsFor(int count) {
        if (count >= 16) {
            return FROM_16;
        }
        if (count >= 11) {
            return FROM_11;
        }
        if (count >= 6) {
            return FROM_6;
        }
        return BASE;
    }

    public static int formCount(int held, Predicate<Cell> free) {
        if (held <= 0) {
            return 0;
        }
        int count = held;
        while (count > 0) {
            if (allFree(cellsFor(count), free)) {
                return count;
            }
            count = largestCountBelow(count);
        }
        return 0;
    }

    public static Cell firstBlocked(int held, Predicate<Cell> free) {
        for (Cell c : cellsFor(held)) {
            if (!c.isBase() && !free.test(c)) {
                return c;
            }
        }
        return null;
    }

    private static int largestCountBelow(int count) {
        if (count >= 16) {
            return 15;
        }
        if (count >= 11) {
            return 10;
        }
        if (count >= 6) {
            return 5;
        }

        return 0;
    }

    private static boolean allFree(List<Cell> cells, Predicate<Cell> free) {
        for (Cell c : cells) {
            if (!c.isBase() && !free.test(c)) {
                return false;
            }
        }
        return true;
    }

    public static int[] parentStep(Cell cell) {
        if (cell.isBase()) {
            return null;
        }
        if (cell.right() == 0 && cell.front() == 0) {
            return new int[] {0, -1, 0};
        }
        if (cell.right() != 0) {
            return new int[] {-Integer.signum(cell.right()), 0, 0};
        }

        return new int[] {0, 0, -Integer.signum(cell.front())};
    }
}
