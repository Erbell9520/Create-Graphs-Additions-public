package com.createstats.surveyor.core;

import java.util.Map;
import java.util.function.BiPredicate;

public final class CargoSnapshot implements StopRules.Cargo {
    private final Map<String, Long> items;
    private final Map<String, Long> stacks;
    private final Map<String, Long> fluids;
    private final int idle;
    private final long sampledAt;
    private final boolean known;
    private long now;
    private long day = -1;
    private boolean powered;
    private BiPredicate<String, String> links = (a, b) -> false;

    public CargoSnapshot(Map<String, Long> items, Map<String, Long> stacks, Map<String, Long> fluids,
                         int idle, long sampledAt, boolean known) {
        this.items = Map.copyOf(items);
        this.stacks = Map.copyOf(stacks);
        this.fluids = Map.copyOf(fluids);
        this.idle = Math.max(0, idle);
        this.sampledAt = sampledAt;
        this.now = sampledAt;
        this.known = known;
    }

    public static CargoSnapshot unavailable(long now) {
        return new CargoSnapshot(Map.of(), Map.of(), Map.of(), 0, now, false);
    }

    public CargoSnapshot readings(long now, long day, boolean powered, BiPredicate<String, String> links) {
        this.now = now;
        this.day = day;
        this.powered = powered;
        this.links = links;
        return this;
    }

    public Map<String, Long> itemCounts() { return items; }
    public Map<String, Long> stackCounts() { return stacks; }
    public Map<String, Long> fluidCounts() { return fluids; }
    public int idleAtSample() { return idle; }
    public long sampledAt() { return sampledAt; }
    @Override public boolean known() { return known; }

    private static long count(Map<String, Long> values, String id) {
        if (!id.isEmpty()) return values.getOrDefault(id, 0L);
        return values.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override public long items(String item, boolean fullStacks) { return count(fullStacks ? stacks : items, item); }
    @Override public long fluidMb(String fluid) { return count(fluids, fluid); }
    @Override public int idleTicks() {
        return (int) Math.min(Integer.MAX_VALUE, idle + Math.max(0L, now - sampledAt));
    }
    @Override public long dayTime() { return day; }
    @Override public boolean stopPowered() { return powered; }
    @Override public boolean linkPowered(String first, String second) { return links.test(first, second); }
}
