package com.createstats.power;

public record FeManagerView(int stored, int capacity, boolean holdsCharging, boolean servesLoads,
                            boolean takesSources, int unserved, boolean drawsBursts,
                            boolean fundsStores) {
    public FeManagerView {
        stored = Math.max(0, stored);
        capacity = Math.max(0, capacity);
        unserved = Math.max(0, unserved);
    }

    public FeManagerView(int stored, int capacity, boolean holdsCharging, boolean servesLoads,
                         boolean takesSources, int unserved, boolean drawsBursts) {
        this(stored, capacity, holdsCharging, servesLoads, takesSources, unserved, drawsBursts, false);
    }

    public FeManagerView withStoresFunded(boolean funds) {
        return funds == fundsStores ? this : new FeManagerView(stored, capacity, holdsCharging,
                servesLoads, takesSources, unserved, drawsBursts, funds);
    }

    public FeManagerView(int stored, int capacity, boolean holdsCharging, boolean servesLoads,
                         boolean takesSources) {
        this(stored, capacity, holdsCharging, servesLoads, takesSources, 0, takesSources);
    }

    public static FeManagerView leg(int stored, int capacity, boolean holdsCharging) {
        return leg(stored, capacity, holdsCharging, 0);
    }

    public static FeManagerView leg(int stored, int capacity, boolean holdsCharging, int unserved) {
        return new FeManagerView(stored, capacity, holdsCharging, true, true, unserved, true);
    }

    public static FeManagerView output(int stored, int capacity, boolean holdsCharging) {
        return output(stored, capacity, holdsCharging, 0);
    }

    public static FeManagerView output(int stored, int capacity, boolean holdsCharging, int unserved) {
        return new FeManagerView(stored, capacity, holdsCharging, true, false, unserved, true);
    }

    public static FeManagerView input(int stored, int capacity) {
        return input(stored, capacity, 0);
    }

    public static FeManagerView input(int stored, int capacity, int unserved) {
        return new FeManagerView(stored, capacity, false, false, true, unserved, true);
    }

    public FeManagerView refusingSources() {
        return new FeManagerView(stored, capacity, holdsCharging, servesLoads, false, unserved, false,
                fundsStores);
    }

    public FeManagerView merge(FeManagerView other) {
        if (other == null) {
            return this;
        }
        final FeManagerView reserve = other.capacity > capacity ? other : this;
        return new FeManagerView(reserve.stored, reserve.capacity, holdsCharging || other.holdsCharging,
                servesLoads || other.servesLoads, takesSources || other.takesSources,
                Math.max(unserved, other.unserved), drawsBursts || other.drawsBursts,
                fundsStores || other.fundsStores);
    }
}
