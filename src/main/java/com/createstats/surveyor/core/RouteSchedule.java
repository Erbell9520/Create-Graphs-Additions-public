package com.createstats.surveyor.core;

import java.util.ArrayList;
import java.util.List;

// create's schedule grammar. what a road vehicle can't do is kept, not dropped
public record RouteSchedule(List<Entry> entries, boolean cyclic) {
    public static final int MAX_ENTRIES = 32;
    public static final int MAX_TEXT = 64;

    public RouteSchedule {
        entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
    }

    public sealed interface Instruction permits TravelTo, Rename, Throttle, Unsupported {
        default boolean waits() {
            return false;
        }
    }

    public record TravelTo(String station) implements Instruction {
        public TravelTo {
            station = cut(station);
        }

        @Override
        public boolean waits() {
            return true;
        }
    }

    public record Rename(String title) implements Instruction {
        public Rename {
            title = cut(title);
        }
    }

    public record Throttle(int percent) implements Instruction {
        public Throttle {
            percent = Math.max(5, Math.min(100, percent));
        }
    }

    public record Unsupported(String id, String why) implements Instruction {
        public Unsupported {
            id = cut(id);
            why = why == null ? "" : why;
        }
    }

    public record Entry(Instruction instruction, List<List<StopCondition>> conditions, List<String> declined) {
        public Entry {
            List<List<StopCondition>> cols = new ArrayList<>();
            if (conditions != null) {
                for (List<StopCondition> col : conditions) {
                    if (col == null || col.isEmpty() || cols.size() >= Stop.MAX_ALTERNATIVES) continue;
                    List<StopCondition> c = new ArrayList<>();
                    for (StopCondition k : col) if (k != null && c.size() < Stop.MAX_CONDITIONS) c.add(k.sane());
                    cols.add(List.copyOf(c));
                }
            }
            conditions = List.copyOf(cols);
            declined = declined == null ? List.of() : List.copyOf(declined);
        }

        public Stop asStop() {
            return new Stop(conditions, 0, "", "", false, -1);
        }
    }

    public List<String> stations() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries) if (e.instruction() instanceof TravelTo t) out.add(t.station());
        return out;
    }

    static String cut(String s) {
        if (s == null) return "";
        return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
    }
}
