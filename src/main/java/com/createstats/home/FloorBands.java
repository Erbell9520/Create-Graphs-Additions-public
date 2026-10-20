package com.createstats.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public final class FloorBands {
    private FloorBands() {
    }

    public static final int SAME_STOREY = 1;

    public static List<Integer> storeys(Collection<Integer> floorHeights) {
        final TreeSet<Integer> sorted = new TreeSet<>(floorHeights);
        final List<Integer> out = new ArrayList<>();
        int last = Integer.MIN_VALUE;
        boolean any = false;
        for (int y : sorted) {
            if (!any || (long) y - last > SAME_STOREY) {
                out.add(y);
            }
            last = y;
            any = true;
        }
        return out;
    }

    public static int storeyOf(List<Integer> storeys, int floorHeight) {
        int best = -1;
        for (int i = 0; i < storeys.size(); i++) {
            if (storeys.get(i) <= floorHeight + SAME_STOREY) {
                best = i;
            }
        }
        return best < 0 && !storeys.isEmpty() ? 0 : best;
    }

    public static Name name(int storey, int panelStorey) {
        final int d = storey - panelStorey;
        if (d == 0) {
            return new Name("this", 0);
        }
        if (d == 1) {
            return new Name("up_one", 1);
        }
        if (d == -1) {
            return new Name("down_one", 1);
        }
        return d > 0 ? new Name("up", d) : new Name("down", -d);
    }

    public record Name(String key, int count) {
    }
}
