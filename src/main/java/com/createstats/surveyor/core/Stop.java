package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.List;

// create's layout, list of alternatives, each an AND
public record Stop(List<List<StopCondition>> rules, int leaveAfter, String dockFirst, String dockSecond,
                   boolean indicate, int hornAux) {
    public static final int MAX_ALTERNATIVES = 4;

    public static final int MAX_CONDITIONS = 4;

    public static final Stop DEFAULT = new Stop(List.of(List.of(new StopCondition.Wait(10))), 0, "", "", false, -1);

    public Stop {
        rules = copy(rules);
        dockFirst = dockFirst == null ? "" : dockFirst;
        dockSecond = dockSecond == null ? "" : dockSecond;
    }

    public Stop sane() {
        List<List<StopCondition>> out = new ArrayList<>();
        for (List<StopCondition> alt : rules) {
            if (out.size() >= MAX_ALTERNATIVES) break;
            List<StopCondition> a = new ArrayList<>();
            for (StopCondition c : alt) {
                if (c == null || a.size() >= MAX_CONDITIONS) continue;
                a.add(c.sane());
            }
            if (!a.isEmpty()) out.add(List.copyOf(a));
        }
        return new Stop(out, StopCondition.clamp(leaveAfter, 0, StopCondition.MAX_SECONDS), StopCondition.cut(dockFirst),
                StopCondition.cut(dockSecond), indicate, hornAux < 0 || hornAux > 3 ? -1 : hornAux);
    }

    public static final int[] QUICK_WAITS = {5, 10, 30, 60};

    public static Stop quickStep(Stop current, int dir) {
        int now;
        if (current == null) {
            now = 0;
        } else if (current.rules().size() == 1 && current.rules().get(0).size() == 1
                && current.rules().get(0).get(0) instanceof StopCondition.Wait w) {
            now = w.seconds();
        } else {
            return QUICK_UNCHANGED;
        }
        int next;
        if (dir > 0) {
            next = QUICK_WAITS[QUICK_WAITS.length - 1];
            for (int q : QUICK_WAITS) {
                if (q > now) {
                    next = q;
                    break;
                }
            }
        } else {
            next = 0;
            for (int q : QUICK_WAITS) if (q < now) next = q;
        }
        if (next <= 0) return null;
        Stop base = current == null ? DEFAULT : current;
        return base.withRules(List.of(List.of(new StopCondition.Wait(next))));
    }

    public static final Stop QUICK_UNCHANGED = new Stop(List.of(), -1, "", "", false, -1);

    public boolean docks() {
        return !dockFirst.isEmpty() || !dockSecond.isEmpty();
    }

    public Stop withRules(List<List<StopCondition>> r) {
        return new Stop(r, leaveAfter, dockFirst, dockSecond, indicate, hornAux);
    }

    public Stop withLeaveAfter(int seconds) {
        return new Stop(rules, seconds, dockFirst, dockSecond, indicate, hornAux);
    }

    public Stop withDock(String first, String second) {
        return new Stop(rules, leaveAfter, first, second, indicate, hornAux);
    }

    public Stop withIndicate(boolean v) {
        return new Stop(rules, leaveAfter, dockFirst, dockSecond, v, hornAux);
    }

    public Stop withHornAux(int aux) {
        return new Stop(rules, leaveAfter, dockFirst, dockSecond, indicate, aux);
    }

    private static List<List<StopCondition>> copy(List<List<StopCondition>> rules) {
        if (rules == null) return List.of();
        List<List<StopCondition>> out = new ArrayList<>(rules.size());
        for (List<StopCondition> alt : rules) {
            if (alt == null) continue;
            List<StopCondition> a = new ArrayList<>(alt.size());
            for (StopCondition c : alt) if (c != null) a.add(c);
            out.add(List.copyOf(a));
        }
        return List.copyOf(out);
    }
}
