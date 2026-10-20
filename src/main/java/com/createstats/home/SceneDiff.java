package com.createstats.home;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SceneDiff {
    private SceneDiff() {
    }

    public record State(boolean on, int mode, int colour) {
    }

    public static Set<Long> changes(Map<Long, State> current, Map<Long, State> scene) {
        final Set<Long> out = new LinkedHashSet<>();
        for (Map.Entry<Long, State> e : scene.entrySet()) {
            final State now = current.get(e.getKey());
            if (now != null && differs(now, e.getValue())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public static boolean differs(State now, State target) {
        if (now.on() != target.on()) {
            return true;
        }
        if (!target.on()) {
            return false;
        }
        if (now.mode() >= 0 && target.mode() >= 0 && now.mode() != target.mode()) {
            return true;
        }
        return now.colour() >= 0 && target.colour() >= 0 && now.colour() != target.colour();
    }
}
