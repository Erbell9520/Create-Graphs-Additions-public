package com.createstats.power;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongToIntFunction;

// the box only knows what its own ports moved, so the manager books its own traffic here. the breaker lesson, again
public final class FeInstrumentBudget {
    private long tick = Long.MIN_VALUE;

    private final Map<Long, Integer> spent = new HashMap<>();

    private final Map<Long, Boolean> clipped = new HashMap<>();

    public int remaining(List<Long> boxes, LongToIntFunction ceilingOf, long now) {
        if (boxes.isEmpty()) {
            return FeGridSolver.NO_CEILING;
        }
        roll(now);
        int room = FeGridSolver.NO_CEILING;
        for (int i = 0; i < boxes.size(); i++) {
            final long box = boxes.get(i);
            final int ceiling = ceilingOf.applyAsInt(box);
            if (ceiling >= FeGridSolver.NO_CEILING) {
                continue;
            }
            room = Math.min(room, Math.max(0, ceiling - spent.getOrDefault(box, 0)));
        }
        return room;
    }

    public void spend(List<Long> boxes, int fe, boolean clip, long now) {
        if (boxes.isEmpty()) {
            return;
        }
        roll(now);
        for (int i = 0; i < boxes.size(); i++) {
            final long box = boxes.get(i);
            if (fe > 0) {
                spent.merge(box, fe, FeRates::plus);
            }
            if (clip) {
                clipped.put(box, Boolean.TRUE);
            }
        }
    }

    public boolean clipped(long box, long now) {
        return now == tick && clipped.getOrDefault(box, Boolean.FALSE);
    }

    private void roll(long now) {
        if (now != tick) {
            tick = now;
            spent.clear();
            clipped.clear();
        }
    }
}
