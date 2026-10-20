package com.createstats.powertrack.core;

import java.util.List;

public final class SectionExchange {
    public enum Role {
        SUPPLY,
        COLLECT,
        IDLE
    }

    public enum State {
        DEAD,

        IDLE,

        LIVE,

        COLLECTING
    }

    public record Feeder(long grid, Role role, int available, int room, int trainFloorPct) {
    }

    public record Car(long train, int shoes, int stored, int capacity, boolean mayCharge, boolean mayDischarge) {
    }

    public record Plan(int[] feederDelta, int[] carDelta, int railFlow, int wanted, State state) {
    }

    private SectionExchange() {
    }

    public static Plan plan(List<Feeder> feeders, List<Car> cars, int ceiling, int collectorRate) {
        final int nf = feeders.size();
        final int nc = cars.size();
        final int[] fd = new int[nf];
        final int[] cd = new int[nc];
        if (nf == 0) {
            return new Plan(fd, cd, 0, 0, State.DEAD);
        }
        final int cap = Math.max(0, ceiling);
        final int shoeRate = Math.max(0, collectorRate);

        boolean live = false;
        boolean supplies = false;
        boolean collects = false;
        for (Feeder f : feeders) {
            live |= f.role() == Role.SUPPLY && f.available() > 0;
            supplies |= f.role() == Role.SUPPLY;
            collects |= f.role() == Role.COLLECT && f.room() > 0;
        }

        final int[] want = new int[nc];
        long wanted = 0;
        if (supplies) {
            for (int j = 0; j < nc; j++) {
                final Car c = cars.get(j);
                if (!c.mayCharge()) {
                    continue;
                }
                final long room = Math.max(0L, (long) c.capacity() - Math.max(0, c.stored()));
                want[j] = (int) Math.min(rate(c, shoeRate), room);
                wanted += want[j];
            }
        }
        final int chargeWanted = (int) Math.min(Integer.MAX_VALUE, wanted);

        if (live) {
            final int[] avail = new int[nf];
            long supply = 0;
            for (int i = 0; i < nf; i++) {
                final Feeder f = feeders.get(i);
                avail[i] = f.role() == Role.SUPPLY ? Math.max(0, f.available()) : 0;
                supply += avail[i];
            }
            final int toCars = (int) Math.min(Math.min(supply, wanted), cap);
            final int[] given = waterFill(toCars, want);
            final int[] taken = waterFill(toCars, avail);
            for (int j = 0; j < nc; j++) {
                cd[j] += given[j];
            }
            for (int i = 0; i < nf; i++) {
                fd[i] -= taken[i];
                avail[i] -= taken[i];
            }

            int left = cap - toCars;
            int carried = 0;
            for (int s = 0; s < nf && left > 0; s++) {
                if (avail[s] <= 0) {
                    continue;
                }
                for (int r = 0; r < nf && left > 0 && avail[s] > 0; r++) {
                    final Feeder to = feeders.get(r);
                    if (to.role() != Role.COLLECT || to.grid() == feeders.get(s).grid()) {
                        continue;
                    }
                    final int room = Math.max(0, to.room()) - Math.max(0, fd[r]);
                    final int move = Math.min(Math.min(avail[s], room), left);
                    if (move > 0) {
                        fd[s] -= move;
                        fd[r] += move;
                        avail[s] -= move;
                        left -= move;
                        carried += move;
                    }
                }
            }
            final int flow = toCars + carried;
            return new Plan(fd, cd, flow, chargeWanted, flow > 0 ? State.LIVE : State.IDLE);
        }

        if (collects) {
            int floor = 0;
            final int[] room = new int[nf];
            long rooms = 0;
            for (int i = 0; i < nf; i++) {
                final Feeder f = feeders.get(i);
                if (f.role() == Role.COLLECT) {
                    room[i] = Math.max(0, f.room());
                    rooms += room[i];
                    floor = Math.max(floor, Math.max(0, Math.min(100, f.trainFloorPct())));
                }
            }
            final int[] give = new int[nc];
            long offered = 0;
            for (int j = 0; j < nc; j++) {
                final Car c = cars.get(j);
                if (!c.mayDischarge()) {
                    continue;
                }
                give[j] = (int) Math.min(rate(c, shoeRate), aboveFloor(c.stored(), c.capacity(), floor));
                offered += give[j];
            }
            final int moved = (int) Math.min(Math.min(offered, rooms), cap);
            final int[] fromCars = waterFill(moved, give);
            final int[] intoFeeders = waterFill(moved, room);
            for (int j = 0; j < nc; j++) {
                cd[j] -= fromCars[j];
            }
            for (int i = 0; i < nf; i++) {
                fd[i] += intoFeeders[i];
            }
            return new Plan(fd, cd, moved, supplies ? chargeWanted : (int) Math.min(Integer.MAX_VALUE, offered),
                    moved > 0 ? State.COLLECTING : State.IDLE);
        }

        return new Plan(fd, cd, 0, chargeWanted, State.IDLE);
    }

    public static State energised(List<Feeder> feeders) {
        if (feeders.isEmpty()) {
            return State.DEAD;
        }
        boolean collects = false;
        for (Feeder f : feeders) {
            if (f.role() == Role.SUPPLY && f.available() > 0) {
                return State.LIVE;
            }
            collects |= f.role() == Role.COLLECT && f.room() > 0;
        }
        return collects ? State.COLLECTING : State.IDLE;
    }

    private static int rate(Car c, int shoeRate) {
        final int shoes = Math.max(0, Math.min(RailPower.MAX_COLLECTORS_PER_CAR, c.shoes()));
        return shoes * shoeRate;
    }

    static long aboveFloor(int stored, int capacity, int floorPct) {
        if (capacity <= 0) {
            return 0L;
        }
        final long floor = floorPct <= 0 ? 0L : (long) Math.ceil(capacity * (floorPct / 100.0));
        return Math.max(0L, (long) Math.max(0, stored) - floor);
    }

    static int[] waterFill(int total, int[] caps) {
        final int n = caps.length;
        final int[] out = new int[n];
        long left = Math.max(0, total);
        while (left > 0) {
            int open = 0;
            for (int i = 0; i < n; i++) {
                if (out[i] < Math.max(0, caps[i])) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            final long share = left / open;
            if (share == 0) {
                for (int i = 0; i < n && left > 0; i++) {
                    if (out[i] < Math.max(0, caps[i])) {
                        out[i]++;
                        left--;
                    }
                }
                break;
            }
            for (int i = 0; i < n; i++) {
                final int room = Math.max(0, caps[i]) - out[i];
                if (room > 0) {
                    final int add = (int) Math.min(room, share);
                    out[i] += add;
                    left -= add;
                }
            }
        }
        return out;
    }
}
