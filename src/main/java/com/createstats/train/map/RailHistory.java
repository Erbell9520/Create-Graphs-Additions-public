package com.createstats.train.map;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.createstats.train.smart.RailSnapshot;

public final class RailHistory {
    private RailHistory() {
    }

    private static final long SAMPLE_TICKS = 10L;

    public static final int CAPACITY = 1440;

    private static final int MAX_TRAINS = 64;

    public static final byte MOVING = 0;
    public static final byte DOCKED = 1;
    public static final byte HELD = 2;
    public static final byte FAULT = 3;

    public static final class Series {
        String routeKey = "";
        String lineKey = "";

        private final float[] timeAt = new float[CAPACITY];
        private final float[] chainAt = new float[CAPACITY];
        private final byte[] stateAt = new byte[CAPACITY];
        private int count;
        private int head;

        float routeLength;

        public int size() {
            return this.count;
        }

        public float time(int i) {
            return this.timeAt[index(i)];
        }

        public float chain(int i) {
            return this.chainAt[index(i)];
        }

        public byte state(int i) {
            return this.stateAt[index(i)];
        }

        public String lineKey() {
            return this.lineKey;
        }

        public float latestTime() {
            return this.count == 0 ? Float.NaN : time(this.count - 1);
        }

        public float latestChain() {
            return this.count == 0 ? Float.NaN : chain(this.count - 1);
        }

        private int index(int i) {
            final int start = this.count < CAPACITY ? 0 : this.head;
            return (start + i) % CAPACITY;
        }

        void add(float time, float chain, byte state) {
            this.timeAt[this.head] = time;
            this.chainAt[this.head] = chain;
            this.stateAt[this.head] = state;
            this.head = (this.head + 1) % CAPACITY;
            if (this.count < CAPACITY) {
                this.count++;
            }
        }

        void reset(String routeKey, String lineKey, float routeLength) {
            this.routeKey = routeKey;
            this.lineKey = lineKey;
            this.routeLength = routeLength;
            this.count = 0;
            this.head = 0;
        }
    }

    public static final class Pulse {
        private final float[] timeAt = new float[CAPACITY];
        private final short[] movingAt = new short[CAPACITY];
        private final short[] heldAt = new short[CAPACITY];
        private final float[] speedAt = new float[CAPACITY];
        private int count;
        private int head;

        public int size() {
            return this.count;
        }

        public float time(int i) {
            return this.timeAt[index(i)];
        }

        public int moving(int i) {
            return this.movingAt[index(i)];
        }

        public int held(int i) {
            return this.heldAt[index(i)];
        }

        public float speed(int i) {
            return this.speedAt[index(i)];
        }

        private int index(int i) {
            final int start = this.count < CAPACITY ? 0 : this.head;
            return (start + i) % CAPACITY;
        }

        void add(float time, int moving, int held, float speed) {
            this.timeAt[this.head] = time;
            this.movingAt[this.head] = (short) Math.min(Short.MAX_VALUE, moving);
            this.heldAt[this.head] = (short) Math.min(Short.MAX_VALUE, held);
            this.speedAt[this.head] = speed;
            this.head = (this.head + 1) % CAPACITY;
            if (this.count < CAPACITY) {
                this.count++;
            }
        }

        void clear() {
            this.count = 0;
            this.head = 0;
        }
    }

    private static final Map<UUID, Series> SERIES = new LinkedHashMap<>();
    private static final Pulse PULSE = new Pulse();

    private static long lastSampleTick = Long.MIN_VALUE;
    private static float latestTime = Float.NaN;

    public static void sample(RailSnapshot snapshot, RailMap map) {
        if (snapshot == null || map == null || snapshot.isEmpty()) {
            return;
        }
        final long tick = snapshot.serverTick;
        if (lastSampleTick != Long.MIN_VALUE) {
            final long since = tick - lastSampleTick;
            if (since < 0L) {
                clear();
            } else if (since < SAMPLE_TICKS) {
                return;
            }
        }
        lastSampleTick = tick;
        latestTime = tick / 20.0f;

        int moving = 0;
        int held = 0;
        float speedSum = 0.0f;
        for (final RailSnapshot.TrainState t : snapshot.trains()) {
            if (t.waitingForSignal) {
                held++;
            } else if (t.moving()) {
                moving++;
                speedSum += t.metresPerSecond();
            }
            record(map, t);
        }
        PULSE.add(latestTime, moving, held, moving == 0 ? 0.0f : speedSum / moving);
        forget(snapshot);
    }

    private static void record(RailMap map, RailSnapshot.TrainState t) {
        if (t.id == null) {
            return;
        }
        final int lineIndex = map.lineIndexOf(t.id);
        if (lineIndex < 0) {
            return;
        }
        final LineChainage chainage = map.chainage(lineIndex);
        if (chainage.isEmpty()) {
            return;
        }
        final float at = chainage.chainageOf(t);
        if (Float.isNaN(at)) {
            return;
        }
        final String routeKey = String.join("|", t.stops);
        final String lineKey = map.lines().get(lineIndex).key;

        Series series = SERIES.get(t.id);
        if (series == null) {
            if (SERIES.size() >= MAX_TRAINS) {
                return;
            }
            series = new Series();
            SERIES.put(t.id, series);
            series.reset(routeKey, lineKey, chainage.total);
        } else if (!series.routeKey.equals(routeKey)
                || Math.abs(series.routeLength - chainage.total) > 1.0f) {
            series.reset(routeKey, lineKey, chainage.total);
        }
        series.add(latestTime, at, stateOf(t));
    }

    private static byte stateOf(RailSnapshot.TrainState t) {
        if (t.unhealthy()) {
            return FAULT;
        }
        if (t.waitingForSignal) {
            return HELD;
        }
        return t.docked() ? DOCKED : MOVING;
    }

    private static void forget(RailSnapshot snapshot) {
        if (SERIES.isEmpty()) {
            return;
        }
        SERIES.keySet().removeIf(id -> snapshot.train(id) == null);
    }

    public static Series of(UUID trainId) {
        return trainId == null ? null : SERIES.get(trainId);
    }

    public static Pulse pulse() {
        return PULSE;
    }

    public static float latestTime() {
        return latestTime;
    }

    public static float span() {
        float oldest = Float.NaN;
        for (final Series s : SERIES.values()) {
            if (s.size() > 0 && (Float.isNaN(oldest) || s.time(0) < oldest)) {
                oldest = s.time(0);
            }
        }
        if (Float.isNaN(oldest) && PULSE.size() > 0) {
            oldest = PULSE.time(0);
        }
        return Float.isNaN(oldest) || Float.isNaN(latestTime) ? 0.0f : latestTime - oldest;
    }

    public static LineChainage chainage(RailMap map, int lineIndex) {
        return map == null ? LineChainage.EMPTY : map.chainage(lineIndex);
    }

    public static boolean hasAnything() {
        return !SERIES.isEmpty();
    }

    public static void clear() {
        SERIES.clear();
        PULSE.clear();
        lastSampleTick = Long.MIN_VALUE;
        latestTime = Float.NaN;
    }
}
