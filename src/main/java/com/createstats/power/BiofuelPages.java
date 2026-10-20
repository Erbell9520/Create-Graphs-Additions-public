package com.createstats.power;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

public final class BiofuelPages {
    public record Page<T>(int burnTicks, List<T> items) {
    }

    private BiofuelPages() {
    }

    public static <T> List<Page<T>> build(Collection<T> items, ToIntFunction<T> burnTicks,
                                          Comparator<T> order, int perPage) {
        if (perPage < 1) {
            throw new IllegalArgumentException("a page needs at least one slot, got " + perPage);
        }
        final Map<Integer, List<T>> byTicks = new TreeMap<>(Comparator.reverseOrder());
        for (T item : items) {
            final int ticks = burnTicks.applyAsInt(item);
            if (ticks > 0) {
                byTicks.computeIfAbsent(ticks, k -> new ArrayList<>()).add(item);
            }
        }
        final List<Page<T>> pages = new ArrayList<>();
        for (Map.Entry<Integer, List<T>> group : byTicks.entrySet()) {
            final List<T> sorted = new ArrayList<>(group.getValue());
            sorted.sort(order);
            for (int from = 0; from < sorted.size(); from += perPage) {
                pages.add(new Page<>(group.getKey(),
                        List.copyOf(sorted.subList(from, Math.min(sorted.size(), from + perPage)))));
            }
        }
        return pages;
    }
}
