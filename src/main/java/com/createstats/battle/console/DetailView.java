package com.createstats.battle.console;

import java.util.List;

public record DetailView(String key, int lostVersion, int[] lost, int[] ageTicks, byte[] causes, int[] burning,
                         List<Place> places, long receivedAt) {
    public record Place(int id, String name, int kind, int size, int anchor) {
    }

    public static DetailView empty(String key) {
        return new DetailView(key, -1, new int[0], new int[0], new byte[0], new int[0], List.of(), 0L);
    }

    public long age(int i, long clock) {
        return (long) ageTicks[i] + Math.max(0L, clock - receivedAt);
    }
}
