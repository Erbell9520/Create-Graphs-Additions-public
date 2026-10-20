package com.createstats.surveyor.core;

// ahead = shortest way forward round the loop
public final class TrafficGap {
    public record Car(double s, double front, double rear, double speed) {
    }

    public record Room(double gap, int ahead) {
        public static final Room CLEAR = new Room(Double.POSITIVE_INFINITY, -1);
    }

    private TrafficGap() {
    }

    public static Room[] rooms(PathSpline sp, Car[] cars) {
        Room[] out = new Room[cars.length];
        for (int i = 0; i < cars.length; i++) out[i] = roomOf(sp, cars, i);
        return out;
    }

    public static Room roomOf(PathSpline sp, Car[] cars, int i) {
        Car me = cars[i];
        double best = Double.POSITIVE_INFINITY;
        int ahead = -1;
        for (int j = 0; j < cars.length; j++) {
            if (j == i) continue;
            double d = sp.closed() ? sp.ahead(me.s(), cars[j].s()) : cars[j].s() - me.s();

            if (d == 0.0 && j < i) continue;
            if (d < 0.0) continue;
            if (d < best) {
                best = d;
                ahead = j;
            }
        }
        if (ahead < 0) return Room.CLEAR;
        return new Room(best - me.front() - cars[ahead].rear(), ahead);
    }

    public static double speedCap(double gap, double standoff, double decel, double speedAhead) {
        if (!Double.isFinite(gap)) return Double.POSITIVE_INFINITY;
        double room = gap - standoff;
        if (room <= 0.0) return 0.0;
        double credit = 0.5 * Math.max(0.0, speedAhead);
        return Math.sqrt(2.0 * decel * room + credit * credit);
    }
}
