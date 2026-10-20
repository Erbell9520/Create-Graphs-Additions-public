package com.createstats.surveyor.core;

public sealed interface StopCondition permits StopCondition.Wait, StopCondition.Idle, StopCondition.Items,
        StopCondition.Fluid, StopCondition.Link, StopCondition.TimeOfDay, StopCondition.Players, StopCondition.Powered,
        StopCondition.Docked {
    int MAX_SECONDS = 3600;

    int MAX_AMOUNT = 1_000_000;

    String kind();

    StopCondition sane();

    enum Op {
        GREATER(">"), LESS("<"), EQUAL("=");

        public final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }

        public boolean test(long current, long target) {
            return switch (this) {
                case GREATER -> current > target;
                case LESS -> current < target;
                case EQUAL -> current == target;
            };
        }

        public static Op byOrdinal(int i) {
            Op[] v = values();
            return i >= 0 && i < v.length ? v[i] : GREATER;
        }
    }

    record Wait(int seconds) implements StopCondition {
        @Override
        public String kind() {
            return "wait";
        }

        @Override
        public StopCondition sane() {
            return new Wait(clamp(seconds, 0, MAX_SECONDS));
        }
    }

    record Idle(int seconds) implements StopCondition {
        @Override
        public String kind() {
            return "idle";
        }

        @Override
        public StopCondition sane() {
            return new Idle(clamp(seconds, 1, MAX_SECONDS));
        }
    }

    record Items(Op op, int amount, boolean stacks, String item) implements StopCondition {
        public Items {
            op = op == null ? Op.GREATER : op;
            item = item == null ? "" : item;
        }

        @Override
        public String kind() {
            return "items";
        }

        @Override
        public StopCondition sane() {
            return new Items(op, clamp(amount, 0, MAX_AMOUNT), stacks, cut(item));
        }
    }

    record Fluid(Op op, int buckets, String fluid) implements StopCondition {
        public Fluid {
            op = op == null ? Op.GREATER : op;
            fluid = fluid == null ? "" : fluid;
        }

        @Override
        public String kind() {
            return "fluid";
        }

        @Override
        public StopCondition sane() {
            return new Fluid(op, clamp(buckets, 0, MAX_AMOUNT), cut(fluid));
        }
    }

    record Link(String first, String second, boolean powered) implements StopCondition {
        public Link {
            first = first == null ? "" : first;
            second = second == null ? "" : second;
        }

        @Override
        public String kind() {
            return "link";
        }

        @Override
        public StopCondition sane() {
            return new Link(cut(first), cut(second), powered);
        }
    }

    record TimeOfDay(int hour, int minute, int rotation) implements StopCondition {
        public static final int[] ROTATION_TICKS = {24000, 12000, 6000, 4000, 3000, 2000, 1000, 750, 500, 250};

        public static final int WINDOW = 40;

        @Override
        public String kind() {
            return "time_of_day";
        }

        @Override
        public StopCondition sane() {
            return new TimeOfDay(clamp(hour, 0, 23), clamp(minute, 0, 59), clamp(rotation, 0, ROTATION_TICKS.length - 1));
        }

        public int rotationTicks() {
            return ROTATION_TICKS[clamp(rotation, 0, ROTATION_TICKS.length - 1)];
        }

        public int targetTicks() {
            return (int) (((hour + 18) % 24 * 1000.0 + Math.ceil(minute / 60.0f * 1000.0f)) % rotationTicks());
        }

        public boolean doneAt(long dayTime) {
            int now = (int) Math.floorMod(dayTime, (long) rotationTicks());
            int diff = now - targetTicks();
            return diff >= 0 && diff <= WINDOW;
        }

        public int ticksUntil(long dayTime) {
            int now = (int) Math.floorMod(dayTime, (long) rotationTicks());
            int diff = targetTicks() - now;
            if (doneAt(dayTime)) return 0;
            return diff >= 0 ? diff : diff + rotationTicks();
        }
    }

    record Players(int count, boolean orMore) implements StopCondition {
        @Override
        public String kind() {
            return "players";
        }

        @Override
        public StopCondition sane() {
            return new Players(clamp(count, 0, 20), orMore);
        }
    }

    record Powered() implements StopCondition {
        @Override
        public String kind() {
            return "powered";
        }

        @Override
        public StopCondition sane() {
            return this;
        }
    }

    record Docked() implements StopCondition {
        @Override
        public String kind() {
            return "docked";
        }

        @Override
        public StopCondition sane() {
            return this;
        }
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static String cut(String id) {
        return id == null ? "" : (id.length() > 96 ? id.substring(0, 96) : id);
    }
}
